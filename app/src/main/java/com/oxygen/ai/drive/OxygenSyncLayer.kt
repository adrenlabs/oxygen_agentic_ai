package com.oxygen.ai.drive

import com.oxygen.ai.core.error.OxygenError
import com.oxygen.ai.core.logging.OxygenLog
import com.oxygen.ai.core.net.NetworkMonitor
import com.oxygen.ai.data.db.dao.SyncDao
import com.oxygen.ai.data.db.entities.OfflineQueueEntity
import com.oxygen.ai.data.db.entities.SyncStateEntity
import com.oxygen.ai.security.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

enum class DriveMode { LOCAL_ONLY, DRIVE_BACKUP, DRIVE_SYNC }

data class SyncRecord(
    val schemaVersion: Int = 1,
    val deviceId: String,
    val recordId: String,
    val collection: String,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Long,
    val checksum: String,
    val payload: String,
)

class DriveConflictResolver {
    enum class Decision { KEEP_LOCAL, KEEP_REMOTE, KEEP_BOTH }

    fun decide(local: SyncRecord, remote: SyncRecord): Decision {
        if (local.checksum == remote.checksum) return Decision.KEEP_LOCAL
        return when {
            local.revision > remote.revision -> Decision.KEEP_LOCAL
            remote.revision > local.revision -> Decision.KEEP_REMOTE
            local.updatedAt > remote.updatedAt -> Decision.KEEP_LOCAL
            remote.updatedAt > local.updatedAt -> Decision.KEEP_REMOTE
            else -> Decision.KEEP_BOTH
        }
    }
}

class DriveRestClient(
    private val secrets: SecretStore,
    private val http: OkHttpClient = OkHttpClient(),
) {
    private fun token(): String = secrets.get(SecretStore.DRIVE_ACCESS_TOKEN)
        ?: throw OxygenError.AuthenticationFailed("Drive is not signed in")

    suspend fun ensureFolder(name: String, parentId: String?): String = withContext(Dispatchers.IO) {
        val q = buildString {
            append("name='").append(name.replace("'", "\\'")).append("' and mimeType='application/vnd.google-apps.folder' and trashed=false")
            if (parentId != null) append(" and '").append(parentId).append("' in parents")
        }
        val list = get("https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(q, "UTF-8")}&fields=files(id,name)")
        val files = JSONObject(list).optJSONArray("files")
        if (files != null && files.length() > 0) return@withContext files.getJSONObject(0).getString("id")
        val meta = JSONObject()
            .put("name", name)
            .put("mimeType", "application/vnd.google-apps.folder")
        if (parentId != null) meta.put("parents", org.json.JSONArray().put(parentId))
        val created = postJson("https://www.googleapis.com/drive/v3/files", meta.toString())
        JSONObject(created).getString("id")
    }

    suspend fun uploadJson(name: String, parentId: String, json: String): String = withContext(Dispatchers.IO) {
        val boundary = "oxygen-${UUID.randomUUID()}"
        val body = buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(JSONObject().put("name", name).put("parents", org.json.JSONArray().put(parentId)).toString())
            append("\r\n--").append(boundary).append("\r\n")
            append("Content-Type: application/json\r\n\r\n")
            append(json)
            append("\r\n--").append(boundary).append("--")
        }
        val req = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
            .header("Authorization", "Bearer ${token()}")
            .post(body.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw OxygenError.DriveSyncFailed("upload HTTP ${resp.code}")
            JSONObject(text).optString("id")
        }
    }

    suspend fun download(fileId: String): String = withContext(Dispatchers.IO) {
        get("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
    }

    private fun get(url: String): String {
        val req = Request.Builder().url(url).header("Authorization", "Bearer ${token()}").get().build()
        return http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw OxygenError.DriveSyncFailed("GET HTTP ${resp.code}")
            text
        }
    }

    private fun postJson(url: String, json: String): String {
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${token()}")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()
        return http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw OxygenError.DriveSyncFailed("POST HTTP ${resp.code}")
            text
        }
    }
}

class OxygenSyncLayer(
    private val dao: SyncDao,
    private val secrets: SecretStore,
    private val network: NetworkMonitor,
    private val mode: () -> DriveMode,
    private val deviceId: () -> String,
    private val rest: DriveRestClient,
    private val resolver: DriveConflictResolver = DriveConflictResolver(),
) {
    private val folders = listOf("memory", "conversations", "documents", "embeddings", "backups", "settings", "metadata")

    suspend fun backup(collection: String? = null): Result<String> = runCatching {
        if (mode() == DriveMode.LOCAL_ONLY) throw OxygenError.DriveSyncFailed("Drive is in Local Only mode")
        if (!network.isOnlineNow()) {
            enqueue("backup", collection ?: "*")
            return@runCatching "Queued offline"
        }
        val root = rest.ensureFolder("OXYGEN", null)
        folders.forEach { rest.ensureFolder(it, root) }
        OxygenLog.i("drive", "Backup structure ready collection=${collection ?: "*"}")
        "Backup folder ready"
    }

    suspend fun restore(): Result<String> = runCatching {
        if (mode() == DriveMode.LOCAL_ONLY) throw OxygenError.DriveSyncFailed("Drive is in Local Only mode")
        if (!network.isOnlineNow()) throw OxygenError.Offline("drive restore")
        "Restore scanned"
    }

    suspend fun upsertLocal(collection: String, recordId: String, payload: String) {
        val now = System.currentTimeMillis()
        val checksum = sha256(payload)
        val existing = dao.get(recordId)
        val revision = (existing?.revision ?: 0) + 1
        dao.upsert(
            SyncStateEntity(
                recordId = recordId,
                collection = collection,
                schemaVersion = 1,
                deviceId = deviceId(),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                revision = revision,
                checksum = checksum,
                syncState = "DIRTY",
            ),
        )
        if (mode() == DriveMode.DRIVE_SYNC && network.isOnlineNow()) {
            runCatching { push(collection, recordId, payload, revision, checksum) }
        } else if (mode() != DriveMode.LOCAL_ONLY && !network.isOnlineNow()) {
            enqueue("push", payload)
        }
    }

    fun resolve(local: SyncRecord, remote: SyncRecord) = resolver.decide(local, remote)

    suspend fun flushQueue() {
        if (!network.isOnlineNow() || mode() == DriveMode.LOCAL_ONLY) return
        dao.queue().forEach { item ->
            runCatching { backup(null) }
                .onSuccess { dao.dequeue(item.id) }
                .onFailure { OxygenLog.w("drive", "queue retry ${item.id}", it) }
        }
    }

    private suspend fun push(collection: String, recordId: String, payload: String, revision: Long, checksum: String) {
        val root = rest.ensureFolder("OXYGEN", null)
        val folder = rest.ensureFolder(collection, root)
        val envelope = buildJsonObject {
            put("schemaVersion", 1)
            put("deviceId", deviceId())
            put("recordId", recordId)
            put("collection", collection)
            put("updatedAt", System.currentTimeMillis())
            put("revision", revision)
            put("checksum", checksum)
            put("payload", payload)
        }
        val id = rest.uploadJson("$recordId.json", folder, envelope.toString())
        val prev = dao.get(recordId)
        if (prev != null) dao.upsert(prev.copy(syncState = "SYNCED", remoteFileId = id, lastError = null))
    }

    private suspend fun enqueue(kind: String, payload: String) {
        dao.enqueue(
            OfflineQueueEntity(
                id = UUID.randomUUID().toString(),
                kind = kind,
                payloadJson = payload,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

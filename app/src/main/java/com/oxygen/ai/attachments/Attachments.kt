package com.oxygen.ai.attachments

import android.content.Context
import android.net.Uri
import com.oxygen.ai.core.error.OxygenError
import com.oxygen.ai.data.db.dao.AttachmentDao
import com.oxygen.ai.data.db.entities.AttachmentEntity
import com.oxygen.ai.security.PathSafety
import java.io.File
import java.util.UUID

enum class AttachmentClass { DIRECT_CONTEXT, RAG_INDEXED, UNSUPPORTED }

object AttachmentClassifier {
    private val textExt = setOf("txt", "md", "json", "csv", "kt", "java", "py", "js", "ts", "html", "xml")
    private val ragExt = setOf("pdf", "docx")
    private val imageExt = setOf("png", "jpg", "jpeg", "webp", "gif", "heic")

    fun classify(name: String, sizeBytes: Long): AttachmentClass {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext in imageExt -> AttachmentClass.UNSUPPORTED
            ext in ragExt || sizeBytes > 24_000 -> AttachmentClass.RAG_INDEXED
            ext in textExt -> AttachmentClass.DIRECT_CONTEXT
            else -> AttachmentClass.UNSUPPORTED
        }
    }
}

class AttachmentStore(
    private val context: Context,
    private val dao: AttachmentDao,
) {
    fun root(): File = File(context.filesDir, "attachments").apply { mkdirs() }

    suspend fun import(uri: Uri, conversationId: String, displayName: String, mime: String, size: Long): AttachmentEntity {
        if (PathSafety.isOversized(size, 80L * 1024 * 1024)) {
            throw OxygenError.StorageInsufficient(size, 80L * 1024 * 1024)
        }
        val dest = File(root(), PathSafety.sanitizeFileName("${UUID.randomUUID()}-$displayName"))
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { input.copyTo(it) }
        } ?: throw OxygenError.Validation("Unable to read attachment")
        val entity = AttachmentEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            messageId = null,
            displayName = displayName,
            mimeType = mime,
            localPath = dest.absolutePath,
            sizeBytes = dest.length(),
            classification = AttachmentClassifier.classify(displayName, dest.length()).name,
            documentId = null,
            createdAt = System.currentTimeMillis(),
        )
        dao.upsert(entity)
        return entity
    }

    suspend fun forConversation(id: String) = dao.forConversation(id)
}

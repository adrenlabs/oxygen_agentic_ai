package com.oxygen.ai.telegram

import com.oxygen.ai.agent.AgentRequest
import com.oxygen.ai.agent.AgentSink
import com.oxygen.ai.core.error.OxygenError
import com.oxygen.ai.core.logging.OxygenLog
import com.oxygen.ai.core.net.NetworkMonitor
import com.oxygen.ai.data.db.dao.TelegramDao
import com.oxygen.ai.data.db.entities.TelegramAllowlistEntity
import com.oxygen.ai.reasoning.ReasoningCatalog
import com.oxygen.ai.security.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class TelegramSession(
    val chatId: String,
    val userId: Long,
    val username: String?,
    var conversationId: String?,
)

class TelegramBotAdapter(
    private val secrets: SecretStore,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build(),
) {
    private fun token(): String = secrets.get(SecretStore.TELEGRAM_BOT_TOKEN)
        ?: throw OxygenError.TelegramFailed("Bot token is not configured")

    private fun url(method: String) = "https://api.telegram.org/bot${token()}/$method"

    suspend fun getUpdates(offset: Long, timeout: Int = 25): JSONArray = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("${url("getUpdates")}?offset=$offset&timeout=$timeout&allowed_updates=%5B%22message%22%5D")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw OxygenError.TelegramFailed("HTTP ${resp.code}")
            val json = JSONObject(body)
            if (!json.optBoolean("ok")) throw OxygenError.TelegramFailed(json.optString("description"))
            json.optJSONArray("result") ?: JSONArray()
        }
    }

    suspend fun sendMessage(chatId: String, text: String): String = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("chat_id", chatId)
            .put("text", text.take(3900))
            .toString()
        val req = Request.Builder()
            .url(url("sendMessage"))
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw OxygenError.TelegramFailed("HTTP ${resp.code}")
            body
        }
    }
}

class TelegramRouter {
    data class Routed(
        val kind: Kind,
        val text: String,
        val modelHint: String? = null,
        val reasoningHint: String? = null,
    )

    enum class Kind { CHAT, COMMAND, MODEL, REASONING, IGNORE }

    fun route(text: String): Routed {
        val t = text.trim()
        if (t.isEmpty()) return Routed(Kind.IGNORE, t)
        return when {
            t.equals("/start", true) -> Routed(Kind.COMMAND, "start")
            t.equals("/help", true) -> Routed(Kind.COMMAND, "help")
            t.startsWith("/model ", true) -> Routed(Kind.MODEL, t.removePrefix("/model ").trim(), modelHint = t.substringAfter(' ').trim())
            t.startsWith("/reason ", true) || t.startsWith("/reasoning ", true) ->
                Routed(Kind.REASONING, t.substringAfter(' ').trim(), reasoningHint = t.substringAfter(' ').trim())
            else -> Routed(Kind.CHAT, t)
        }
    }
}

class TelegramGateway(
    private val adapter: TelegramBotAdapter,
    private val dao: TelegramDao,
    private val network: NetworkMonitor,
    private val enabled: () -> Boolean,
    private val sinkFactory: () -> AgentSink?,
) {
    private val running = AtomicBoolean(false)
    private val offset = AtomicLong(0)
    private val router = TelegramRouter()
    private val sessions = HashMap<String, TelegramSession>()

    fun isRunning(): Boolean = running.get()

    suspend fun sendText(chatId: String, text: String): Result<String> = runCatching {
        if (!enabled()) throw OxygenError.TelegramFailed("Telegram is disabled")
        if (!network.isOnlineNow()) throw OxygenError.Offline("telegram")
        adapter.sendMessage(chatId, text)
    }

    suspend fun allow(userId: Long, username: String?) {
        dao.add(TelegramAllowlistEntity(userId, username, System.currentTimeMillis()))
    }

    suspend fun deny(userId: Long) = dao.remove(userId)

    suspend fun allowlist() = dao.allowlist()

    suspend fun pollLoop() {
        if (!running.compareAndSet(false, true)) return
        OxygenLog.i("telegram", "Gateway loop started")
        try {
            while (running.get()) {
                if (!enabled() || !network.isOnlineNow()) {
                    delay(3000)
                    continue
                }
                val updates = runCatching { adapter.getUpdates(offset.get()) }.getOrElse {
                    OxygenLog.w("telegram", "poll failed", it)
                    delay(2000)
                    JSONArray()
                }
                for (i in 0 until updates.length()) {
                    val u = updates.optJSONObject(i) ?: continue
                    offset.set(u.optLong("update_id") + 1)
                    handle(u)
                }
            }
        } finally {
            running.set(false)
        }
    }

    fun stop() {
        running.set(false)
    }

    private suspend fun handle(update: JSONObject) {
        val msg = update.optJSONObject("message") ?: return
        val from = msg.optJSONObject("from") ?: return
        val userId = from.optLong("id")
        val username = from.optString("username").ifBlank { null }
        val chatId = msg.optJSONObject("chat")?.opt("id")?.toString() ?: return
        val allowed = dao.allowlist().any { it.userId == userId }
        if (!allowed) {
            OxygenLog.w("telegram", "Rejected non-allowlisted user $userId")
            return
        }
        val text = msg.optString("text")
        val routed = router.route(text)
        val session = sessions.getOrPut(chatId) { TelegramSession(chatId, userId, username, null) }
        when (routed.kind) {
            TelegramRouter.Kind.IGNORE -> return
            TelegramRouter.Kind.COMMAND -> adapter.sendMessage(
                chatId,
                if (routed.text == "help") {
                    "OXYGEN AI\n/model <name>\n/reason <extra_low|low|medium|high|max>\nSend a message to talk to the same Agent Core."
                } else {
                    "OXYGEN AI is online. This Telegram chat uses the same Agent Core as the Android UI."
                },
            )
            TelegramRouter.Kind.MODEL -> adapter.sendMessage(chatId, "Model hint recorded: ${routed.modelHint}")
            TelegramRouter.Kind.REASONING -> adapter.sendMessage(
                chatId,
                "Reasoning set toward ${ReasoningCatalog.parseLevel(routed.reasoningHint).name}",
            )
            TelegramRouter.Kind.CHAT -> {
                val sink = sinkFactory()
                if (sink == null) {
                    adapter.sendMessage(chatId, "Agent Core is not ready on the device.")
                    return
                }
                val result = sink.submit(
                    AgentRequest(
                        conversationId = session.conversationId,
                        text = routed.text,
                        source = "telegram",
                    ),
                )
                session.conversationId = result.conversationId
                adapter.sendMessage(chatId, result.text.ifBlank { "(empty)" })
            }
        }
    }
}

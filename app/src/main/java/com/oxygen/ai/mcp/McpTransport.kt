package com.oxygen.ai.mcp

import com.oxygen.ai.core.error.OxygenError
import com.oxygen.ai.core.logging.OxygenLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

interface McpTransport {
    val kind: String
    suspend fun send(payload: String, timeoutMs: Long): String
    suspend fun close()
}

class HttpStreamableTransport(
    private val endpoint: String,
    private val client: OkHttpClient,
    private val headers: Map<String, String> = emptyMap(),
) : McpTransport {
    override val kind: String = "http"

    override suspend fun send(payload: String, timeoutMs: Long): String = withContext(Dispatchers.IO) {
        try {
            withTimeout(timeoutMs) {
                val req = Request.Builder()
                    .url(endpoint)
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .header("Accept", "application/json, text/event-stream")
                    .apply { headers.forEach { (k, v) -> header(k, v) } }
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw OxygenError.McpConnectionFailed(endpoint, "HTTP ${resp.code}")
                    }
                    if (body.startsWith("event:") || body.contains("data:")) {
                        body.lineSequence()
                            .filter { it.startsWith("data:") }
                            .joinToString("") { it.removePrefix("data:").trim() }
                            .ifBlank { body }
                    } else body
                }
            }
        } catch (e: OxygenError) {
            throw e
        } catch (e: Exception) {
            OxygenLog.e("mcp", "HTTP transport failed", e)
            throw OxygenError.McpConnectionFailed(endpoint, e.message ?: "network")
        }
    }

    override suspend fun close() = Unit
}

class SseTransport(
    private val endpoint: String,
    private val client: OkHttpClient,
) : McpTransport {
    override val kind: String = "sse"
    private val http = HttpStreamableTransport(endpoint, client)

    override suspend fun send(payload: String, timeoutMs: Long): String = http.send(payload, timeoutMs)
    override suspend fun close() = http.close()
}

fun defaultMcpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()

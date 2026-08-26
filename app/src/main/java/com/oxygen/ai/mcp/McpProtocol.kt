package com.oxygen.ai.mcp

import com.oxygen.ai.tools.ToolCall
import com.oxygen.ai.tools.ToolResult
import com.oxygen.ai.tools.ToolSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicLong

/**
 * Official MCP JSON-RPC 2.0 client messages. Transport-agnostic.
 */
class McpProtocol {
    private val seq = AtomicLong(1)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun nextId(): Long = seq.getAndIncrement()

    fun initialize(id: Long): String = encode(
        request(
            id,
            "initialize",
            buildJsonObject {
                put("protocolVersion", "2024-11-05")
                put(
                    "capabilities",
                    buildJsonObject {
                        put("roots", buildJsonObject { put("listChanged", JsonPrimitive(false)) })
                        put("sampling", buildJsonObject {})
                    },
                )
                put(
                    "clientInfo",
                    buildJsonObject {
                        put("name", "OXYGEN AI")
                        put("version", "1.0.0")
                    },
                )
            },
        ),
    )

    fun initialized(): String = encode(
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "notifications/initialized")
        },
    )

    fun listTools(id: Long): String = encode(request(id, "tools/list", buildJsonObject {}))
    fun listResources(id: Long): String = encode(request(id, "resources/list", buildJsonObject {}))
    fun listPrompts(id: Long): String = encode(request(id, "prompts/list", buildJsonObject {}))
    fun ping(id: Long): String = encode(request(id, "ping", buildJsonObject {}))

    fun callTool(id: Long, name: String, args: JsonObject): String =
        encode(
            request(
                id,
                "tools/call",
                buildJsonObject {
                    put("name", name)
                    put("arguments", args)
                },
            ),
        )

    fun readResource(id: Long, uri: String): String =
        encode(request(id, "resources/read", buildJsonObject { put("uri", uri) }))

    fun getPrompt(id: Long, name: String, args: JsonObject): String =
        encode(
            request(
                id,
                "prompts/get",
                buildJsonObject {
                    put("name", name)
                    put("arguments", args)
                },
            ),
        )

    fun cancel(id: Long): String = encode(
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "notifications/cancelled")
            put("params", buildJsonObject { put("requestId", id) })
        },
    )

    fun parse(raw: String): JsonObject = json.parseToJsonElement(raw).jsonObject

    fun parseTools(result: JsonObject): List<ToolSpec> {
        val tools = result["result"]?.jsonObject?.get("tools")?.jsonArray ?: return emptyList()
        return tools.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val desc = o["description"]?.jsonPrimitive?.contentOrNull ?: ""
            val schema = (o["inputSchema"] as? JsonObject) ?: buildJsonObject {}
            ToolSpec(name, desc, "mcp", schema)
        }
    }

    fun parseToolResult(call: ToolCall, result: JsonObject): ToolResult {
        if (result["error"] != null) {
            val msg = result["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull ?: "mcp error"
            return ToolResult(call.id, call.name, false, "", error = msg)
        }
        val content = result["result"]?.jsonObject?.get("content")
        val text = flattenContent(content)
        val isError = result["result"]?.jsonObject?.get("isError")?.jsonPrimitive?.contentOrNull == "true"
        return ToolResult(call.id, call.name, !isError, text, error = if (isError) text else null)
    }

    private fun flattenContent(content: JsonElement?): String {
        if (content == null) return ""
        return when (content) {
            is JsonArray -> content.joinToString("\n") { item ->
                (item as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
                    ?: item.toString()
            }
            is JsonPrimitive -> content.content
            else -> content.toString()
        }
    }

    private fun request(id: Long, method: String, params: JsonObject): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("method", method)
        put("params", params)
    }

    private fun encode(obj: JsonObject): String = json.encodeToString(JsonObject.serializer(), obj)
}

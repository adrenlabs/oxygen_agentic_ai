package com.oxygen.ai.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

enum class PermissionMode { DISABLED, ASK, ALLOWED }

data class ToolSpec(
    val name: String,
    val description: String,
    val origin: String,
    val schema: JsonObject,
    val destructive: Boolean = false,
)

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: JsonObject,
)

data class ToolResult(
    val callId: String,
    val name: String,
    val ok: Boolean,
    val content: String,
    val truncated: Boolean = false,
    val error: String? = null,
)

interface Tool {
    val spec: ToolSpec
    suspend fun invoke(call: ToolCall): ToolResult
}

class ToolRegistry {
    private val tools = LinkedHashMap<String, Tool>()

    fun register(tool: Tool) {
        tools[tool.spec.name] = tool
    }

    fun unregister(name: String) {
        tools.remove(name)
    }

    fun get(name: String): Tool? = tools[name]

    fun all(): List<Tool> = tools.values.toList()

    fun specs(): List<ToolSpec> = tools.values.map { it.spec }
}

fun stringArg(obj: JsonObject, key: String, default: String = ""): String =
    (obj[key] as? JsonPrimitive)?.content ?: default

fun schema(vararg fields: Pair<String, String>): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("object"))
    put(
        "properties",
        buildJsonObject {
            fields.forEach { (name, desc) ->
                put(
                    name,
                    buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive(desc))
                    },
                )
            }
        },
    )
}

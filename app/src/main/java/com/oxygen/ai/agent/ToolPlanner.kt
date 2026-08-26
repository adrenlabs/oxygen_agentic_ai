package com.oxygen.ai.agent

import com.oxygen.ai.tools.ToolCall
import com.oxygen.ai.tools.ToolRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

class ToolPlanner(
    private val registry: ToolRegistry,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun initialCalls(request: AgentRequest, plan: AgentPlan): List<ToolCall> {
        val enabled = request.enabledTools
        return plan.toolNames.mapNotNull { name ->
            if (enabled != null && name !in enabled && enabled.isNotEmpty()) return@mapNotNull null
            if (registry.get(name) == null) return@mapNotNull null
            when (name) {
                "rag_search", "memory_search", "web_search" ->
                    ToolCall(UUID.randomUUID().toString(), name, obj("query" to request.text, "limit" to "6"))
                "datetime" -> ToolCall(UUID.randomUUID().toString(), name, obj())
                "calculator" -> {
                    val expr = Regex("([0-9.]+\\s*[-+*/^()]\\s*[0-9.+\\-*/^() ]+)").find(request.text)?.value
                    expr?.let { ToolCall(UUID.randomUUID().toString(), name, obj("expression" to it)) }
                }
                else -> null
            }
        }
    }

    fun parseModelToolCalls(text: String): List<ToolCall> {
        val out = ArrayList<ToolCall>()
        val xml = Regex("<tool_call>\\s*([\\s\\S]*?)\\s*</tool_call>").findAll(text)
        xml.forEach { m -> parseJsonCall(m.groupValues[1])?.let { out.add(it) } }
        val fence = Regex("```json\\s*(\\{[\\s\\S]*?\\})\\s*```").findAll(text)
        fence.forEach { m -> parseJsonCall(m.groupValues[1])?.let { out.add(it) } }
        return out.distinctBy { it.name + it.arguments.toString() }
    }

    private fun parseJsonCall(raw: String): ToolCall? {
        return runCatching {
            val obj = json.parseToJsonElement(raw).jsonObject
            val name = (obj["name"] ?: obj["tool"])?.jsonPrimitive?.content ?: return null
            if (registry.get(name) == null) return null
            val args = (obj["arguments"] ?: obj["params"] ?: obj["input"]) as? JsonObject ?: JsonObject(emptyMap())
            ToolCall(UUID.randomUUID().toString(), name, args)
        }.getOrNull()
    }

    private fun obj(vararg pairs: Pair<String, String>): JsonObject =
        JsonObject(pairs.associate { it.first to kotlinx.serialization.json.JsonPrimitive(it.second) })
}

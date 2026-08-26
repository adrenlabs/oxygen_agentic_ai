package com.oxygen.ai.agent

import com.oxygen.ai.core.error.OxygenError
import com.oxygen.ai.core.logging.OxygenLog
import com.oxygen.ai.tools.ToolCall
import com.oxygen.ai.tools.ToolPermissionManager
import com.oxygen.ai.tools.ToolRegistry
import com.oxygen.ai.tools.ToolResult
import kotlinx.coroutines.withTimeout

class ExecutionManager(
    private val registry: ToolRegistry,
    private val permissions: ToolPermissionManager,
) {
    suspend fun execute(call: ToolCall, timeoutMs: Long, maxOutput: Int, userConfirmed: Boolean): ToolResult {
        val tool = registry.get(call.name) ?: return ToolResult(call.id, call.name, false, "", error = "Unknown tool")
        val allowed = permissions.allows("tool", call.name, tool.spec.destructive, userConfirmed)
        if (!allowed) {
            return ToolResult(call.id, call.name, false, "", error = OxygenError.PermissionDenied(call.name).userMessage)
        }
        return try {
            val result = withTimeout(timeoutMs) { tool.invoke(call) }
            if (result.content.length > maxOutput) {
                result.copy(content = result.content.take(maxOutput), truncated = true)
            } else result
        } catch (e: Exception) {
            OxygenLog.e("tool", "Tool ${call.name} failed", e)
            ToolResult(call.id, call.name, false, "", error = e.message)
        }
    }
}

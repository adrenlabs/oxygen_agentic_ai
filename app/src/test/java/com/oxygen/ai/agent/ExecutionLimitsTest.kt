package com.oxygen.ai.agent

import com.oxygen.ai.tools.Tool
import com.oxygen.ai.tools.ToolCall
import com.oxygen.ai.tools.ToolPermissionManager
import com.oxygen.ai.tools.ToolRegistry
import com.oxygen.ai.tools.ToolResult
import com.oxygen.ai.tools.ToolSpec
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionLimitsTest {
    @Test
    fun unknownToolFailsClosed() = runBlocking {
        val registry = ToolRegistry()
        val exec = ExecutionManager(registry, object : ToolPermissionManager(FakePermDao()) {})
        val result = exec.execute(
            ToolCall("1", "explode", buildJsonObject {}),
            1000,
            100,
            true,
        )
        assertFalse(result.ok)
    }

    @Test
    fun outputIsTruncated() = runBlocking {
        val registry = ToolRegistry()
        registry.register(object : Tool {
            override val spec = ToolSpec("echo", "e", "builtin", buildJsonObject {})
            override suspend fun invoke(call: ToolCall) = ToolResult(call.id, "echo", true, "x".repeat(5000))
        })
        val exec = ExecutionManager(registry, object : ToolPermissionManager(FakePermDao()) {
            override suspend fun allows(type: String, id: String, destructive: Boolean, userConfirmed: Boolean) = true
        })
        val result = exec.execute(ToolCall("1", "echo", buildJsonObject {}), 1000, 20, true)
        assertTrue(result.truncated)
        assertTrue(result.content.length <= 20)
    }
}

private class FakePermDao : com.oxygen.ai.data.db.dao.PermissionDao {
    override suspend fun all() = emptyList<com.oxygen.ai.data.db.entities.ToolPermissionEntity>()
    override suspend fun get(type: String, id: String) = null
    override suspend fun upsert(entity: com.oxygen.ai.data.db.entities.ToolPermissionEntity) = Unit
}

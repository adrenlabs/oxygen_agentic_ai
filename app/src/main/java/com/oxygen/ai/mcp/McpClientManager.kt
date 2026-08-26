package com.oxygen.ai.mcp

import com.oxygen.ai.core.error.OxygenError
import com.oxygen.ai.core.logging.OxygenLog
import com.oxygen.ai.data.db.dao.McpDao
import com.oxygen.ai.data.db.entities.McpServerEntity
import com.oxygen.ai.tools.PermissionMode
import com.oxygen.ai.tools.Tool
import com.oxygen.ai.tools.ToolCall
import com.oxygen.ai.tools.ToolPermissionManager
import com.oxygen.ai.tools.ToolRegistry
import com.oxygen.ai.tools.ToolResult
import com.oxygen.ai.tools.ToolSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import java.util.UUID

data class McpConnection(
    val server: McpServerEntity,
    val transport: McpTransport,
    val protocol: McpProtocol,
    val tools: List<ToolSpec>,
    val resources: List<String>,
    val prompts: List<String>,
)

class McpServerManager(private val dao: McpDao) {
    fun observe(): Flow<List<McpServerEntity>> = dao.observe()
    suspend fun all() = dao.all()
    suspend fun upsert(entity: McpServerEntity) = dao.upsert(entity)
    suspend fun delete(id: String) = dao.delete(id)
    suspend fun get(id: String) = dao.get(id)
}

class McpClientManager(
    private val servers: McpServerManager,
    private val permissions: ToolPermissionManager,
    private val registry: ToolRegistry,
    private val http: OkHttpClient = defaultMcpClient(),
) {
    private val mutex = Mutex()
    private val connections = LinkedHashMap<String, McpConnection>()
    private val protocol = McpProtocol()

    suspend fun connect(server: McpServerEntity): McpConnection = mutex.withLock {
        val transport = when (server.transport.lowercase()) {
            "sse" -> SseTransport(server.endpoint, http)
            else -> HttpStreamableTransport(server.endpoint, http)
        }
        val initId = protocol.nextId()
        val initRaw = transport.send(protocol.initialize(initId), 20_000)
        protocol.parse(initRaw)
        runCatching { transport.send(protocol.initialized(), 10_000) }
        val toolsRaw = transport.send(protocol.listTools(protocol.nextId()), 20_000)
        val tools = protocol.parseTools(protocol.parse(toolsRaw))
        val conn = McpConnection(server, transport, protocol, tools, emptyList(), emptyList())
        connections[server.id] = conn
        tools.forEach { spec ->
            registry.register(McpBoundTool(server.id, spec, this, permissions))
        }
        servers.upsert(server.copy(lastSeenAt = System.currentTimeMillis(), lastError = null))
        OxygenLog.i("mcp", "Connected ${server.name} tools=${tools.size}")
        conn
    }

    suspend fun disconnect(id: String) = mutex.withLock {
        connections.remove(id)?.transport?.close()
    }

    suspend fun execute(serverId: String, call: ToolCall, timeoutMs: Long = 30_000): ToolResult {
        val conn = connections[serverId] ?: servers.get(serverId)?.let { connect(it) }
            ?: throw OxygenError.McpConnectionFailed(serverId, "unknown server")
        val spec = conn.tools.firstOrNull { it.name == call.name }
        val allowed = permissions.allows("mcp", "${serverId}/${call.name}", spec?.destructive == true, userConfirmed = true)
        if (!allowed) throw OxygenError.PermissionDenied("mcp:${call.name}")
        return try {
            val raw = conn.transport.send(conn.protocol.callTool(conn.protocol.nextId(), call.name, call.arguments), timeoutMs)
            val parsed = conn.protocol.parseToolResult(call, conn.protocol.parse(raw))
            val clipped = if (parsed.content.length > 12_000) parsed.copy(content = parsed.content.take(12_000), truncated = true) else parsed
            clipped
        } catch (e: OxygenError) {
            throw e
        } catch (e: Exception) {
            throw OxygenError.McpToolFailed(call.name, e.message ?: "failed")
        }
    }

    fun connections(): List<McpConnection> = connections.values.toList()

    suspend fun discoverAll() {
        servers.all().filter { it.enabled }.forEach { runCatching { connect(it) } }
    }
}

private class McpBoundTool(
    private val serverId: String,
    override val spec: ToolSpec,
    private val client: McpClientManager,
    private val permissions: ToolPermissionManager,
) : Tool {
    override suspend fun invoke(call: ToolCall): ToolResult {
        val mode = permissions.modeFor("mcp", "$serverId/${spec.name}")
        if (mode == PermissionMode.DISABLED) {
            return ToolResult(call.id, spec.name, false, "", error = "disabled")
        }
        return client.execute(serverId, call)
    }
}

fun newMcpServer(name: String, endpoint: String, transport: String = "http"): McpServerEntity =
    McpServerEntity(
        id = UUID.randomUUID().toString(),
        name = name,
        transport = transport,
        endpoint = endpoint,
        enabled = true,
        permissionMode = PermissionMode.ASK.name,
        createdAt = System.currentTimeMillis(),
    )

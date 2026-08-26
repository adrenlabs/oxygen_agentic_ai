package com.oxygen.ai.di

import android.content.Context
import com.oxygen.ai.agent.AgentCore
import com.oxygen.ai.agent.AgentOrchestrator
import com.oxygen.ai.agent.ExecutionManager
import com.oxygen.ai.agent.TaskPlanner
import com.oxygen.ai.agent.ToolPlanner
import com.oxygen.ai.attachments.AttachmentStore
import com.oxygen.ai.chat.ConversationRepository
import com.oxygen.ai.context.OxygenContextEngine
import com.oxygen.ai.core.device.DeviceProfiler
import com.oxygen.ai.core.net.NetworkMonitor
import com.oxygen.ai.data.db.OxygenDatabase
import com.oxygen.ai.diagnostics.DiagnosticsCollector
import com.oxygen.ai.drive.DriveRestClient
import com.oxygen.ai.drive.OxygenSyncLayer
import com.oxygen.ai.inference.LlamaCppInferenceEngine
import com.oxygen.ai.inference.LlamaCppRuntime
import com.oxygen.ai.mcp.McpClientManager
import com.oxygen.ai.mcp.McpServerManager
import com.oxygen.ai.memory.MemoryRepository
import com.oxygen.ai.models.ModelManager
import com.oxygen.ai.pdf.PdfExtractor
import com.oxygen.ai.rag.Chunker
import com.oxygen.ai.rag.NgramHashEmbeddingProvider
import com.oxygen.ai.rag.ParserRegistry
import com.oxygen.ai.rag.RagPipeline
import com.oxygen.ai.rag.defaultParsers
import com.oxygen.ai.reasoning.ReasoningController
import com.oxygen.ai.search.SearxngSearchProvider
import com.oxygen.ai.security.SecretStore
import com.oxygen.ai.settings.SettingsRepository
import com.oxygen.ai.telegram.TelegramBotAdapter
import com.oxygen.ai.telegram.TelegramGateway
import com.oxygen.ai.tools.CalculatorTool
import com.oxygen.ai.tools.DateTimeTool
import com.oxygen.ai.tools.DriveBackupTool
import com.oxygen.ai.tools.FilesListTool
import com.oxygen.ai.tools.MemorySaveTool
import com.oxygen.ai.tools.MemorySearchTool
import com.oxygen.ai.tools.RagSearchTool
import com.oxygen.ai.tools.TelegramSendTool
import com.oxygen.ai.tools.ToolPermissionManager
import com.oxygen.ai.tools.ToolRegistry
import com.oxygen.ai.tools.WebSearchTool
import java.util.UUID

class OxygenGraph(context: Context) {
    val appContext: Context = context.applicationContext
    val db: OxygenDatabase = OxygenDatabase.create(appContext)
    val secrets = SecretStore(appContext)
    val settings = SettingsRepository(db.settings())
    val network = NetworkMonitor(appContext)
    val profiler = DeviceProfiler(appContext)
    val embeddings = NgramHashEmbeddingProvider()
    val runtime = LlamaCppRuntime()
    val engine = LlamaCppInferenceEngine(runtime)
    val models = ModelManager(appContext, db.models(), runtime, settings, profiler)
    val conversations = ConversationRepository(db.conversations(), db.messages())
    val memory = MemoryRepository(db.memories(), embeddings)
    val pdf = PdfExtractor(appContext)
    val parsers = ParserRegistry(defaultParsers() + pdf)
    val rag = RagPipeline(db.documents(), embeddings, Chunker(), parsers)
    val attachments = AttachmentStore(appContext, db.attachments())
    val permissions = ToolPermissionManager(db.permissions())
    val tools = ToolRegistry()
    val mcpServers = McpServerManager(db.mcp())
    val mcp = McpClientManager(mcpServers, permissions, tools)
    val search = SearxngSearchProvider(
        endpointProvider = { settings.searxngEndpoint() },
        enabled = { settings.webSearchEnabled() && !settings.localOnly() },
        network = network,
    )
    val driveClient = DriveRestClient(secrets)
    val sync = OxygenSyncLayer(
        dao = db.sync(),
        secrets = secrets,
        network = network,
        mode = { settings.driveMode() },
        deviceId = {
            settings.snapshot()[SettingsRepository.Keys.DEVICE_ID] ?: "unknown"
        },
        rest = driveClient,
    )
    val telegramAdapter = TelegramBotAdapter(secrets)
    lateinit var telegram: TelegramGateway
        private set
    val contextEngine = OxygenContextEngine()
    val reasoning = ReasoningController()
    val taskPlanner = TaskPlanner()
    val toolPlanner = ToolPlanner(tools)
    val execution = ExecutionManager(tools, permissions)
    val orchestrator: AgentOrchestrator
    val agent: AgentCore
    val diagnostics: DiagnosticsCollector

    init {
        orchestrator = AgentOrchestrator(
            inference = engine,
            models = models,
            contextEngine = contextEngine,
            memory = memory,
            rag = rag,
            search = search,
            taskPlanner = taskPlanner,
            toolPlanner = toolPlanner,
            execution = execution,
            reasoning = reasoning,
            settings = settings,
            sessionProvider = { models.currentSession() },
        )
        agent = AgentCore(orchestrator)
        telegram = TelegramGateway(
            adapter = telegramAdapter,
            dao = db.telegram(),
            network = network,
            enabled = { settings.telegramEnabled() },
            sinkFactory = { agent },
        )
        tools.register(MemorySearchTool(memory))
        tools.register(MemorySaveTool(memory))
        tools.register(RagSearchTool(rag))
        tools.register(WebSearchTool(search))
        tools.register(CalculatorTool())
        tools.register(DateTimeTool())
        tools.register(TelegramSendTool(telegram))
        tools.register(DriveBackupTool(sync))
        tools.register(
            FilesListTool {
                rag.observeDocuments()
                db.documents().all().joinToString("\n") { "- ${it.displayName} (${it.status})" }
                    .ifBlank { "No documents." }
            },
        )
        diagnostics = DiagnosticsCollector(profiler, runtime, models, settings, appContext.filesDir, db)
    }

    suspend fun start() {
        settings.seedDefaults()
        val existing = settings.get(SettingsRepository.Keys.DEVICE_ID)
        if (existing.isBlank()) {
            settings.put(SettingsRepository.Keys.DEVICE_ID, UUID.randomUUID().toString())
        }
        settings.refresh()
        network.start()
        models.refreshActive()
        runCatching { models.discover() }
        if (settings.mcpEnabled()) runCatching { mcp.discoverAll() }
    }

    fun shutdown() {
        network.stop()
        telegram.stop()
    }
}

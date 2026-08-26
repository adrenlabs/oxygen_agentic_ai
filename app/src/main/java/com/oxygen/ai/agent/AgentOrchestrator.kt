package com.oxygen.ai.agent

import com.oxygen.ai.context.ContextPack
import com.oxygen.ai.context.OxygenContextEngine
import com.oxygen.ai.context.PromptMessage
import com.oxygen.ai.context.RankedItem
import com.oxygen.ai.core.error.OxygenError
import com.oxygen.ai.core.logging.OxygenLog
import com.oxygen.ai.inference.ChatTemplate
import com.oxygen.ai.inference.GenerationEvent
import com.oxygen.ai.inference.InferenceEngine
import com.oxygen.ai.inference.ModelSession
import com.oxygen.ai.memory.ConversationMemoryPolicy
import com.oxygen.ai.memory.MemoryRepository
import com.oxygen.ai.models.ModelManager
import com.oxygen.ai.rag.RagPipeline
import com.oxygen.ai.rag.RetrievalQuery
import com.oxygen.ai.reasoning.ReasoningController
import com.oxygen.ai.reasoning.TaskMode
import com.oxygen.ai.search.SearchHit
import com.oxygen.ai.search.SearchProvider
import com.oxygen.ai.search.SearchRequest
import com.oxygen.ai.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import java.util.UUID

class ContextPlanner {
    fun pack(
        memories: List<RankedItem>,
        rag: List<RankedItem>,
        web: List<RankedItem>,
        tools: List<RankedItem>,
    ): ContextPack = ContextPack(memories, rag, tools, web)
}

class AgentOrchestrator(
    private val inference: InferenceEngine,
    private val models: ModelManager,
    private val contextEngine: OxygenContextEngine,
    private val memory: MemoryRepository,
    private val rag: RagPipeline,
    private val search: SearchProvider,
    private val taskPlanner: TaskPlanner,
    private val toolPlanner: ToolPlanner,
    private val execution: ExecutionManager,
    private val reasoning: ReasoningController,
    private val settings: SettingsRepository,
    private val sessionProvider: () -> ModelSession?,
    private val limits: AgentLimits = AgentLimits(),
) {
    fun run(
        request: AgentRequest,
        conversationId: String,
        history: List<PromptMessage>,
    ): Flow<AgentEvent> = channelFlow {
        val session = AgentSession(UUID.randomUUID().toString(), conversationId, request)
        val deadline = System.currentTimeMillis() + limits.maxExecutionTimeMs
        fun emit(state: AgentState) {
            session.state = state
            trySend(AgentEvent.State(state))
        }
        try {
            emit(AgentState.THINKING)
            val mode = request.taskMode ?: TaskMode.CHAT
            val hasDocs = request.documentIds.isNotEmpty()
            val complexity = taskPlanner.classify(request.text, mode, hasDocs)
            val profile = reasoning.resolve(
                requested = request.reasoningLevel ?: settings.reasoningLevel(),
                complexity = complexity,
                mode = mode,
                nativeContext = models.activeProfile()?.contextLimit ?: 32_768,
                deviceSafeContext = settings.deviceSafeContext(),
            )
            session.profile = profile
            emit(AgentState.PLANNING)
            val webOn = settings.webSearchEnabled()
            val plan = taskPlanner.plan(request.text, complexity, mode, hasDocs, webOn)
            session.plan = plan

            if (plan.needsMemory && settings.memoryEnabled()) {
                emit(AgentState.RETRIEVING_MEMORY)
                session.memories = memory.retrieve(request.text, profile.budget.memoryCount, profile.budget.retrievalMinScore)
            }
            if (plan.needsRag && settings.ragEnabled()) {
                emit(AgentState.RETRIEVING_RAG)
                session.rag = rag.retrieve(
                    RetrievalQuery(request.text, profile.budget.ragDepth, profile.budget.retrievalMinScore, request.documentIds),
                )
                session.rag.forEach { trySend(AgentEvent.Citation(it)) }
            }
            if (plan.needsWeb && webOn) {
                emit(AgentState.SEARCHING_WEB)
                val hits = runCatching {
                    search.search(SearchRequest(request.text, profile.budget.webDepth))
                }.getOrNull()
                session.web = (hits?.results ?: emptyList()).map { it.toRanked() }
                session.web.forEach { trySend(AgentEvent.Citation(it)) }
            }

            val pending = ArrayDeque(toolPlanner.initialCalls(request, plan).filter {
                it.name != "web_search" && it.name != "rag_search" && it.name != "memory_search"
            })

            var lastText = ""
            while (session.iterations < minOf(profile.budget.maxIterations, limits.maxIterations)) {
                if (session.cancelled) {
                    emit(AgentState.CANCELLED)
                    trySend(AgentEvent.Completed(partial(session, lastText, true, null)))
                    return@channelFlow
                }
                if (System.currentTimeMillis() > deadline) {
                    emit(AgentState.COMPLETED)
                    trySend(AgentEvent.Completed(partial(session, lastText.ifBlank { "Stopped: time limit reached." }, true, "time")))
                    return@channelFlow
                }
                session.iterations++

                while (pending.isNotEmpty() && session.toolCalls < minOf(profile.budget.maxToolCalls, limits.maxToolCalls)) {
                    val call = pending.removeFirst()
                    session.toolCalls++
                    val state = if (call.name == "web_search") AgentState.SEARCHING_WEB else AgentState.CALLING_TOOL
                    emit(state)
                    trySend(AgentEvent.Tool(call.name, "running"))
                    val result = execution.execute(
                        call,
                        timeoutMs = 25_000,
                        maxOutput = limits.maxToolOutputSize,
                        userConfirmed = request.confirmedTools.contains(call.name),
                    )
                    session.toolResults += result
                    trySend(AgentEvent.Tool(call.name, if (result.ok) "done" else "error"))
                    if (call.name == "web_search" && result.ok) {
                        session.web = session.web + RankedItem(call.id, "web", result.content, 0.5f, "web")
                    }
                }

                emit(AgentState.PROCESSING_RESULT)
                val modelSession = sessionProvider()
                    ?: throw OxygenError.ModelNotFound(models.activeProfile()?.filePath ?: "(none)")
                val pack = ContextPlanner().pack(
                    session.memories,
                    session.rag,
                    session.web,
                    session.toolResults.map {
                        RankedItem(it.callId, it.name, it.content.ifBlank { it.error ?: "" }, if (it.ok) 0.8f else 0.2f, "tool")
                    },
                )
                val assembled = contextEngine.assemble(
                    userText = request.text,
                    history = history,
                    pack = pack,
                    profile = profile,
                    systemPrompt = request.systemPrompt,
                    templateKind = ChatTemplate.detect(
                        models.activeProfile()?.architecture ?: "",
                        models.activeProfile()?.chatTemplate ?: "",
                        models.activeProfile()?.displayName ?: "",
                    ),
                    nativeContext = models.activeProfile()?.contextLimit ?: 32_768,
                    deviceSafeContext = settings.deviceSafeContext(),
                    allowExtended = settings.allowExtendedContext() && profile.budget.contextTokens > 32_768,
                )
                session.prompt = assembled

                emit(AgentState.GENERATING)
                val gen = StringBuilder()
                inference.generate(modelSession, assembled.rendered, profile.generation).collect { ev ->
                    when (ev) {
                        is GenerationEvent.Token -> {
                            gen.append(ev.text)
                            trySend(AgentEvent.Token(ev.text))
                        }
                        is GenerationEvent.Metrics -> trySend(
                            AgentEvent.Metrics(ev.promptTokens, ev.generatedTokens, ev.tokensPerSecond),
                        )
                        is GenerationEvent.Error -> throw OxygenError.InferenceFailed(ev.message)
                        is GenerationEvent.Completed -> if (ev.cancelled) session.cancelled = true
                    }
                }
                lastText = stripHiddenThinking(gen.toString())
                session.output = StringBuilder(lastText)
                val more = toolPlanner.parseModelToolCalls(gen.toString())
                if (more.isEmpty() || session.toolCalls >= minOf(profile.budget.maxToolCalls, limits.maxToolCalls)) break
                pending.addAll(more)
            }

            if (settings.memoryEnabled()) {
                runCatching {
                    memory.decideAndApply(request.text, lastText, conversationId, ConversationMemoryPolicy.AUTO)
                }
            }
            emit(AgentState.COMPLETED)
            trySend(
                AgentEvent.Completed(
                    AgentTurnResult(
                        conversationId = conversationId,
                        messageId = UUID.randomUUID().toString(),
                        text = lastText,
                        state = AgentState.COMPLETED,
                        citations = (session.rag + session.web),
                        partial = false,
                    ),
                ),
            )
        } catch (e: OxygenError) {
            OxygenLog.e("agent", e.developerMessage, e)
            emit(if (e is OxygenError.Cancelled) AgentState.CANCELLED else AgentState.FAILED)
            trySend(AgentEvent.Failed(e.userMessage))
        } catch (e: Exception) {
            OxygenLog.e("agent", "Unhandled agent failure", e)
            emit(AgentState.FAILED)
            trySend(AgentEvent.Failed("The agent stopped because of an unexpected error."))
        }
    }

    private fun partial(session: AgentSession, text: String, partial: Boolean, limit: String?) =
        AgentTurnResult(session.conversationId, UUID.randomUUID().toString(), text, session.state, session.rag + session.web, partial, limit)

    private fun stripHiddenThinking(text: String): String {
        return text
            .replace(Regex("<think>[\\s\\S]*?</think>"), "")
            .replace(Regex("<thought>[\\s\\S]*?</thought>"), "")
            .trim()
    }

    private fun SearchHit.toRanked() = RankedItem(url, title, snippet, 0.5f, "web", url = url)
}

class AgentCore(
    val orchestrator: AgentOrchestrator,
) : AgentSink {
    override suspend fun submit(request: AgentRequest): AgentTurnResult {
        var last: AgentTurnResult? = null
        orchestrator.run(request, request.conversationId ?: UUID.randomUUID().toString(), emptyList()).collect { ev ->
            if (ev is AgentEvent.Completed) last = ev.result
            if (ev is AgentEvent.Failed) {
                last = AgentTurnResult(
                    request.conversationId ?: "",
                    UUID.randomUUID().toString(),
                    ev.message,
                    AgentState.FAILED,
                    emptyList(),
                    true,
                    ev.message,
                )
            }
        }
        return last ?: AgentTurnResult(
            request.conversationId ?: "",
            UUID.randomUUID().toString(),
            "",
            AgentState.FAILED,
            emptyList(),
            true,
            "no result",
        )
    }
}

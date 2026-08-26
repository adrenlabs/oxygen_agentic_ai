package com.oxygen.ai.ui.vm

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oxygen.ai.OxygenApplication
import com.oxygen.ai.agent.AgentEvent
import com.oxygen.ai.agent.AgentRequest
import com.oxygen.ai.agent.AgentState
import com.oxygen.ai.attachments.AttachmentClass
import com.oxygen.ai.attachments.AttachmentClassifier
import com.oxygen.ai.context.RankedItem
import com.oxygen.ai.core.error.OxygenError
import com.oxygen.ai.core.identity.OxygenBrand
import com.oxygen.ai.data.db.entities.ConversationEntity
import com.oxygen.ai.data.db.entities.MessageEntity
import com.oxygen.ai.di.OxygenGraph
import com.oxygen.ai.reasoning.ReasoningLevel
import com.oxygen.ai.reasoning.TaskMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ChatUiState(
    val conversation: ConversationEntity? = null,
    val messages: List<MessageEntity> = emptyList(),
    val input: String = "",
    val streaming: String = "",
    val state: AgentState = AgentState.IDLE,
    val modelName: String = OxygenBrand.DEFAULT_MODEL_DISPLAY,
    val reasoning: ReasoningLevel = ReasoningLevel.MEDIUM,
    val taskMode: TaskMode = TaskMode.CHAT,
    val contextUsed: Int = 0,
    val contextTotal: Int = 8192,
    val toolsMode: String = "Auto",
    val error: String? = null,
    val citations: List<RankedItem> = emptyList(),
    val attachments: List<String> = emptyList(),
    val online: Boolean = true,
    val generating: Boolean = false,
    val metrics: String? = null,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val graph: OxygenGraph = (app as OxygenApplication).graph
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state
    private var genJob: Job? = null
    private var messageJob: Job? = null

    init {
        viewModelScope.launch {
            graph.settings.refresh()
            _state.update {
                it.copy(
                    reasoning = graph.settings.reasoningLevel(),
                    taskMode = graph.settings.taskMode(),
                    modelName = graph.models.activeProfile()?.displayName ?: OxygenBrand.DEFAULT_MODEL_DISPLAY,
                    contextTotal = graph.settings.deviceSafeContext(),
                    online = graph.network.online.value,
                )
            }
        }
        viewModelScope.launch {
            graph.network.online.collect { on -> _state.update { it.copy(online = on) } }
        }
    }

    fun bindConversation(id: String?) {
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            val existing = _state.value.conversation
            val conv = when {
                id != null -> graph.conversations.get(id) ?: graph.conversations.create()
                existing != null -> existing
                else -> graph.conversations.create(
                    modelId = graph.settings.activeModelId(),
                    reasoning = graph.settings.reasoningLevel().name,
                    taskMode = graph.settings.taskMode().name,
                )
            }
            _state.update { it.copy(conversation = conv) }
            graph.conversations.observeMessages(conv.id).collect { msgs ->
                _state.update { s -> s.copy(messages = msgs) }
            }
        }
    }

    fun onInput(v: String) = _state.update { it.copy(input = v) }

    fun setReasoning(level: ReasoningLevel) {
        _state.update { it.copy(reasoning = level) }
        viewModelScope.launch { graph.settings.put(com.oxygen.ai.settings.SettingsRepository.Keys.REASONING, level.name) }
    }

    fun setTaskMode(mode: TaskMode) {
        _state.update { it.copy(taskMode = mode) }
        viewModelScope.launch { graph.settings.put(com.oxygen.ai.settings.SettingsRepository.Keys.TASK_MODE, mode.name) }
    }

    fun attach(uri: Uri, name: String, mime: String, size: Long) {
        val cid = _state.value.conversation?.id ?: return
        viewModelScope.launch {
            runCatching {
                val att = graph.attachments.import(uri, cid, name, mime, size)
                val cls = AttachmentClassifier.classify(name, size)
                if (cls == AttachmentClass.RAG_INDEXED) {
                    val doc = graph.rag.indexFile(File(att.localPath), name, mime, uri.toString())
                    graph.conversations.get(cid)?.let { c ->
                        graph.db.conversations().upsert(c.copy(ragSources = c.ragSources + doc.id))
                    }
                }
                _state.update { it.copy(attachments = it.attachments + name) }
            }.onFailure { e ->
                _state.update { it.copy(error = (e as? OxygenError)?.userMessage ?: "Attachment failed") }
            }
        }
    }

    fun send() {
        val text = _state.value.input.trim()
        val conv = _state.value.conversation ?: return
        if (text.isBlank() || _state.value.generating) return
        _state.update { it.copy(input = "", streaming = "", generating = true, error = null, state = AgentState.THINKING) }
        viewModelScope.launch {
            graph.conversations.addMessage(conv.id, "user", text)
        }
        genJob = viewModelScope.launch {
            val history = graph.conversations.historyAsPrompt(conv.id)
            val docs = conv.ragSources
            val req = AgentRequest(
                conversationId = conv.id,
                text = text,
                reasoningLevel = _state.value.reasoning,
                taskMode = _state.value.taskMode,
                systemPrompt = conv.systemPrompt,
                documentIds = docs,
            )
            var acc = StringBuilder()
            var metrics: Triple<Int, Int, Double>? = null
            var citations = emptyList<RankedItem>()
            graph.orchestrator.run(req, conv.id, history).collect { ev ->
                when (ev) {
                    is AgentEvent.State -> _state.update { it.copy(state = ev.state) }
                    is AgentEvent.Token -> {
                        acc.append(ev.text)
                        _state.update { it.copy(streaming = acc.toString()) }
                    }
                    is AgentEvent.Metrics -> {
                        metrics = Triple(ev.promptTokens, ev.generatedTokens, ev.tokensPerSecond)
                        _state.update {
                            it.copy(
                                contextUsed = ev.promptTokens,
                                metrics = "${ev.generatedTokens} tok · ${"%.1f".format(ev.tokensPerSecond)} t/s",
                            )
                        }
                    }
                    is AgentEvent.Citation -> {
                        citations = citations + ev.item
                        _state.update { it.copy(citations = citations) }
                    }
                    is AgentEvent.Completed -> {
                        val body = ev.result.text.ifBlank { acc.toString() }
                        graph.conversations.addMessage(
                            conv.id, "assistant", body, modelId = graph.models.activeProfile()?.modelId,
                            citations = ev.result.citations, metrics = metrics,
                        )
                        _state.update { it.copy(generating = false, streaming = "", state = AgentState.IDLE) }
                    }
                    is AgentEvent.Failed -> {
                        graph.conversations.addMessage(conv.id, "assistant", ev.message, status = "ERROR")
                        _state.update { it.copy(generating = false, error = ev.message, state = AgentState.FAILED) }
                    }
                    is AgentEvent.Tool -> { }
                }
            }
        }
    }

    fun stop() {
        graph.models.currentSession()?.let { graph.engine.cancel(it) }
        genJob?.cancel()
        _state.update { it.copy(generating = false, state = AgentState.CANCELLED) }
    }

    fun retry() {
        val lastUser = _state.value.messages.lastOrNull { it.role == "user" } ?: return
        _state.update { it.copy(input = lastUser.content) }
        send()
    }

    fun regenerate() {
        val lastUser = _state.value.messages.lastOrNull { it.role == "user" } ?: return
        viewModelScope.launch {
            _state.value.messages.lastOrNull { it.role == "assistant" }?.let { graph.conversations.deleteMessage(it.id) }
            _state.update { it.copy(input = lastUser.content) }
            send()
        }
    }
}

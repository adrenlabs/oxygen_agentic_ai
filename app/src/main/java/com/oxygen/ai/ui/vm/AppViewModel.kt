package com.oxygen.ai.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oxygen.ai.OxygenApplication
import com.oxygen.ai.data.db.entities.ConversationEntity
import com.oxygen.ai.data.db.entities.DocumentEntity
import com.oxygen.ai.data.db.entities.McpServerEntity
import com.oxygen.ai.data.db.entities.ModelProfileEntity
import com.oxygen.ai.diagnostics.DiagnosticsSnapshot
import com.oxygen.ai.di.OxygenGraph
import com.oxygen.ai.mcp.newMcpServer
import com.oxygen.ai.memory.MemoryRecord
import com.oxygen.ai.settings.SettingsRepository
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val graph: OxygenGraph = (app as OxygenApplication).graph

    val conversations: StateFlow<List<ConversationEntity>> =
        graph.conversations.observeConversations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val models: StateFlow<List<ModelProfileEntity>> =
        graph.models.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val documents: StateFlow<List<DocumentEntity>> =
        graph.rag.observeDocuments().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val memories: StateFlow<List<MemoryRecord>> =
        graph.memory.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val mcpServers: StateFlow<List<McpServerEntity>> =
        graph.mcpServers.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings: StateFlow<Map<String, String>> =
        graph.settings.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val diagnostics = MutableStateFlow<DiagnosticsSnapshot?>(null)
    val notice = MutableStateFlow<String?>(null)

    fun refreshDiagnostics() {
        diagnostics.value = graph.diagnostics.capture()
    }

    fun importModel(uri: Uri, name: String) = viewModelScope.launch {
        runCatching {
            val dest = java.io.File(graph.models.modelsDir(), com.oxygen.ai.security.PathSafety.sanitizeFileName(name))
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            } ?: error("Unable to read model")
            graph.models.importFile(dest, name)
        }.onSuccess { notice.value = "Imported ${it.displayName}" }
            .onFailure { notice.value = it.message }
    }

    fun loadModel(id: String) = viewModelScope.launch {
        runCatching { graph.models.load(id) }
            .onSuccess { notice.value = "Model loaded" }
            .onFailure { notice.value = it.message }
    }

    fun deleteModel(id: String) = viewModelScope.launch { graph.models.delete(id) }
    fun deleteConversation(id: String) = viewModelScope.launch { graph.conversations.delete(id) }
    fun renameConversation(id: String, title: String) = viewModelScope.launch { graph.conversations.rename(id, title) }
    fun deleteMemory(id: String) = viewModelScope.launch { graph.memory.delete(id) }
    fun deleteDocument(id: String) = viewModelScope.launch { graph.rag.delete(id) }

    fun putSetting(key: String, value: String) = viewModelScope.launch {
        graph.settings.put(key, value)
        graph.settings.refresh()
    }

    fun addMcp(name: String, endpoint: String) = viewModelScope.launch {
        val s = newMcpServer(name, endpoint)
        graph.mcpServers.upsert(s)
        runCatching { graph.mcp.connect(s) }.onFailure { notice.value = it.message }
    }

    fun setTelegramToken(token: String) {
        graph.secrets.put(com.oxygen.ai.security.SecretStore.TELEGRAM_BOT_TOKEN, token)
    }

    fun flag(key: String): Boolean = settings.value[key]?.toBoolean() ?: when (key) {
        SettingsRepository.Keys.MEMORY, SettingsRepository.Keys.RAG, SettingsRepository.Keys.LOCAL_ONLY -> true
        else -> false
    }
}

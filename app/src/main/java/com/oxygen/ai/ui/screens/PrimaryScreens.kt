package com.oxygen.ai.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oxygen.ai.core.identity.OxygenBrand
import com.oxygen.ai.settings.SettingsRepository
import com.oxygen.ai.telegram.TelegramService
import com.oxygen.ai.ui.components.OxygenDocumentCard
import com.oxygen.ai.ui.components.OxygenMemoryCard
import com.oxygen.ai.ui.components.OxygenModelCard
import com.oxygen.ai.ui.theme.OxygenDimensions
import com.oxygen.ai.ui.vm.AppViewModel

@Composable
fun HomeScreen(
    onOpenChat: () -> Unit,
    onOpenModels: () -> Unit,
    onNavigate: (String) -> Unit,
    vm: AppViewModel,
) {
    val models by vm.models.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().padding(OxygenDimensions.screenPad).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(OxygenBrand.APP_NAME, style = MaterialTheme.typography.displaySmall)
        Text(OxygenBrand.DESCRIPTION, style = MaterialTheme.typography.bodyLarge)
        Text("A local-first personal agent. The chat is one interface — the Agent Core is the system.", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onOpenChat, modifier = Modifier.fillMaxWidth()) { Text("Open chat") }
        OutlinedButton(onClick = onOpenModels, modifier = Modifier.fillMaxWidth()) { Text("Import a GGUF model") }
        Text("Installed models: ${models.size}", style = MaterialTheme.typography.labelLarge)
        Text("More", style = MaterialTheme.typography.titleMedium)
        listOf("memory" to "Memory", "rag" to "RAG", "tools" to "Tools", "mcp" to "MCP", "web" to "Web search", "telegram" to "Telegram", "diagnostics" to "Diagnostics", "about" to "About").forEach { (route, label) ->
            TextButton(onClick = { onNavigate(route) }) { Text(label) }
        }
    }
}

@Composable
fun ConversationsScreen(vm: AppViewModel, onOpen: (String) -> Unit) {
    val items by vm.conversations.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items, key = { it.id }) { c ->
            Column(
                Modifier.fillMaxWidth().clickable { onOpen(c.id) }.padding(12.dp),
            ) {
                Text(c.title, style = MaterialTheme.typography.titleMedium)
                Text(c.reasoningProfile + " · " + c.taskMode, style = MaterialTheme.typography.bodyMedium)
                Row {
                    TextButton(onClick = { vm.deleteConversation(c.id) }) { Text("Delete") }
                    TextButton(onClick = { vm.renameConversation(c.id, c.title + " *") }) { Text("Rename") }
                }
            }
        }
        if (items.isEmpty()) item { Text("No conversations yet.") }
    }
}

@Composable
fun ModelsScreen(vm: AppViewModel) {
    val items by vm.models.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Models", style = MaterialTheme.typography.headlineMedium)
        Text("Import a GGUF such as ${OxygenBrand.DEFAULT_MODEL_FILE}. Models are never bundled in the APK.", style = MaterialTheme.typography.bodyMedium)
        notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(items, key = { it.modelId }) { m ->
                OxygenModelCard(
                    name = m.displayName,
                    detail = "${m.architecture} · ${m.quantization} · ctx ${m.contextLimit} · ${m.fileSize / (1024 * 1024)} MB",
                    selected = false,
                    onClick = { vm.loadModel(m.modelId) },
                )
                TextButton(onClick = { vm.deleteModel(m.modelId) }) { Text("Delete") }
            }
        }
    }
}

@Composable
fun MemoryScreen(vm: AppViewModel) {
    val items by vm.memories.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Long-term memory", style = MaterialTheme.typography.headlineMedium) }
        items(items, key = { it.id }) { m ->
            OxygenMemoryCard("", m.content, m.category.name)
            TextButton(onClick = { vm.deleteMemory(m.id) }) { Text("Delete") }
        }
        if (items.isEmpty()) item { Text("Nothing stored yet. OXYGEN only keeps durable facts, never every message.") }
    }
}

@Composable
fun DocumentsScreen(vm: AppViewModel) {
    val items by vm.documents.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Documents", style = MaterialTheme.typography.headlineMedium) }
        items(items, key = { it.id }) { d ->
            OxygenDocumentCard(d.displayName, d.status, d.pageCount) {}
            TextButton(onClick = { vm.deleteDocument(d.id) }) { Text("Delete") }
        }
        if (items.isEmpty()) item { Text("Attach a PDF or text file from Chat to index it with RAG.") }
    }
}

@Composable
fun RagScreen(vm: AppViewModel) {
    val items by vm.documents.collectAsStateWithLifecycle()
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("RAG", style = MaterialTheme.typography.headlineMedium)
        Text("Documents are chunked, embedded with a replaceable local embedding provider, and retrieved by hybrid cosine + BM25. Entire files are never dumped into the prompt.")
        Text("Indexed documents: ${items.size}")
        Text("Embedding provider: ${vm.graph.embeddings.id} (${vm.graph.embeddings.dimensions}d)")
    }
}

@Composable
fun ToolsScreen(vm: AppViewModel) {
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tools", style = MaterialTheme.typography.headlineMedium)
        vm.graph.tools.specs().forEach {
            Text("• ${it.name}  (${it.origin}) — ${it.description}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun McpScreen(vm: AppViewModel) {
    val servers by vm.mcpServers.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("MCP", style = MaterialTheme.typography.headlineMedium)
        Text("OXYGEN is an MCP client. Servers default to Ask Every Time.")
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(endpoint, { endpoint = it }, label = { Text("HTTP endpoint") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.addMcp(name, endpoint); name = ""; endpoint = "" }, enabled = name.isNotBlank() && endpoint.isNotBlank()) {
            Text("Add server")
        }
        servers.forEach {
            Text("${it.name} · ${it.endpoint} · ${it.permissionMode}")
        }
    }
}

@Composable
fun WebSearchScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var endpoint by remember { mutableStateOf(settings[SettingsRepository.Keys.SEARXNG].orEmpty()) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Web search", style = MaterialTheme.typography.headlineMedium)
        Text("Primary provider is a SearXNG-compatible endpoint. No commercial provider is hardcoded.")
        OutlinedTextField(endpoint, { endpoint = it }, label = { Text("SearXNG base URL") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.putSetting(SettingsRepository.Keys.SEARXNG, endpoint) }) { Text("Save endpoint") }
        SettingToggle("Enable web search", settings[SettingsRepository.Keys.WEB].toBoolean()) {
            vm.putSetting(SettingsRepository.Keys.WEB, it.toString())
        }
    }
}

@Composable
fun TelegramScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var token by remember { mutableStateOf("") }
    val settings by vm.settings.collectAsStateWithLifecycle()
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Telegram", style = MaterialTheme.typography.headlineMedium)
        Text("Another interface to the same Agent Core. Disabled by default. Allowlist required.")
        OutlinedTextField(token, { token = it }, label = { Text("Bot token") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.setTelegramToken(token); token = "" }) { Text("Store token in Keystore") }
        SettingToggle("Enable Telegram", settings[SettingsRepository.Keys.TELEGRAM].toBoolean()) {
            vm.putSetting(SettingsRepository.Keys.TELEGRAM, it.toString())
        }
        Button(onClick = { ctx.startForegroundService(Intent(ctx, TelegramService::class.java)) }) {
            Text("Start gateway (foreground)")
        }
        OutlinedButton(onClick = { ctx.stopService(Intent(ctx, TelegramService::class.java)) }) {
            Text("Stop gateway")
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text("AI / Privacy", style = MaterialTheme.typography.titleMedium)
        SettingToggle("Local only", settings[SettingsRepository.Keys.LOCAL_ONLY].toBoolean()) {
            vm.putSetting(SettingsRepository.Keys.LOCAL_ONLY, it.toString())
        }
        SettingToggle("Memory", settings[SettingsRepository.Keys.MEMORY] != "false") {
            vm.putSetting(SettingsRepository.Keys.MEMORY, it.toString())
        }
        SettingToggle("RAG", settings[SettingsRepository.Keys.RAG] != "false") {
            vm.putSetting(SettingsRepository.Keys.RAG, it.toString())
        }
        SettingToggle("Web search", settings[SettingsRepository.Keys.WEB].toBoolean()) {
            vm.putSetting(SettingsRepository.Keys.WEB, it.toString())
        }
        SettingToggle("Telegram", settings[SettingsRepository.Keys.TELEGRAM].toBoolean()) {
            vm.putSetting(SettingsRepository.Keys.TELEGRAM, it.toString())
        }
        SettingToggle("MCP", settings[SettingsRepository.Keys.MCP].toBoolean()) {
            vm.putSetting(SettingsRepository.Keys.MCP, it.toString())
        }
        SettingToggle("Allow extended context (YaRN, only when needed)", settings[SettingsRepository.Keys.EXTENDED].toBoolean()) {
            vm.putSetting(SettingsRepository.Keys.EXTENDED, it.toString())
        }
        SettingToggle("Dynamic color", settings[SettingsRepository.Keys.DYNAMIC_COLOR].toBoolean()) {
            vm.putSetting(SettingsRepository.Keys.DYNAMIC_COLOR, it.toString())
        }
        Text("Drive mode: ${settings[SettingsRepository.Keys.DRIVE_MODE] ?: "LOCAL_ONLY"}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("LOCAL_ONLY", "DRIVE_BACKUP", "DRIVE_SYNC").forEach { mode ->
                TextButton(onClick = { vm.putSetting(SettingsRepository.Keys.DRIVE_MODE, mode) }) { Text(mode) }
            }
        }
        Text("Performance: ${settings[SettingsRepository.Keys.PERF] ?: "BALANCED"}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ECO", "BALANCED", "PERFORMANCE", "CUSTOM").forEach { p ->
                TextButton(onClick = { vm.putSetting(SettingsRepository.Keys.PERF, p) }) { Text(p) }
            }
        }
    }
}

@Composable
fun DiagnosticsScreen(vm: AppViewModel) {
    val snap by vm.diagnostics.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshDiagnostics() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Diagnostics", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = { vm.refreshDiagnostics() }) { Text("Refresh") }
        snap?.fields?.forEach { (k, v) ->
            Text("$k: $v", style = MaterialTheme.typography.bodyMedium)
        }
        Text("Logs", style = MaterialTheme.typography.titleMedium)
        snap?.logs?.takeLast(40)?.forEach {
            Text("${it.level} ${it.topic} ${it.message}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun AboutScreen() {
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(OxygenBrand.APP_NAME, style = MaterialTheme.typography.displaySmall)
        Text(OxygenBrand.CATEGORY, style = MaterialTheme.typography.titleMedium)
        Text(OxygenBrand.DESCRIPTION)
        Text("Package: ${OxygenBrand.PACKAGE_NAME}")
        Text("Default local model: ${OxygenBrand.DEFAULT_MODEL_FILE}")
        Text("No cloud LLM is required. Inference never secretly falls back to a remote model.")
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

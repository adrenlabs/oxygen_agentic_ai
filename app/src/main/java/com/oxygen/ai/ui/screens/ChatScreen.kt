package com.oxygen.ai.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oxygen.ai.agent.AgentState
import com.oxygen.ai.core.identity.OxygenBrand
import com.oxygen.ai.reasoning.TaskMode
import com.oxygen.ai.ui.components.OxygenAgentStatus
import com.oxygen.ai.ui.components.OxygenAttachmentCard
import com.oxygen.ai.ui.components.OxygenChatBubble
import com.oxygen.ai.ui.components.OxygenContextMeter
import com.oxygen.ai.ui.components.OxygenErrorCard
import com.oxygen.ai.ui.components.OxygenMessageCard
import com.oxygen.ai.ui.components.OxygenReasoningSelector
import com.oxygen.ai.ui.components.OxygenSourceCard
import com.oxygen.ai.ui.components.OxygenThinkingIndicator
import com.oxygen.ai.ui.theme.OxygenDimensions
import com.oxygen.ai.ui.vm.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(vm: ChatViewModel, conversationId: String?) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(conversationId) { vm.bindConversation(conversationId) }
    val list = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.streaming) {
        if (state.messages.isNotEmpty()) list.animateScrollToItem(state.messages.lastIndex)
    }
    val attachCtx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val mime = attachCtx.contentResolver.getType(uri) ?: "application/octet-stream"
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
            vm.attach(uri, name, mime, 0L)
        }
    }
    val clip = LocalClipboardManager.current

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(OxygenBrand.APP_NAME, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Model: ${state.modelName}  ·  Reasoning: ${state.reasoning.name.lowercase()}  ·  Context: ${state.contextUsed / 1000}K / ${state.contextTotal / 1000}K  ·  Tools: ${state.toolsMode}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        if (!state.online) {
            Text(
                "Offline — local chat, memory, and RAG still work.",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(OxygenDimensions.screenPad),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.messages, key = { it.id }) { msg ->
                if (msg.role == "user") {
                    OxygenChatBubble(msg.role, msg.content)
                } else {
                    OxygenMessageCard(
                        msg.role,
                        msg.content,
                        buildString {
                            if (msg.generatedTokens > 0) append("${msg.generatedTokens} tok  ")
                            if (msg.tokensPerSecond > 0) append("%.1f t/s".format(msg.tokensPerSecond))
                        }.ifBlank { null },
                    )
                    Row {
                        IconButton(onClick = { clip.setText(AnnotatedString(msg.content)) }) {
                            Icon(Icons.Outlined.ContentCopy, "Copy")
                        }
                        IconButton(onClick = { vm.regenerate() }) { Icon(Icons.Outlined.Refresh, "Regenerate") }
                        IconButton(onClick = { }) { Icon(Icons.Outlined.Share, "Share") }
                    }
                }
            }
            if (state.streaming.isNotBlank()) {
                item { OxygenMessageCard("assistant", state.streaming, state.metrics) }
            }
            if (state.generating && state.state != AgentState.GENERATING) {
                item { OxygenThinkingIndicator() }
            }
            if (state.state != AgentState.IDLE && state.state != AgentState.COMPLETED) {
                item { OxygenAgentStatus(state.state) }
            }
            items(state.citations, key = { it.id + it.title }) {
                OxygenSourceCard(it.title, listOfNotNull(it.url, it.page?.let { p -> "p.$p" }).joinToString(" · "))
            }
            state.error?.let { item { OxygenErrorCard(it) } }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OxygenContextMeter(state.contextUsed, state.contextTotal)
            OxygenReasoningSelector(state.reasoning, vm::setReasoning)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TaskMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.taskMode == mode,
                        onClick = { vm.setTaskMode(mode) },
                        label = { Text(mode.name.lowercase().replaceFirstChar { it.titlecase() }) },
                    )
                }
            }
            if (state.attachments.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.attachments.forEach { OxygenAttachmentCard(it, "attached") }
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Outlined.AttachFile, "Attach")
                }
                OutlinedTextField(
                    value = state.input,
                    onValueChange = vm::onInput,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message OXYGEN…") },
                    minLines = 1,
                    maxLines = 6,
                )
                if (state.generating) {
                    FilledIconButton(onClick = vm::stop) { Icon(Icons.Outlined.Stop, "Stop") }
                } else {
                    FilledIconButton(onClick = vm::send, enabled = state.input.isNotBlank()) {
                        Icon(Icons.Outlined.Send, "Send")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = vm::retry) { Text("Retry") }
                TextButton(onClick = vm::regenerate) { Text("Regenerate") }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

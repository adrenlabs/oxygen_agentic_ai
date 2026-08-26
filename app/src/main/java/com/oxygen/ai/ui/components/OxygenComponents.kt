package com.oxygen.ai.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oxygen.ai.agent.AgentState
import com.oxygen.ai.agent.userLabel
import com.oxygen.ai.reasoning.ReasoningCatalog
import com.oxygen.ai.reasoning.ReasoningLevel
import com.oxygen.ai.ui.theme.OxygenShapes

@Composable
fun OxygenChatBubble(role: String, text: String, modifier: Modifier = Modifier) {
    val mine = role == "user"
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            Modifier
                .widthIn(max = 640.dp)
                .clip(RoundedCornerShape(if (mine) 18.dp else 16.dp))
                .background(
                    if (mine) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                )
                .padding(12.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun OxygenMessageCard(role: String, text: String, meta: String? = null) {
    Card(
        shape = OxygenShapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (role == "user") "You" else "OXYGEN",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            MarkdownBody(text)
            if (!meta.isNullOrBlank()) {
                Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun MarkdownBody(text: String) {
    val blocks = remember(text) { splitFences(text) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            if (block.code) {
                Text(
                    block.text,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(OxygenShapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(10.dp),
                )
            } else {
                Text(block.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

data class MdBlock(val text: String, val code: Boolean)

fun splitFences(text: String): List<MdBlock> {
    val out = ArrayList<MdBlock>()
    val parts = text.split("```")
    parts.forEachIndexed { i, p ->
        val body = if (i % 2 == 1) p.substringAfter('\n', p) else p
        if (body.isNotBlank()) out.add(MdBlock(body.trim(), i % 2 == 1))
    }
    if (out.isEmpty()) out.add(MdBlock(text, false))
    return out
}

@Composable
fun OxygenAgentStatus(state: AgentState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.SmartToy, null, tint = MaterialTheme.colorScheme.primary)
        Text(state.userLabel(), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun OxygenToolCard(name: String, status: String) {
    Card(shape = OxygenShapes.small, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(status, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun OxygenMemoryCard(title: String, body: String, category: String) {
    Card(shape = OxygenShapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Memory, null)
                Spacer(Modifier.width(8.dp))
                Text(category, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (title.isNotBlank()) Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun OxygenModelCard(name: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = OxygenShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun OxygenDocumentCard(name: String, status: String, pages: Int, onClick: () -> Unit) {
    Card(onClick = onClick, shape = OxygenShapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Description, null)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Medium)
                Text("$status · $pages pages", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun OxygenSourceCard(title: String, subtitle: String) {
    Card(shape = OxygenShapes.small, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Link, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OxygenReasoningSelector(level: ReasoningLevel, onChange: (ReasoningLevel) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = it }) {
        TextField(
            value = ReasoningCatalog.levelLabel(level),
            onValueChange = {},
            readOnly = true,
            label = { Text("Reasoning") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            ReasoningLevel.entries.forEach {
                DropdownMenuItem(
                    text = { Text(ReasoningCatalog.levelLabel(it)) },
                    onClick = { onChange(it); expanded = false },
                )
            }
        }
    }
}

@Composable
fun OxygenContextMeter(used: Int, total: Int) {
    val frac = if (total == 0) 0f else (used.toFloat() / total).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Context", style = MaterialTheme.typography.labelLarge)
            Text("${used / 1000}K / ${total / 1000}K", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OxygenModelSelector(models: List<String>, selected: String?, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        models.forEach {
            FilterChip(selected = it == selected, onClick = { onSelect(it) }, label = { Text(it) })
        }
    }
}

@Composable
fun OxygenAttachmentCard(name: String, kind: String) {
    AssistChip(onClick = {}, label = { Text("$name · $kind") })
}

@Composable
fun OxygenThinkingIndicator() {
    val t = rememberInfiniteTransition(label = "think")
    val s by t.animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "s")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(10.dp)
                .scale(s)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Text("Thinking…", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun OxygenErrorCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = OxygenShapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Text(message)
        }
    }
}

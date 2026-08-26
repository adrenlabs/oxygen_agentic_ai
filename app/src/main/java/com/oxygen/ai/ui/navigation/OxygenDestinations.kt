package com.oxygen.ai.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.ui.graphics.vector.ImageVector

enum class OxygenDest(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val primary: Boolean,
) {
    Home("home", "Home", Icons.Outlined.Home, true),
    Chat("chat", "Chat", Icons.Outlined.Chat, true),
    Conversations("conversations", "Chats", Icons.Outlined.Forum, true),
    Models("models", "Models", Icons.Outlined.SmartToy, true),
    Memory("memory", "Memory", Icons.Outlined.Memory, false),
    Documents("documents", "Docs", Icons.Outlined.Description, true),
    Rag("rag", "RAG", Icons.Outlined.Storage, false),
    Tools("tools", "Tools", Icons.Outlined.Extension, false),
    Mcp("mcp", "MCP", Icons.Outlined.Hub, false),
    WebSearch("web", "Search", Icons.Outlined.TravelExplore, false),
    Telegram("telegram", "Telegram", Icons.Outlined.Search, false),
    Settings("settings", "Settings", Icons.Outlined.Settings, true),
    Diagnostics("diagnostics", "Diagnostics", Icons.Outlined.MonitorHeart, false),
    About("about", "About", Icons.Outlined.Info, false),
}

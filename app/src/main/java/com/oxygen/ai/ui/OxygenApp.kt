package com.oxygen.ai.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.oxygen.ai.settings.SettingsRepository
import com.oxygen.ai.ui.navigation.OxygenDest
import com.oxygen.ai.ui.screens.AboutScreen
import com.oxygen.ai.ui.screens.ChatScreen
import com.oxygen.ai.ui.screens.ConversationsScreen
import com.oxygen.ai.ui.screens.DiagnosticsScreen
import com.oxygen.ai.ui.screens.DocumentsScreen
import com.oxygen.ai.ui.screens.HomeScreen
import com.oxygen.ai.ui.screens.McpScreen
import com.oxygen.ai.ui.screens.MemoryScreen
import com.oxygen.ai.ui.screens.ModelsScreen
import com.oxygen.ai.ui.screens.RagScreen
import com.oxygen.ai.ui.screens.SettingsScreen
import com.oxygen.ai.ui.screens.TelegramScreen
import com.oxygen.ai.ui.screens.ToolsScreen
import com.oxygen.ai.ui.screens.WebSearchScreen
import com.oxygen.ai.ui.theme.OxygenTheme
import com.oxygen.ai.ui.vm.AppViewModel
import com.oxygen.ai.ui.vm.ChatViewModel

@Composable
fun OxygenApp(
    appViewModel: AppViewModel = viewModel(),
    chatViewModel: ChatViewModel = viewModel(),
) {
    val settings by appViewModel.settings.collectAsStateWithLifecycle()
    val darkPref = settings[SettingsRepository.Keys.DARK] ?: "system"
    val dark = when (darkPref) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val dynamic = settings[SettingsRepository.Keys.DYNAMIC_COLOR].toBoolean()
    OxygenTheme(darkTheme = dark, dynamicColor = dynamic) {
        Surface(Modifier.fillMaxSize()) {
            val nav = rememberNavController()
            val back by nav.currentBackStackEntryAsState()
            val route = back?.destination?.route ?: OxygenDest.Home.route
            val primary = remember { OxygenDest.entries.filter { it.primary } }
            NavigationSuiteScaffold(
                navigationSuiteItems = {
                    primary.forEach { dest ->
                        item(
                            selected = route.startsWith(dest.route),
                            onClick = {
                                nav.navigate(dest.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { androidx.compose.material3.Icon(dest.icon, dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                },
            ) {
                NavHost(navController = nav, startDestination = OxygenDest.Home.route) {
                    composable(OxygenDest.Home.route) {
                        HomeScreen(
                            onOpenChat = { nav.navigate(OxygenDest.Chat.route) },
                            onOpenModels = { nav.navigate(OxygenDest.Models.route) },
                            onNavigate = { nav.navigate(it) },
                            vm = appViewModel,
                        )
                    }
                    composable(OxygenDest.Chat.route) { ChatScreen(chatViewModel, null) }
                    composable(
                        "chat/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.StringType }),
                    ) { entry ->
                        ChatScreen(chatViewModel, entry.arguments?.getString("id"))
                    }
                    composable(OxygenDest.Conversations.route) {
                        ConversationsScreen(appViewModel) { id -> nav.navigate("chat/$id") }
                    }
                    composable(OxygenDest.Models.route) { ModelsScreen(appViewModel) }
                    composable(OxygenDest.Memory.route) { MemoryScreen(appViewModel) }
                    composable(OxygenDest.Documents.route) { DocumentsScreen(appViewModel) }
                    composable(OxygenDest.Rag.route) { RagScreen(appViewModel) }
                    composable(OxygenDest.Tools.route) { ToolsScreen(appViewModel) }
                    composable(OxygenDest.Mcp.route) { McpScreen(appViewModel) }
                    composable(OxygenDest.WebSearch.route) { WebSearchScreen(appViewModel) }
                    composable(OxygenDest.Telegram.route) { TelegramScreen(appViewModel) }
                    composable(OxygenDest.Settings.route) { SettingsScreen(appViewModel) }
                    composable(OxygenDest.Diagnostics.route) { DiagnosticsScreen(appViewModel) }
                    composable(OxygenDest.About.route) { AboutScreen() }
                }
            }
        }
    }
}

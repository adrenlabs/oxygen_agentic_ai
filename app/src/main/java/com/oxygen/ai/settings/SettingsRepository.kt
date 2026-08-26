package com.oxygen.ai.settings

import com.oxygen.ai.core.device.PerformanceProfile
import com.oxygen.ai.data.db.dao.SettingsDao
import com.oxygen.ai.data.db.entities.SettingsEntity
import com.oxygen.ai.drive.DriveMode
import com.oxygen.ai.reasoning.ReasoningCatalog
import com.oxygen.ai.reasoning.ReasoningLevel
import com.oxygen.ai.reasoning.TaskMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(
    private val dao: SettingsDao,
) {
    fun observe(): Flow<Map<String, String>> = dao.observeAll().map { list -> list.associate { it.key to it.value } }

    suspend fun get(key: String, default: String = ""): String = dao.get(key) ?: default

    suspend fun put(key: String, value: String) = dao.upsert(SettingsEntity(key, value))

    suspend fun flag(key: String, default: Boolean): Boolean = get(key, default.toString()).toBoolean()

    fun reasoningLevel(): ReasoningLevel = ReasoningCatalog.parseLevel(cached[Keys.REASONING])
    fun taskMode(): TaskMode = ReasoningCatalog.parseMode(cached[Keys.TASK_MODE])
    fun memoryEnabled(): Boolean = cached[Keys.MEMORY]?.toBoolean() ?: true
    fun ragEnabled(): Boolean = cached[Keys.RAG]?.toBoolean() ?: true
    fun webSearchEnabled(): Boolean = cached[Keys.WEB]?.toBoolean() ?: false
    fun telegramEnabled(): Boolean = cached[Keys.TELEGRAM]?.toBoolean() ?: false
    fun mcpEnabled(): Boolean = cached[Keys.MCP]?.toBoolean() ?: false
    fun localOnly(): Boolean = cached[Keys.LOCAL_ONLY]?.toBoolean() ?: true
    fun allowExtendedContext(): Boolean = cached[Keys.EXTENDED]?.toBoolean() ?: false
    fun deviceSafeContext(): Int = cached[Keys.SAFE_CTX]?.toIntOrNull() ?: 8192
    fun activeModelId(): String? = cached[Keys.ACTIVE_MODEL]
    fun searxngEndpoint(): String = cached[Keys.SEARXNG].orEmpty()
    fun driveMode(): DriveMode = runCatching { DriveMode.valueOf(cached[Keys.DRIVE_MODE] ?: "LOCAL_ONLY") }
        .getOrDefault(DriveMode.LOCAL_ONLY)
    fun performance(): PerformanceProfile =
        runCatching { PerformanceProfile.valueOf(cached[Keys.PERF] ?: "BALANCED") }.getOrDefault(PerformanceProfile.BALANCED)
    fun dynamicColor(): Boolean = cached[Keys.DYNAMIC_COLOR]?.toBoolean() ?: false
    fun darkMode(): String = cached[Keys.DARK] ?: "system"

    @Volatile
    private var cached: Map<String, String> = emptyMap()

    suspend fun refresh() {
        cached = dao.all().associate { it.key to it.value }
    }

    fun snapshot(): Map<String, String> = cached

    suspend fun seedDefaults() {
        val defaults = mapOf(
            Keys.REASONING to ReasoningLevel.MEDIUM.name,
            Keys.TASK_MODE to TaskMode.CHAT.name,
            Keys.MEMORY to "true",
            Keys.RAG to "true",
            Keys.WEB to "false",
            Keys.TELEGRAM to "false",
            Keys.MCP to "false",
            Keys.LOCAL_ONLY to "true",
            Keys.EXTENDED to "false",
            Keys.SAFE_CTX to "8192",
            Keys.DRIVE_MODE to DriveMode.LOCAL_ONLY.name,
            Keys.PERF to PerformanceProfile.BALANCED.name,
            Keys.DYNAMIC_COLOR to "false",
            Keys.DARK to "system",
            Keys.SEARXNG to "",
        )
        defaults.forEach { (k, v) -> if (dao.get(k) == null) dao.upsert(SettingsEntity(k, v)) }
        refresh()
    }

    object Keys {
        const val REASONING = "reasoning.level"
        const val TASK_MODE = "reasoning.mode"
        const val MEMORY = "privacy.memory"
        const val RAG = "privacy.rag"
        const val WEB = "privacy.web"
        const val TELEGRAM = "privacy.telegram"
        const val MCP = "privacy.mcp"
        const val LOCAL_ONLY = "privacy.local_only"
        const val EXTENDED = "context.extended"
        const val SAFE_CTX = "context.safe"
        const val ACTIVE_MODEL = "model.active"
        const val SEARXNG = "search.searxng"
        const val DRIVE_MODE = "drive.mode"
        const val PERF = "perf.profile"
        const val DYNAMIC_COLOR = "ui.dynamic_color"
        const val DARK = "ui.dark"
        const val DEVICE_ID = "device.id"
    }
}

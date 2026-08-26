package com.oxygen.ai.tools

import com.oxygen.ai.data.db.dao.PermissionDao
import com.oxygen.ai.data.db.entities.ToolPermissionEntity

open class ToolPermissionManager(
    private val dao: PermissionDao,
) {
    suspend fun modeFor(type: String, id: String): PermissionMode {
        val stored = dao.get(type, id)?.mode
        return PermissionMode.entries.firstOrNull { it.name == stored } ?: PermissionMode.ASK
    }

    suspend fun set(type: String, id: String, mode: PermissionMode) {
        dao.upsert(ToolPermissionEntity(type, id, mode.name, System.currentTimeMillis()))
    }

    open suspend fun allows(type: String, id: String, destructive: Boolean, userConfirmed: Boolean): Boolean {
        val mode = modeFor(type, id)
        return when (mode) {
            PermissionMode.DISABLED -> false
            PermissionMode.ALLOWED -> !destructive || userConfirmed
            PermissionMode.ASK -> userConfirmed
        }
    }
}

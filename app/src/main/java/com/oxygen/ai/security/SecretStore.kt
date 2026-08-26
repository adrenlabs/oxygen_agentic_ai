package com.oxygen.ai.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Android Keystore-backed secret store. Tokens never live in Room or logs.
 */
class SecretStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "oxygen_secrets",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun put(key: String, value: String?) {
        prefs.edit().apply {
            if (value.isNullOrBlank()) remove(key) else putString(key, value)
        }.apply()
    }

    fun get(key: String): String? = prefs.getString(key, null)?.takeIf { it.isNotBlank() }

    fun has(key: String): Boolean = !get(key).isNullOrBlank()

    companion object {
        const val TELEGRAM_BOT_TOKEN = "telegram.bot_token"
        const val DRIVE_ACCESS_TOKEN = "drive.access_token"
        const val DRIVE_REFRESH_TOKEN = "drive.refresh_token"
        const val DRIVE_CLIENT_ID = "drive.client_id"
        const val SEARCH_API_KEY = "search.api_key"
        const val MCP_SECRET_PREFIX = "mcp.secret."
    }
}

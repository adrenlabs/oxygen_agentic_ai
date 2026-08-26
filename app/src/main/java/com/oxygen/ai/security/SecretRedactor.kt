package com.oxygen.ai.security

object SecretRedactor {
    private val labeled = Regex(
        "(?i)(api[_-]?key|token|secret|password|authorization)\\s*[:=]\\s*\\S+",
    )
    private val bearer = Regex("(?i)bearer\\s+[A-Za-z0-9._\\-+/=]+")
    private val bot = Regex("(?i)bot\\d{6,}:[A-Za-z0-9_-]{20,}")
    private val googleAccess = Regex("ya29\\.[A-Za-z0-9._\\-]+")
    private val googleRefresh = Regex("1//[A-Za-z0-9._\\-]+")
    private val googleApi = Regex("AIza[0-9A-Za-z\\-_]{20,}")

    fun redact(input: String): String {
        var out = labeled.replace(input) { "${it.groupValues[1]}=***" }
        out = bearer.replace(out, "Bearer ***")
        out = bot.replace(out, "bot***:***")
        out = googleAccess.replace(out, "ya29.***")
        out = googleRefresh.replace(out, "1//***")
        out = googleApi.replace(out, "AIza***")
        return out
    }
}

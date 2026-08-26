package com.oxygen.ai.context

enum class ContextProfile { CTX_8K, CTX_16K, CTX_32K_NATIVE, EXTENDED }

data class PromptMessage(
    val role: String,
    val content: String,
    val priority: Int = 0,
    val tokens: Int = TokenEstimator.estimate(content),
)

data class ContextPack(
    val memories: List<RankedItem> = emptyList(),
    val rag: List<RankedItem> = emptyList(),
    val tools: List<RankedItem> = emptyList(),
    val web: List<RankedItem> = emptyList(),
)

data class RankedItem(
    val id: String,
    val title: String,
    val content: String,
    val score: Float,
    val source: String,
    val page: Int? = null,
    val url: String? = null,
    val tokens: Int = TokenEstimator.estimate(content),
)

data class ContextBudget(
    val profile: ContextProfile,
    val total: Int,
    val system: Int,
    val memories: Int,
    val rag: Int,
    val tools: Int,
    val web: Int,
    val history: Int,
    val outputReserve: Int,
) {
    val usableInput: Int get() = total - outputReserve
}

data class AssembledPrompt(
    val messages: List<PromptMessage>,
    val rendered: String,
    val budget: ContextBudget,
    val usedTokens: Int,
    val dropped: List<String>,
    val citations: List<RankedItem>,
)

object ContextProfiles {
    fun fromLimit(limit: Int, allowExtended: Boolean): ContextProfile = when {
        allowExtended && limit > 32_768 -> ContextProfile.EXTENDED
        limit >= 32_768 -> ContextProfile.CTX_32K_NATIVE
        limit >= 16_384 -> ContextProfile.CTX_16K
        else -> ContextProfile.CTX_8K
    }

    fun allocate(
        total: Int,
        outputReserve: Int,
        wantsMemory: Boolean,
        wantsRag: Boolean,
        wantsTools: Boolean,
        wantsWeb: Boolean,
    ): ContextBudget {
        val usable = (total - outputReserve).coerceAtLeast(512)
        val system = (usable * 0.12).toInt().coerceIn(256, 1200)
        val mem = if (wantsMemory) (usable * 0.10).toInt() else 0
        val rag = if (wantsRag) (usable * 0.18).toInt() else 0
        val tools = if (wantsTools) (usable * 0.12).toInt() else 0
        val web = if (wantsWeb) (usable * 0.10).toInt() else 0
        val usedFixed = system + mem + rag + tools + web
        val history = (usable - usedFixed).coerceAtLeast(256)
        val profile = fromLimit(total, total > 32_768)
        return ContextBudget(profile, total, system, mem, rag, tools, web, history, outputReserve)
    }
}

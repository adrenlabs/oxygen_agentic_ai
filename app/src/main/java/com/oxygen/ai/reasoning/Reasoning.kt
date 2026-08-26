package com.oxygen.ai.reasoning

import com.oxygen.ai.inference.GenerationConfig

enum class ReasoningLevel { EXTRA_LOW, LOW, MEDIUM, HIGH, MAX }

enum class TaskMode { CHAT, CODING, RESEARCH, MATH, COMPLEX, AGENT }

enum class TaskComplexity { TRIVIAL, SIMPLE, MODERATE, HARD, EXTREME }

data class ReasoningBudget(
    val thinking: Boolean,
    val contextTokens: Int,
    val outputTokens: Int,
    val memoryCount: Int,
    val ragDepth: Int,
    val webDepth: Int,
    val maxToolCalls: Int,
    val maxPlanningSteps: Int,
    val maxIterations: Int,
    val maxRetries: Int,
    val retrievalMinScore: Float,
)

data class ReasoningProfile(
    val level: ReasoningLevel,
    val taskMode: TaskMode,
    val budget: ReasoningBudget,
    val generation: GenerationConfig,
    val label: String,
)

object ReasoningCatalog {

    fun profile(
        level: ReasoningLevel,
        mode: TaskMode = TaskMode.CHAT,
        nativeContext: Int = 32_768,
        deviceSafeContext: Int = 8_192,
    ): ReasoningProfile {
        val ctxCap = minOf(nativeContext, deviceSafeContext)
        val budget = when (level) {
            ReasoningLevel.EXTRA_LOW -> ReasoningBudget(
                thinking = false, contextTokens = minOf(2048, ctxCap), outputTokens = 192,
                memoryCount = 1, ragDepth = 1, webDepth = 0, maxToolCalls = 0,
                maxPlanningSteps = 1, maxIterations = 1, maxRetries = 0, retrievalMinScore = 0.42f,
            )
            ReasoningLevel.LOW -> ReasoningBudget(
                thinking = false, contextTokens = minOf(4096, ctxCap), outputTokens = 384,
                memoryCount = 3, ragDepth = 3, webDepth = 2, maxToolCalls = 1,
                maxPlanningSteps = 2, maxIterations = 2, maxRetries = 1, retrievalMinScore = 0.32f,
            )
            ReasoningLevel.MEDIUM -> ReasoningBudget(
                thinking = mode != TaskMode.CHAT, contextTokens = minOf(8192, ctxCap), outputTokens = 640,
                memoryCount = 6, ragDepth = 6, webDepth = 5, maxToolCalls = 4,
                maxPlanningSteps = 4, maxIterations = 4, maxRetries = 1, retrievalMinScore = 0.24f,
            )
            ReasoningLevel.HIGH -> ReasoningBudget(
                thinking = true, contextTokens = minOf(16384, ctxCap), outputTokens = 1024,
                memoryCount = 10, ragDepth = 10, webDepth = 8, maxToolCalls = 8,
                maxPlanningSteps = 6, maxIterations = 6, maxRetries = 2, retrievalMinScore = 0.18f,
            )
            ReasoningLevel.MAX -> ReasoningBudget(
                thinking = true, contextTokens = ctxCap, outputTokens = 1536,
                memoryCount = 16, ragDepth = 16, webDepth = 12, maxToolCalls = 12,
                maxPlanningSteps = 8, maxIterations = 8, maxRetries = 2, retrievalMinScore = 0.14f,
            )
        }
        val modeBudget = applyMode(budget, mode, ctxCap)
        val gen = GenerationConfig(
            maxTokens = modeBudget.outputTokens,
            temperature = when (mode) {
                TaskMode.CODING, TaskMode.MATH -> 0.2f
                TaskMode.RESEARCH, TaskMode.COMPLEX, TaskMode.AGENT -> 0.5f
                TaskMode.CHAT -> if (level <= ReasoningLevel.LOW) 0.8f else 0.7f
            },
            topP = if (mode == TaskMode.CODING || mode == TaskMode.MATH) 0.85f else 0.9f,
            topK = 40,
            minP = 0.05f,
            stopSequences = listOf("<|im_end|>", "<|eot_id|>"),
        )
        return ReasoningProfile(level, mode, modeBudget, gen, label = "${mode.name.lowercase()} / ${levelLabel(level)}")
    }

    fun levelLabel(level: ReasoningLevel): String = when (level) {
        ReasoningLevel.EXTRA_LOW -> "Extra Low"
        ReasoningLevel.LOW -> "Low"
        ReasoningLevel.MEDIUM -> "Medium"
        ReasoningLevel.HIGH -> "High"
        ReasoningLevel.MAX -> "Max"
    }

    fun parseLevel(raw: String?): ReasoningLevel =
        ReasoningLevel.entries.firstOrNull { it.name.equals(raw, true) || levelLabel(it).equals(raw, true) }
            ?: ReasoningLevel.MEDIUM

    fun parseMode(raw: String?): TaskMode =
        TaskMode.entries.firstOrNull { it.name.equals(raw, true) } ?: TaskMode.CHAT

    private fun applyMode(base: ReasoningBudget, mode: TaskMode, ctxCap: Int): ReasoningBudget {
        return when (mode) {
            TaskMode.CHAT -> base.copy(maxToolCalls = minOf(base.maxToolCalls, 2))
            TaskMode.CODING -> base.copy(
                thinking = true,
                ragDepth = base.ragDepth + 2,
                outputTokens = minOf(base.outputTokens + 256, 2048),
            )
            TaskMode.RESEARCH -> base.copy(
                webDepth = base.webDepth + 3,
                ragDepth = base.ragDepth + 2,
                contextTokens = minOf(ctxCap, base.contextTokens + 2048),
            )
            TaskMode.MATH -> base.copy(thinking = true, maxToolCalls = maxOf(base.maxToolCalls, 2), webDepth = 0)
            TaskMode.COMPLEX -> base.copy(
                thinking = true,
                maxIterations = base.maxIterations + 1,
                maxPlanningSteps = base.maxPlanningSteps + 1,
            )
            TaskMode.AGENT -> base.copy(
                thinking = true,
                maxToolCalls = maxOf(base.maxToolCalls, 6),
                maxIterations = maxOf(base.maxIterations, 6),
            )
        }
    }
}

class ReasoningController {
    fun resolve(
        requested: ReasoningLevel,
        complexity: TaskComplexity,
        mode: TaskMode,
        nativeContext: Int,
        deviceSafeContext: Int,
    ): ReasoningProfile {
        val promoted = when {
            complexity == TaskComplexity.EXTREME && requested < ReasoningLevel.HIGH -> ReasoningLevel.HIGH
            complexity == TaskComplexity.HARD && requested < ReasoningLevel.MEDIUM -> ReasoningLevel.MEDIUM
            complexity == TaskComplexity.TRIVIAL && requested > ReasoningLevel.LOW -> requested
            else -> requested
        }
        return ReasoningCatalog.profile(promoted, mode, nativeContext, deviceSafeContext)
    }
}

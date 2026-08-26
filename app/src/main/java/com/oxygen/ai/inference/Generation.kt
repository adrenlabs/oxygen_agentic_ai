package com.oxygen.ai.inference

data class GenerationConfig(
    val maxTokens: Int = 512,
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.9f,
    val minP: Float = 0.05f,
    val repeatPenalty: Float = 1.08f,
    val seed: Int = -1,
    val stopSequences: List<String> = emptyList(),
    val systemPrompt: String? = null,
)

sealed class GenerationEvent {
    data class Token(val text: String) : GenerationEvent()
    data class Metrics(
        val promptTokens: Int,
        val generatedTokens: Int,
        val tokensPerSecond: Double,
    ) : GenerationEvent()
    data class Error(val message: String) : GenerationEvent()
    data class Completed(val cancelled: Boolean) : GenerationEvent()
}

data class GenerationResult(
    val text: String,
    val promptTokens: Int,
    val generatedTokens: Int,
    val tokensPerSecond: Double,
    val cancelled: Boolean,
    val error: String? = null,
)

data class RuntimeStatus(
    val loaded: Boolean,
    val available: Boolean,
    val handle: String?,
    val modelPath: String?,
    val contextSize: Int,
    val threads: Int,
    val backend: String,
    val detail: String,
)

enum class RuntimeKind { LLAMA_CPP, NONE }

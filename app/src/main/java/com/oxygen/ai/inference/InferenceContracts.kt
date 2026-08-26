package com.oxygen.ai.inference

import kotlinx.coroutines.flow.Flow

interface InferenceEngine {
    val runtime: ModelRuntime
    suspend fun generate(
        session: ModelSession,
        prompt: String,
        config: GenerationConfig,
    ): Flow<GenerationEvent>
}

interface ModelRuntime {
    fun available(): Boolean
    fun status(): RuntimeStatus
    suspend fun load(path: String, sessionConfig: SessionConfig): ModelSession
    suspend fun unload(session: ModelSession)
    fun readMetadata(path: String): GgufMetadata
}

data class SessionConfig(
    val contextSize: Int,
    val threads: Int,
    val batchSize: Int,
    val mmap: Boolean = true,
    val mlock: Boolean = false,
    val gpuLayers: Int = 0,
    val yarnOrigCtx: Int = 0,
    val yarnExtFactor: Float = -1f,
    val yarnAttnFactor: Float = 1f,
    val ropeFreqBase: Float = 0f,
)

class ModelSession internal constructor(
    val handle: String,
    val path: String,
    val config: SessionConfig,
) {
    @Volatile
    var cancelId: Long = 0L
        internal set
}

data class GgufMetadata(
    val ok: Boolean,
    val error: String = "",
    val version: Int = 0,
    val tensorCount: Long = 0,
    val kvCount: Long = 0,
    val architecture: String = "",
    val name: String = "",
    val contextLength: Int = 0,
    val embeddingLength: Int = 0,
    val blockCount: Int = 0,
    val headCount: Int = 0,
    val quantization: String = "",
    val chatTemplate: String = "",
)

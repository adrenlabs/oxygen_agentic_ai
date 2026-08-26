package com.oxygen.ai.inference.nativebridge

import com.oxygen.ai.core.logging.OxygenLog

object LlamaJni {
    @Volatile
    var loaded: Boolean = false
        private set

    @Volatile
    var loadError: String? = null
        private set

    init {
        try {
            System.loadLibrary("oxygen_inference")
            loaded = true
            OxygenLog.i("jni", "liboxygen_inference loaded")
        } catch (t: Throwable) {
            loaded = false
            loadError = t.message
            OxygenLog.e("jni", "Failed to load native library", t)
        }
    }

    interface Listener {
        fun onToken(token: String): Boolean
        fun onMetrics(promptTokens: Int, generatedTokens: Int, tokensPerSecond: Double)
        fun onError(message: String)
        fun onDone(cancelled: Boolean)
    }

    external fun nativeAvailable(): Boolean
    external fun nativeReadGguf(path: String): String
    external fun nativeLoad(
        path: String,
        nCtx: Int,
        nThreads: Int,
        nBatch: Int,
        nGpuLayers: Int,
        useMmap: Boolean,
        useMlock: Boolean,
        ropeFreqBase: Float,
        yarnExtFactor: Float,
        yarnAttnFactor: Float,
        yarnOrigCtx: Int,
    ): String

    external fun nativeUnload(handle: String): Boolean
    external fun nativeCreateCancel(): Long
    external fun nativeCancel(id: Long)
    external fun nativeReleaseCancel(id: Long)
    external fun nativeGenerate(
        handle: String,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
        minP: Float,
        repeatPenalty: Float,
        seed: Int,
        stop: Array<String>,
        cancelId: Long,
        listener: Listener,
    )

    external fun nativeStatus(handle: String): String
}

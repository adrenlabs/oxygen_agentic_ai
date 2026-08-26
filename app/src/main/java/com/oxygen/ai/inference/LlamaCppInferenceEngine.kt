package com.oxygen.ai.inference

import com.oxygen.ai.core.error.OxygenError
import com.oxygen.ai.core.logging.OxygenLog
import com.oxygen.ai.inference.nativebridge.LlamaJni
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class LlamaCppRuntime : ModelRuntime {
    private val sessionRef = AtomicReference<ModelSession?>(null)
    private val lifecycleLock = ReentrantReadWriteLock()

    override fun available(): Boolean = LlamaJni.loaded && runCatching { LlamaJni.nativeAvailable() }.getOrDefault(false)

    override fun status(): RuntimeStatus {
        val session = sessionRef.get()
        val native = if (LlamaJni.loaded && session != null) {
            runCatching { LlamaJni.nativeStatus(session.handle) }.getOrDefault("unknown")
        } else {
            LlamaJni.loadError ?: if (LlamaJni.loaded) "idle" else "native-not-loaded"
        }
        return RuntimeStatus(
            loaded = session != null,
            available = available(),
            handle = session?.handle,
            modelPath = session?.path,
            contextSize = session?.config?.contextSize ?: 0,
            threads = session?.config?.threads ?: 0,
            backend = "llama.cpp",
            detail = native,
        )
    }

    override suspend fun load(path: String, sessionConfig: SessionConfig): ModelSession =
        withContext(Dispatchers.IO) {
            lifecycleLock.write {
                if (!LlamaJni.loaded) {
                    throw OxygenError.ModelLoadFailed(path, LlamaJni.loadError ?: "native library missing")
                }
                val previous = sessionRef.getAndSet(null)
                if (previous != null) {
                    runCatching { LlamaJni.nativeUnload(previous.handle) }
                    previous.cancelId = 0L
                }
                require(sessionConfig.contextSize in 512..131_072) { "contextSize out of safe range" }
                require(sessionConfig.threads in 1..256) { "threads out of safe range" }
                require(sessionConfig.batchSize in 32..8_192) { "batchSize out of safe range" }
                val handle = LlamaJni.nativeLoad(
                    path,
                    sessionConfig.contextSize,
                    sessionConfig.threads,
                    sessionConfig.batchSize,
                    sessionConfig.gpuLayers.coerceAtLeast(0),
                    sessionConfig.mmap,
                    sessionConfig.mlock,
                    sessionConfig.ropeFreqBase,
                    sessionConfig.yarnExtFactor,
                    sessionConfig.yarnAttnFactor,
                    sessionConfig.yarnOrigCtx,
                )
                if (handle.startsWith("ERR:")) {
                    throw OxygenError.ModelLoadFailed(path, handle.removePrefix("ERR:"))
                }
                val session = ModelSession(handle, path, sessionConfig)
                sessionRef.set(session)
                OxygenLog.i("llm", "Loaded model handle=$handle ctx=${sessionConfig.contextSize}")
                session
            }
        }

    override suspend fun unload(session: ModelSession) = withContext(Dispatchers.IO) {
        lifecycleLock.write {
            runCatching {
                if (LlamaJni.loaded) LlamaJni.nativeUnload(session.handle)
            }.onFailure {
                OxygenLog.e("llm", "Native unload failed for ${session.handle}", it)
            }
            if (sessionRef.get()?.handle == session.handle) sessionRef.set(null)
            session.cancelId = 0L
            OxygenLog.i("llm", "Unloaded ${session.handle}")
        }
    }

    override fun readMetadata(path: String): GgufMetadata {
        if (LlamaJni.loaded) {
            val json = runCatching { LlamaJni.nativeReadGguf(path) }.getOrNull()
            if (json != null) return GgufMetadataReader.fromNativeJson(json)
        }
        return GgufMetadataReader.read(path)
    }

    fun currentSession(): ModelSession? = sessionRef.get()

    /**
     * Shared with [LlamaCppInferenceEngine] so generate cannot race unload.
     * The lock lives on the runtime because the runtime owns native session lifetime.
     */
    fun <T> withGenerationLock(block: () -> T): T = lifecycleLock.read { block() }
}

class LlamaCppInferenceEngine(
    override val runtime: LlamaCppRuntime = LlamaCppRuntime(),
) : InferenceEngine {

    override suspend fun generate(
        session: ModelSession,
        prompt: String,
        config: GenerationConfig,
    ): Flow<GenerationEvent> = callbackFlow {
        if (!LlamaJni.loaded) {
            trySend(GenerationEvent.Error(LlamaJni.loadError ?: "native library missing"))
            close(OxygenError.InferenceFailed("native library missing"))
            return@callbackFlow
        }
        val cancelId = LlamaJni.nativeCreateCancel()
        session.cancelId = cancelId
        val listener = object : LlamaJni.Listener {
            override fun onToken(token: String): Boolean {
                trySend(GenerationEvent.Token(token))
                return !isClosedForSend
            }

            override fun onMetrics(promptTokens: Int, generatedTokens: Int, tokensPerSecond: Double) {
                trySend(GenerationEvent.Metrics(promptTokens, generatedTokens, tokensPerSecond))
            }

            override fun onError(message: String) {
                trySend(GenerationEvent.Error(message))
            }

            override fun onDone(cancelled: Boolean) {
                trySend(GenerationEvent.Completed(cancelled))
                close()
            }
        }
        try {
            withContext(Dispatchers.IO) {
                runtime.withGenerationLock {
                    LlamaJni.nativeGenerate(
                        session.handle,
                        prompt,
                        config.maxTokens,
                        config.temperature,
                        config.topK,
                        config.topP,
                        config.minP,
                        config.repeatPenalty,
                        config.seed,
                        config.stopSequences.toTypedArray(),
                        cancelId,
                        listener,
                    )
                }
            }
        } finally {
            if (!isClosedForSend) {
                trySend(GenerationEvent.Completed(false))
                close()
            }
            if (LlamaJni.loaded) LlamaJni.nativeReleaseCancel(cancelId)
            if (session.cancelId == cancelId) session.cancelId = 0L
        }
        awaitClose {
            if (LlamaJni.loaded) LlamaJni.nativeCancel(cancelId)
        }
    }

    fun cancel(session: ModelSession) {
        if (session.cancelId != 0L && LlamaJni.loaded) {
            LlamaJni.nativeCancel(session.cancelId)
        }
    }
}

package com.oxygen.ai.rag

interface EmbeddingProvider {
    val id: String
    val dimensions: Int
    fun embed(text: String): FloatArray
    fun embedAll(texts: List<String>): List<FloatArray> = texts.map { embed(it) }
}

/**
 * Mobile-suitable hashing n-gram embedding (feature hashing / signed hashing).
 * Always available offline. Independently replaceable by a GGUF embedding model.
 * This is not the chat LLM.
 */
class NgramHashEmbeddingProvider(
    override val dimensions: Int = 384,
) : EmbeddingProvider {
    override val id: String = "ngram-hash-v1"

    override fun embed(text: String): FloatArray {
        val vec = FloatArray(dimensions)
        val norm = text.lowercase()
        addHashed(vec, "u:" + norm.take(80), 1.1f)
        val words = norm.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 }
        for (w in words) addHashed(vec, "w:$w", 1f)
        if (words.size >= 2) {
            for (i in 0 until words.size - 1) addHashed(vec, "b:${words[i]}_${words[i + 1]}", 0.7f)
        }
        val compact = norm.replace(" ", "")
        if (compact.length >= 3) {
            for (i in 0..compact.length - 3) addHashed(vec, "c:" + compact.substring(i, i + 3), 0.35f)
        }
        return VectorMath.l2Normalize(vec)
    }

    private fun addHashed(vec: FloatArray, token: String, weight: Float) {
        val h = token.hashCode()
        val idx = (h ushr 1) % dimensions
        val sign = if (h and 1 == 0) 1f else -1f
        vec[idx] += sign * weight
    }
}

class CompositeEmbeddingProvider(
    private val primary: EmbeddingProvider,
    private val fallback: EmbeddingProvider = NgramHashEmbeddingProvider(),
) : EmbeddingProvider {
    override val id: String get() = primary.id
    override val dimensions: Int get() = primary.dimensions

    override fun embed(text: String): FloatArray =
        runCatching { primary.embed(text) }.getOrElse { fallback.embed(text) }
}

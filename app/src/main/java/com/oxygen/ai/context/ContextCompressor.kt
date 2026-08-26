package com.oxygen.ai.context

object ContextCompressor {
    fun compress(text: String, maxTokens: Int): String {
        if (TokenEstimator.estimate(text) <= maxTokens) return text
        val sentences = text.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        if (sentences.isEmpty()) return text.take(maxTokens * 4)
        val picked = ArrayList<String>()
        var used = 0
        // Keep first and last sentences, then fill from the middle by length.
        val ordered = buildList {
            if (sentences.isNotEmpty()) add(0)
            if (sentences.size > 1) add(sentences.lastIndex)
            for (i in 1 until sentences.lastIndex) add(i)
        }
        val taken = BooleanArray(sentences.size)
        for (idx in ordered) {
            val s = sentences[idx]
            val t = TokenEstimator.estimate(s)
            if (used + t > maxTokens) continue
            taken[idx] = true
            used += t
        }
        for (i in sentences.indices) if (taken[i]) picked.add(sentences[i])
        val out = picked.joinToString(" ")
        return out.ifBlank { text.take(maxTokens * 4) }
    }

    fun summarizeHistory(messages: List<PromptMessage>, maxTokens: Int): PromptMessage {
        val body = messages.joinToString("\n") { "${it.role}: ${it.content}" }
        val compressed = compress(body, maxTokens)
        return PromptMessage("system", "Earlier conversation summary:\n$compressed", priority = 2)
    }
}

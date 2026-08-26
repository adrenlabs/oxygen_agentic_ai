package com.oxygen.ai.memory

import com.oxygen.ai.context.RelevanceRanker

object MemoryMerger {
    fun shouldMerge(a: String, b: String): Boolean {
        val score = RelevanceRanker.bm25ish(a, b)
        val overlap = tokenOverlap(a, b)
        return score > 0.55f || overlap > 0.6f
    }

    fun mergeContent(existing: String, incoming: String): String {
        if (incoming.contains(existing, ignoreCase = true)) return incoming.trim()
        if (existing.contains(incoming, ignoreCase = true)) return existing.trim()
        return (existing.trim().trimEnd('.') + ". " + incoming.trim()).trim()
    }

    fun tokenOverlap(a: String, b: String): Float {
        val ta = RelevanceRanker.tokenize(a).toSet()
        val tb = RelevanceRanker.tokenize(b).toSet()
        if (ta.isEmpty() || tb.isEmpty()) return 0f
        val inter = ta.intersect(tb).size.toFloat()
        val union = ta.union(tb).size.toFloat()
        return if (union == 0f) 0f else inter / union
    }
}

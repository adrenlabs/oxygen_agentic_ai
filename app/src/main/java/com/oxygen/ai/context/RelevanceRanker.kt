package com.oxygen.ai.context

import kotlin.math.ln
import kotlin.math.max

object RelevanceRanker {
    fun score(
        query: String,
        document: String,
        recencyWeight: Float = 0f,
        importance: Float = 0f,
    ): Float {
        val lexical = bm25ish(query, document)
        return (0.72f * lexical) + (0.16f * recencyWeight.coerceIn(0f, 1f)) +
            (0.12f * importance.coerceIn(0f, 1f))
    }

    fun bm25ish(query: String, document: String): Float {
        val qTerms = tokenize(query)
        val dTerms = tokenize(document)
        if (qTerms.isEmpty() || dTerms.isEmpty()) return 0f
        val tf = HashMap<String, Int>()
        for (t in dTerms) tf[t] = (tf[t] ?: 0) + 1
        val avg = 400f
        val k1 = 1.2f
        val b = 0.75f
        val dl = dTerms.size.toFloat()
        var score = 0f
        for (q in qTerms.distinct()) {
            val f = (tf[q] ?: 0).toFloat()
            if (f == 0f) continue
            val idf = ln(1.0 + (1.0 / (1.0 + f))).toFloat()
            val denom = f + k1 * (1 - b + b * (dl / avg))
            score += idf * ((f * (k1 + 1)) / max(0.001f, denom))
        }
        return (score / (1f + qTerms.size / 6f)).coerceIn(0f, 1.5f)
    }

    fun tokenize(text: String): List<String> =
        text.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 2 }

    fun dedupe(items: List<RankedItem>): List<RankedItem> {
        val seen = HashSet<String>()
        return items.filter { item ->
            val key = item.content.take(180).lowercase().trim()
            seen.add(key)
        }
    }
}

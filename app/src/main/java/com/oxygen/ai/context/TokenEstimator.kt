package com.oxygen.ai.context

/**
 * Fast heuristic estimator. Not a claim of exact model tokenization.
 * Latin ~4 chars/token, CJK closer to 1.5 chars/token, code a bit denser.
 */
object TokenEstimator {
    fun estimate(text: String): Int {
        if (text.isEmpty()) return 0
        var cjk = 0
        var other = 0
        for (ch in text) {
            if (ch.code in 0x2E80..0x9FFF || ch.code in 0xF900..0xFAFF || ch.code in 0xFF00..0xFFEF) {
                cjk++
            } else {
                other++
            }
        }
        val latinTokens = (other + 3) / 4
        val cjkTokens = ((cjk * 2) + 2) / 3
        return (latinTokens + cjkTokens).coerceAtLeast(1)
    }
}

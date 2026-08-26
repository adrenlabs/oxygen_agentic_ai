package com.oxygen.ai.rag

import com.oxygen.ai.context.TokenEstimator
import java.security.MessageDigest

data class TextChunk(
    val index: Int,
    val text: String,
    val page: Int?,
    val tokenEstimate: Int,
    val checksum: String,
)

class Chunker(
    private val targetTokens: Int = 280,
    private val overlapTokens: Int = 40,
) {
    fun chunk(text: String, pageOfOffset: ((Int) -> Int?)? = null): List<TextChunk> {
        val cleaned = text.replace("\r\n", "\n").replace(Regex("[ \t]+"), " ").trim()
        if (cleaned.isEmpty()) return emptyList()
        val paragraphs = cleaned.split(Regex("\n{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
        val pieces = ArrayList<String>()
        for (p in paragraphs) {
            if (TokenEstimator.estimate(p) <= targetTokens) {
                pieces.add(p)
            } else {
                pieces.addAll(splitWindow(p))
            }
        }
        val out = ArrayList<TextChunk>()
        var idx = 0
        var cursor = 0
        for (piece in pieces) {
            val page = pageOfOffset?.invoke(cleaned.indexOf(piece).coerceAtLeast(cursor))
            out.add(
                TextChunk(
                    index = idx++,
                    text = piece,
                    page = page,
                    tokenEstimate = TokenEstimator.estimate(piece),
                    checksum = sha256(piece),
                ),
            )
            cursor += piece.length
        }
        return out
    }

    private fun splitWindow(text: String): List<String> {
        val words = text.split(Regex("\\s+"))
        val out = ArrayList<String>()
        var start = 0
        while (start < words.size) {
            val buf = StringBuilder()
            var i = start
            while (i < words.size) {
                val next = if (buf.isEmpty()) words[i] else buf.toString() + " " + words[i]
                if (TokenEstimator.estimate(next) > targetTokens && buf.isNotEmpty()) break
                if (buf.isNotEmpty()) buf.append(' ')
                buf.append(words[i])
                i++
            }
            out.add(buf.toString())
            if (i >= words.size) break
            var back = 0
            var overlap = 0
            while (i - 1 - back >= start && overlap < overlapTokens) {
                overlap += TokenEstimator.estimate(words[i - 1 - back])
                back++
            }
            start = (i - back).coerceAtLeast(start + 1)
        }
        return out
    }

    private fun sha256(text: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }
}

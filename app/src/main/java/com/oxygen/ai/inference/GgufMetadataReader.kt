package com.oxygen.ai.inference

import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Pure-Kotlin GGUF header reader. Used when the native library is not yet
 * loaded and by unit tests. Does not execute model weights.
 */
object GgufMetadataReader {

    fun read(path: String): GgufMetadata {
        val file = File(path)
        if (!file.exists()) return GgufMetadata(ok = false, error = "File not found")
        return runCatching { readFile(file) }.getOrElse {
            GgufMetadata(ok = false, error = it.message ?: "Read failed")
        }
    }

    fun fromNativeJson(json: String): GgufMetadata {
        val o = JSONObject(json)
        return GgufMetadata(
            ok = o.optBoolean("ok"),
            error = o.optString("error"),
            version = o.optInt("version"),
            tensorCount = o.optLong("tensorCount"),
            kvCount = o.optLong("kvCount"),
            architecture = o.optString("architecture"),
            name = o.optString("name"),
            contextLength = o.optInt("contextLength"),
            embeddingLength = o.optInt("embeddingLength"),
            blockCount = o.optInt("blockCount"),
            headCount = o.optInt("headCount"),
            quantization = o.optString("quantization"),
            chatTemplate = o.optString("chatTemplate"),
        )
    }

    private fun readFile(file: File): GgufMetadata {
        RandomAccessFile(file, "r").use { raf ->
            val magic = ByteArray(4)
            raf.readFully(magic)
            if (String(magic, StandardCharsets.US_ASCII) != "GGUF") {
                return GgufMetadata(ok = false, error = "Not a GGUF file")
            }
            val version = raf.readU32()
            val tensorCount = raf.readU64()
            val kvCount = raf.readU64()
            if (kvCount > 4096) return GgufMetadata(ok = false, error = "Unreasonable metadata count")
            var architecture = ""
            var name = ""
            var contextLength = 0
            var embeddingLength = 0
            var blockCount = 0
            var headCount = 0
            var quantization = ""
            var chatTemplate = ""
            repeat(kvCount.toInt()) {
                val key = raf.readGgufString()
                val type = raf.readU32()
                val value = raf.readValue(type)
                when {
                    key == "general.architecture" -> architecture = value
                    key == "general.name" -> name = value
                    key.endsWith(".context_length") -> contextLength = value.toIntOrNull() ?: contextLength
                    key.endsWith(".embedding_length") -> embeddingLength = value.toIntOrNull() ?: embeddingLength
                    key.endsWith(".block_count") -> blockCount = value.toIntOrNull() ?: blockCount
                    key.endsWith(".attention.head_count") -> headCount = value.toIntOrNull() ?: headCount
                    key.contains("file_type") -> quantization = value
                    key == "tokenizer.chat_template" -> chatTemplate = value
                }
            }
            return GgufMetadata(
                ok = true,
                version = version,
                tensorCount = tensorCount,
                kvCount = kvCount,
                architecture = architecture,
                name = name,
                contextLength = contextLength,
                embeddingLength = embeddingLength,
                blockCount = blockCount,
                headCount = headCount,
                quantization = quantization,
                chatTemplate = chatTemplate,
            )
        }
    }

    private fun RandomAccessFile.readU32(): Int {
        val b = ByteArray(4)
        readFully(b)
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun RandomAccessFile.readU64(): Long {
        val b = ByteArray(8)
        readFully(b)
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).long
    }

    private fun RandomAccessFile.readGgufString(): String {
        val n = readU64()
        require(n in 0..16_000_000) { "String too large" }
        val bytes = ByteArray(n.toInt())
        if (n > 0) readFully(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun RandomAccessFile.readValue(type: Int): String {
        return when (type) {
            0 -> read().toString()
            1 -> read().toByte().toString()
            2 -> {
                val b = ByteArray(2); readFully(b)
                ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).short.toUShort().toString()
            }
            3 -> {
                val b = ByteArray(2); readFully(b)
                ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).short.toString()
            }
            4 -> readU32().toUInt().toString()
            5 -> readU32().toString()
            6 -> {
                val b = ByteArray(4); readFully(b)
                ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).float.toString()
            }
            7 -> if (read() != 0) "true" else "false"
            8 -> readGgufString()
            9 -> {
                val elem = readU32()
                val n = readU64()
                repeat(n.toInt().coerceAtMost(2_000_000)) { readValue(elem) }
                "[array:$n]"
            }
            10 -> readU64().toULong().toString()
            11 -> readU64().toString()
            12 -> {
                val b = ByteArray(8); readFully(b)
                ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).double.toString()
            }
            else -> error("Unknown GGUF type $type")
        }
    }
}

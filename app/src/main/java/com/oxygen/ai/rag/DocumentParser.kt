package com.oxygen.ai.rag

import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

data class ParsedDocument(
    val text: String,
    val pageCount: Int,
    val pageStarts: List<Int>,
    val metadata: Map<String, String>,
)

interface DocumentParser {
    fun supports(mimeType: String, fileName: String): Boolean
    fun parse(stream: InputStream, fileName: String, mimeType: String): ParsedDocument
}

class PlainTextParser : DocumentParser {
    override fun supports(mimeType: String, fileName: String): Boolean {
        val n = fileName.lowercase()
        return mimeType.startsWith("text/") || n.endsWith(".txt") || n.endsWith(".md") ||
            n.endsWith(".json") || n.endsWith(".csv") || n.endsWith(".kt") ||
            n.endsWith(".java") || n.endsWith(".py") || n.endsWith(".js") || n.endsWith(".ts")
    }

    override fun parse(stream: InputStream, fileName: String, mimeType: String): ParsedDocument {
        val text = stream.bufferedReader().use(BufferedReader::readText)
        return ParsedDocument(text, 1, listOf(0), mapOf("name" to fileName))
    }
}

class DocxParser : DocumentParser {
    override fun supports(mimeType: String, fileName: String): Boolean {
        return fileName.lowercase().endsWith(".docx") ||
            mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    }

    override fun parse(stream: InputStream, fileName: String, mimeType: String): ParsedDocument {
        val xml = StringBuilder()
        ZipInputStream(stream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    xml.append(zip.bufferedReader().readText())
                    break
                }
                entry = zip.nextEntry
            }
        }
        val text = xml.toString()
            .replace(Regex("</w:p>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .trim()
        return ParsedDocument(text, 1, listOf(0), mapOf("name" to fileName))
    }
}

class ParserRegistry(private val parsers: List<DocumentParser>) {
    fun find(mimeType: String, fileName: String): DocumentParser? =
        parsers.firstOrNull { it.supports(mimeType, fileName) }
}

fun defaultParsers(): List<DocumentParser> = listOf(PlainTextParser(), DocxParser())

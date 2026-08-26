package com.oxygen.ai.tools

import com.oxygen.ai.drive.OxygenSyncLayer
import com.oxygen.ai.memory.MemoryRepository
import com.oxygen.ai.rag.RagPipeline
import com.oxygen.ai.rag.RetrievalQuery
import com.oxygen.ai.search.SearchProvider
import com.oxygen.ai.search.SearchRequest
import com.oxygen.ai.telegram.TelegramGateway
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.pow

class MemorySearchTool(private val memory: MemoryRepository) : Tool {
    override val spec = ToolSpec(
        "memory_search",
        "Search long-term local memories.",
        "builtin",
        schema("query" to "search text", "limit" to "max results"),
    )

    override suspend fun invoke(call: ToolCall): ToolResult {
        val q = stringArg(call.arguments, "query")
        val limit = stringArg(call.arguments, "limit", "6").toIntOrNull() ?: 6
        val hits = memory.retrieve(q, limit, 0.15f)
        val body = if (hits.isEmpty()) "No memories matched." else hits.joinToString("\n") { "- ${it.content}" }
        return ToolResult(call.id, spec.name, true, body)
    }
}

class MemorySaveTool(private val memory: MemoryRepository) : Tool {
    override val spec = ToolSpec(
        "memory_save",
        "Store a durable personal memory. Use only for facts the user asked to remember.",
        "builtin",
        schema("content" to "memory text", "category" to "PERSONAL|PREFERENCE|PROJECT|TASK|FACT|WORKFLOW|REFERENCE"),
        destructive = false,
    )

    override suspend fun invoke(call: ToolCall): ToolResult {
        val content = stringArg(call.arguments, "content")
        if (content.isBlank()) return ToolResult(call.id, spec.name, false, "", error = "empty content")
        val now = System.currentTimeMillis()
        val cat = runCatching {
            com.oxygen.ai.memory.MemoryCategory.valueOf(stringArg(call.arguments, "category", "FACT"))
        }.getOrDefault(com.oxygen.ai.memory.MemoryCategory.FACT)
        memory.save(
            com.oxygen.ai.memory.MemoryRecord(
                id = java.util.UUID.randomUUID().toString(),
                content = content, category = cat, importance = 0.6f, confidence = 0.8f,
                source = "tool", createdAt = now, updatedAt = now, conversationId = null,
            ),
        )
        return ToolResult(call.id, spec.name, true, "Saved memory.")
    }
}

class RagSearchTool(private val rag: RagPipeline) : Tool {
    override val spec = ToolSpec(
        "rag_search",
        "Search indexed local documents and PDFs.",
        "builtin",
        schema("query" to "search text", "limit" to "max chunks"),
    )

    override suspend fun invoke(call: ToolCall): ToolResult {
        val hits = rag.retrieve(
            RetrievalQuery(stringArg(call.arguments, "query"), stringArg(call.arguments, "limit", "6").toIntOrNull() ?: 6, 0.12f),
        )
        val body = if (hits.isEmpty()) "No document matches."
        else hits.joinToString("\n") { "- ${it.title}${it.page?.let { p -> " p.$p" } ?: ""}: ${it.content.take(400)}" }
        return ToolResult(call.id, spec.name, true, body)
    }
}

class WebSearchTool(private val provider: SearchProvider) : Tool {
    override val spec = ToolSpec(
        "web_search",
        "Search the public web through the configured search provider.",
        "builtin",
        schema("query" to "search query", "count" to "result count"),
    )

    override suspend fun invoke(call: ToolCall): ToolResult {
        val res = provider.search(
            SearchRequest(stringArg(call.arguments, "query"), stringArg(call.arguments, "count", "5").toIntOrNull() ?: 5),
        )
        val body = res.results.joinToString("\n") { "- ${it.title} (${it.url}): ${it.snippet}" }
            .ifBlank { "No results." }
        return ToolResult(call.id, spec.name, res.ok, body, error = res.error)
    }
}

class CalculatorTool : Tool {
    override val spec = ToolSpec(
        "calculator",
        "Evaluate a basic arithmetic expression. Supports + - * / ^ and parentheses.",
        "builtin",
        schema("expression" to "arithmetic expression"),
    )

    override suspend fun invoke(call: ToolCall): ToolResult {
        val expr = stringArg(call.arguments, "expression")
        return runCatching {
            ToolResult(call.id, spec.name, true, ExprEval.eval(expr).toString())
        }.getOrElse {
            ToolResult(call.id, spec.name, false, "", error = it.message)
        }
    }
}

class DateTimeTool : Tool {
    override val spec = ToolSpec(
        "datetime",
        "Return the current local date and time.",
        "builtin",
        schema("zone" to "optional IANA zone id"),
    )

    override suspend fun invoke(call: ToolCall): ToolResult {
        val zone = stringArg(call.arguments, "zone").ifBlank { ZoneId.systemDefault().id }
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.US)
        val text = Instant.now().atZone(runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.systemDefault())).format(fmt)
        return ToolResult(call.id, spec.name, true, text)
    }
}

class TelegramSendTool(private val gateway: TelegramGateway) : Tool {
    override val spec = ToolSpec(
        "telegram_send",
        "Send a message through the configured Telegram bot to an allowlisted chat.",
        "builtin",
        schema("chatId" to "telegram chat id", "text" to "message"),
        destructive = false,
    )

    override suspend fun invoke(call: ToolCall): ToolResult {
        val chat = stringArg(call.arguments, "chatId")
        val text = stringArg(call.arguments, "text")
        val r = gateway.sendText(chat, text)
        return ToolResult(call.id, spec.name, r.isSuccess, r.getOrNull() ?: "", error = r.exceptionOrNull()?.message)
    }
}

class DriveBackupTool(private val sync: OxygenSyncLayer) : Tool {
    override val spec = ToolSpec(
        "drive_backup",
        "Trigger a Google Drive backup of local OXYGEN data. Never runs silently.",
        "builtin",
        schema("collection" to "optional collection name"),
        destructive = false,
    )

    override suspend fun invoke(call: ToolCall): ToolResult {
        val result = sync.backup(stringArg(call.arguments, "collection").ifBlank { null })
        return ToolResult(call.id, spec.name, result.isSuccess, result.getOrNull() ?: "", error = result.exceptionOrNull()?.message)
    }
}

class FilesListTool(private val lister: suspend () -> String) : Tool {
    override val spec = ToolSpec(
        "files_list",
        "List locally imported documents known to OXYGEN.",
        "builtin",
        schema(),
    )

    override suspend fun invoke(call: ToolCall): ToolResult =
        ToolResult(call.id, spec.name, true, lister())
}

object ExprEval {
    fun eval(expr: String): Double {
        val p = Parser(expr.replace(" ", ""))
        val v = p.parseExpr()
        if (!p.done()) error("Unexpected input at ${p.pos}")
        return v
    }

    private class Parser(val s: String) {
        var pos = 0
        fun done() = pos >= s.length
        fun peek(): Char = if (done()) '\u0000' else s[pos]
        fun eat(c: Char) {
            if (peek() != c) error("Expected $c")
            pos++
        }

        fun parseExpr(): Double {
            var v = parseTerm()
            while (peek() == '+' || peek() == '-') {
                val op = peek(); pos++
                val r = parseTerm()
                v = if (op == '+') v + r else v - r
            }
            return v
        }

        fun parseTerm(): Double {
            var v = parsePower()
            while (peek() == '*' || peek() == '/') {
                val op = peek(); pos++
                val r = parsePower()
                v = if (op == '*') v * r else v / r
            }
            return v
        }

        fun parsePower(): Double {
            val v = parseUnary()
            return if (peek() == '^') {
                pos++
                v.pow(parseUnary())
            } else v
        }

        fun parseUnary(): Double {
            if (peek() == '-') {
                pos++; return -parseUnary()
            }
            if (peek() == '(') {
                pos++
                val v = parseExpr()
                eat(')')
                return v
            }
            val start = pos
            while (peek().isDigit() || peek() == '.') pos++
            if (start == pos) error("Number expected")
            return s.substring(start, pos).toDouble()
        }
    }
}

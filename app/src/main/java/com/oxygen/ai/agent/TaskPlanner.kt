package com.oxygen.ai.agent

import com.oxygen.ai.reasoning.TaskComplexity
import com.oxygen.ai.reasoning.TaskMode

class TaskPlanner {
    fun classify(text: String, mode: TaskMode, hasDocs: Boolean): TaskComplexity {
        val t = text.lowercase()
        val multi = listOf("and then", "then ", "after that", "also ", "verify", "summarize", "send")
        val hits = multi.count { t.contains(it) }
        return when {
            mode == TaskMode.AGENT || hits >= 3 || t.length > 800 -> TaskComplexity.EXTREME
            mode == TaskMode.COMPLEX || mode == TaskMode.RESEARCH || hits >= 2 -> TaskComplexity.HARD
            hasDocs || mode == TaskMode.CODING || mode == TaskMode.MATH || t.length > 280 -> TaskComplexity.MODERATE
            t.length < 40 -> TaskComplexity.TRIVIAL
            else -> TaskComplexity.SIMPLE
        }
    }

    fun plan(text: String, complexity: TaskComplexity, mode: TaskMode, hasDocs: Boolean, webEnabled: Boolean): AgentPlan {
        val t = text.lowercase()
        val needsWeb = webEnabled && (
            mode == TaskMode.RESEARCH ||
                t.contains("search") || t.contains("latest") || t.contains("news") ||
                t.contains("verify") || t.contains("on the web") || t.contains("look up")
            )
        val needsRag = hasDocs || t.contains("document") || t.contains("pdf") || t.contains("in my files")
        val needsMemory = t.contains("remember") || t.contains("my ") || t.contains("i prefer")
        val tools = buildList {
            if (needsRag) add("rag_search")
            if (needsMemory) add("memory_search")
            if (needsWeb) add("web_search")
            if (t.contains("telegram") || t.contains("send the result")) add("telegram_send")
            if (t.contains("calculate") || t.contains("compute") || Regex("\\d+\\s*[+\\-*/]\\s*\\d+").containsMatchIn(t)) {
                add("calculator")
            }
            if (t.contains("time") || t.contains("date")) add("datetime")
        }.distinct()
        val steps = buildList {
            add("classify")
            if (needsMemory) add("memory")
            if (needsRag) add("rag")
            if (needsWeb) add("web")
            if (tools.any { it == "telegram_send" || it == "calculator" }) add("tools")
            add("generate")
        }
        return AgentPlan(complexity, needsMemory, needsRag, needsWeb, tools, steps)
    }
}

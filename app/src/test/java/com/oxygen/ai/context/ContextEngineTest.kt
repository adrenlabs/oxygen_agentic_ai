package com.oxygen.ai.context

import com.oxygen.ai.inference.ChatTemplateKind
import com.oxygen.ai.reasoning.ReasoningCatalog
import com.oxygen.ai.reasoning.ReasoningLevel
import com.oxygen.ai.reasoning.TaskMode
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenEstimatorTest {
    @Test
    fun emptyIsZero() {
        assertTrue(TokenEstimator.estimate("") == 0)
    }

    @Test
    fun latinIsAboutFourChars() {
        val n = TokenEstimator.estimate("abcd".repeat(25))
        assertTrue(n in 20..40)
    }

    @Test
    fun cjkIsDenser() {
        val n = TokenEstimator.estimate("汉字".repeat(20))
        assertTrue(n >= 20)
    }
}

class ContextBudgetTest {
    @Test
    fun outputReserveIsHonored() {
        val b = ContextProfiles.allocate(8192, 640, true, true, true, true)
        assertTrue(b.usableInput + b.outputReserve == b.total)
        assertTrue(b.history >= 256)
        assertTrue(b.system >= 256)
    }

    @Test
    fun unusedChannelsGetZero() {
        val b = ContextProfiles.allocate(4096, 256, false, false, false, false)
        assertTrue(b.memories == 0 && b.rag == 0 && b.tools == 0 && b.web == 0)
        assertTrue(b.history > 1000)
    }
}

class RelevanceRankerTest {
    @Test
    fun relevantBeatsUnrelated() {
        val q = "qwen local inference android"
        val a = RelevanceRanker.score(q, "Running Qwen local inference on Android with llama.cpp")
        val b = RelevanceRanker.score(q, "The weather in Kolkata is humid in August")
        assertTrue(a > b)
    }

    @Test
    fun dedupeRemovesNearCopies() {
        val items = listOf(
            RankedItem("1", "a", "Same chunk of text about oxygen", 1f, "rag"),
            RankedItem("2", "b", "Same chunk of text about oxygen", 0.9f, "rag"),
        )
        assertTrue(RelevanceRanker.dedupe(items).size == 1)
    }
}

class ContextEngineAssemblyTest {
    @Test
    fun doesNotExceedBudgetAndKeepsUser() {
        val engine = OxygenContextEngine()
        val history = (1..40).map { PromptMessage("user", "history message number $it ".repeat(20)) }
        val pack = ContextPack(
            memories = listOf(RankedItem("m", "mem", "User prefers concise answers", 0.8f, "memory")),
            rag = listOf(RankedItem("r", "doc.pdf", "Document says the voltage is 12V on page 3", 0.7f, "rag", page = 3)),
            web = listOf(RankedItem("w", "site", "Latest firmware is 2.4", 0.6f, "web", url = "https://example.com")),
        )
        val profile = ReasoningCatalog.profile(ReasoningLevel.MEDIUM, TaskMode.RESEARCH, 32768, 8192)
        val assembled = engine.assemble(
            userText = "What voltage does the document specify?",
            history = history,
            pack = pack,
            profile = profile,
            systemPrompt = null,
            templateKind = ChatTemplateKind.QWEN3,
            nativeContext = 32768,
            deviceSafeContext = 8192,
            allowExtended = false,
        )
        assertTrue(assembled.usedTokens <= assembled.budget.total)
        assertTrue(assembled.messages.last().role == "user")
        assertTrue(assembled.citations.isNotEmpty())
        assertTrue(assembled.rendered.contains("<|im_start|>"))
    }
}

package com.oxygen.ai.inference

import com.oxygen.ai.context.PromptMessage

/**
 * Provider-neutral chat template renderer. Templates are selected from the
 * model profile, never hardcoded into Agent Core.
 */
enum class ChatTemplateKind {
    QWEN3,
    CHATML,
    GEMMA,
    PHI,
    DEEPSEEK,
    LLAMA3,
    RAW,
}

object ChatTemplate {
    fun detect(architecture: String, template: String, name: String): ChatTemplateKind {
        val blob = "$architecture $template $name".lowercase()
        return when {
            "qwen3" in blob || "qwen2" in blob || "qwen" in blob -> ChatTemplateKind.QWEN3
            "gemma" in blob -> ChatTemplateKind.GEMMA
            "phi" in blob -> ChatTemplateKind.PHI
            "deepseek" in blob -> ChatTemplateKind.DEEPSEEK
            "llama-3" in blob || "llama3" in blob -> ChatTemplateKind.LLAMA3
            "im_start" in blob || "chatml" in blob -> ChatTemplateKind.CHATML
            else -> ChatTemplateKind.CHATML
        }
    }

    fun render(
        kind: ChatTemplateKind,
        messages: List<PromptMessage>,
        addGenerationPrompt: Boolean,
        thinking: Boolean?,
    ): String {
        return when (kind) {
            ChatTemplateKind.QWEN3,
            ChatTemplateKind.CHATML,
            -> renderChatMl(messages, addGenerationPrompt, thinking)
            ChatTemplateKind.GEMMA -> renderGemma(messages, addGenerationPrompt)
            ChatTemplateKind.PHI -> renderPhi(messages, addGenerationPrompt)
            ChatTemplateKind.DEEPSEEK -> renderDeepSeek(messages, addGenerationPrompt)
            ChatTemplateKind.LLAMA3 -> renderLlama3(messages, addGenerationPrompt)
            ChatTemplateKind.RAW -> messages.joinToString("\n\n") { "${it.role}: ${it.content}" } +
                if (addGenerationPrompt) "\nassistant:" else ""
        }
    }

    private fun renderChatMl(
        messages: List<PromptMessage>,
        addGenerationPrompt: Boolean,
        thinking: Boolean?,
    ): String {
        val sb = StringBuilder()
        messages.forEach { m ->
            var content = m.content
            if (m.role == "user" && thinking != null) {
                val tag = if (thinking) "/think" else "/no_think"
                if (!content.contains("/think") && !content.contains("/no_think")) {
                    content = "$content\n$tag"
                }
            }
            sb.append("<|im_start|>").append(m.role).append('\n')
            sb.append(content).append("<|im_end|>\n")
        }
        if (addGenerationPrompt) sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    private fun renderGemma(messages: List<PromptMessage>, add: Boolean): String {
        val sb = StringBuilder()
        messages.forEach { m ->
            val role = if (m.role == "assistant") "model" else m.role
            sb.append("<start_of_turn>").append(role).append('\n')
            sb.append(m.content).append("<end_of_turn>\n")
        }
        if (add) sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    private fun renderPhi(messages: List<PromptMessage>, add: Boolean): String {
        val sb = StringBuilder()
        messages.forEach { m ->
            when (m.role) {
                "system" -> sb.append("<|system|>\n").append(m.content).append("<|end|>\n")
                "user" -> sb.append("<|user|>\n").append(m.content).append("<|end|>\n")
                else -> sb.append("<|assistant|>\n").append(m.content).append("<|end|>\n")
            }
        }
        if (add) sb.append("<|assistant|>\n")
        return sb.toString()
    }

    private fun renderDeepSeek(messages: List<PromptMessage>, add: Boolean): String {
        val sb = StringBuilder()
        messages.forEach { m ->
            when (m.role) {
                "system" -> sb.append(m.content).append("\n\n")
                "user" -> sb.append("User: ").append(m.content).append("\n\n")
                else -> sb.append("Assistant: ").append(m.content).append("\n\n")
            }
        }
        if (add) sb.append("Assistant: ")
        return sb.toString()
    }

    private fun renderLlama3(messages: List<PromptMessage>, add: Boolean): String {
        val sb = StringBuilder()
        messages.forEach { m ->
            sb.append("<|start_header_id|>").append(m.role).append("<|end_header_id|>\n\n")
            sb.append(m.content).append("<|eot_id|>")
        }
        if (add) sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        return sb.toString()
    }
}

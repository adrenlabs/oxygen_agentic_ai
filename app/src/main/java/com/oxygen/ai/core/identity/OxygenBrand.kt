package com.oxygen.ai.core.identity

/**
 * Configurable branding layer. Visual identity can change here without
 * touching Agent Core, persistence, or native inference.
 */
object OxygenBrand {
    const val APP_NAME: String = "OXYGEN AI"
    const val SHORT_NAME: String = "OXYGEN"
    const val PACKAGE_NAME: String = "com.oxygen.ai"
    const val CATEGORY: String = "Local-First Personal Agentic AI"
    const val DEFAULT_MODEL_FILE: String = "Qwen3-4B-Q4_K_M.gguf"
    const val DEFAULT_MODEL_DISPLAY: String = "Qwen3-4B"
    const val DESCRIPTION: String =
        "OXYGEN AI is a personal AI agent that can communicate, remember, retrieve knowledge, " +
            "use tools, search the web, work with documents, execute bounded multi-step tasks, " +
            "and run a local language model directly on Android."
}

package com.oxygen.ai.agent

import com.oxygen.ai.context.AssembledPrompt
import com.oxygen.ai.context.RankedItem
import com.oxygen.ai.reasoning.ReasoningLevel
import com.oxygen.ai.reasoning.ReasoningProfile
import com.oxygen.ai.reasoning.TaskComplexity
import com.oxygen.ai.reasoning.TaskMode
import com.oxygen.ai.tools.ToolCall
import com.oxygen.ai.tools.ToolResult

enum class AgentState {
    IDLE,
    THINKING,
    PLANNING,
    RETRIEVING_MEMORY,
    RETRIEVING_RAG,
    CALLING_TOOL,
    SEARCHING_WEB,
    PROCESSING_RESULT,
    GENERATING,
    WAITING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

fun AgentState.userLabel(): String = when (this) {
    AgentState.IDLE -> "Ready"
    AgentState.THINKING -> "Thinking…"
    AgentState.PLANNING -> "Planning…"
    AgentState.RETRIEVING_MEMORY -> "Reading memory…"
    AgentState.RETRIEVING_RAG -> "Searching documents…"
    AgentState.CALLING_TOOL -> "Using tool…"
    AgentState.SEARCHING_WEB -> "Searching the web…"
    AgentState.PROCESSING_RESULT -> "Processing…"
    AgentState.GENERATING -> "Generating…"
    AgentState.WAITING -> "Waiting…"
    AgentState.COMPLETED -> "Done"
    AgentState.FAILED -> "Failed"
    AgentState.CANCELLED -> "Cancelled"
}

data class AgentLimits(
    val maxToolCalls: Int = 8,
    val maxIterations: Int = 6,
    val maxExecutionTimeMs: Long = 180_000,
    val maxContextTokens: Int = 32_768,
    val maxToolOutputSize: Int = 8_000,
    val maxRetries: Int = 2,
)

data class AgentRequest(
    val conversationId: String?,
    val text: String,
    val source: String = "android",
    val attachments: List<String> = emptyList(),
    val documentIds: List<String> = emptyList(),
    val reasoningLevel: ReasoningLevel? = null,
    val taskMode: TaskMode? = null,
    val systemPrompt: String? = null,
    val enabledTools: List<String>? = null,
    /** Tool names explicitly confirmed by the user for this execution. */
    val confirmedTools: Set<String> = emptySet(),
)

data class AgentTurnResult(
    val conversationId: String,
    val messageId: String,
    val text: String,
    val state: AgentState,
    val citations: List<RankedItem>,
    val partial: Boolean,
    val error: String? = null,
)

sealed class AgentEvent {
    data class State(val state: AgentState) : AgentEvent()
    data class Token(val text: String) : AgentEvent()
    data class Tool(val name: String, val status: String) : AgentEvent()
    data class Citation(val item: RankedItem) : AgentEvent()
    data class Metrics(val promptTokens: Int, val generatedTokens: Int, val tokensPerSecond: Double) : AgentEvent()
    data class Completed(val result: AgentTurnResult) : AgentEvent()
    data class Failed(val message: String) : AgentEvent()
}

data class AgentPlan(
    val complexity: TaskComplexity,
    val needsMemory: Boolean,
    val needsRag: Boolean,
    val needsWeb: Boolean,
    val toolNames: List<String>,
    val steps: List<String>,
)

interface AgentSink {
    suspend fun submit(request: AgentRequest): AgentTurnResult
}

class AgentSession(
    val id: String,
    val conversationId: String,
    val request: AgentRequest,
) {
    var state: AgentState = AgentState.IDLE
    var profile: ReasoningProfile? = null
    var plan: AgentPlan? = null
    var memories: List<RankedItem> = emptyList()
    var rag: List<RankedItem> = emptyList()
    var web: List<RankedItem> = emptyList()
    val toolResults: MutableList<ToolResult> = mutableListOf()
    var prompt: AssembledPrompt? = null
    var output: StringBuilder = StringBuilder()
    var iterations: Int = 0
    var toolCalls: Int = 0
    var startedAt: Long = System.currentTimeMillis()
    @Volatile var cancelled: Boolean = false
}

package com.oxygen.ai.memory

enum class MemoryCategory { PERSONAL, PREFERENCE, PROJECT, TASK, FACT, WORKFLOW, REFERENCE }

enum class MemoryPolicyAction { SAVE, UPDATE, MERGE, IGNORE, DELETE }

enum class ConversationMemoryPolicy { AUTO, ALWAYS_ASK, OFF }

data class MemoryRecord(
    val id: String,
    val content: String,
    val category: MemoryCategory,
    val importance: Float,
    val confidence: Float,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
    val conversationId: String?,
    val tags: List<String> = emptyList(),
    val embedding: FloatArray? = null,
)

data class MemoryDecision(
    val action: MemoryPolicyAction,
    val record: MemoryRecord?,
    val reason: String,
)

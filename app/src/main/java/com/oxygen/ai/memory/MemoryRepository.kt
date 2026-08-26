package com.oxygen.ai.memory

import com.oxygen.ai.context.RankedItem
import com.oxygen.ai.context.RelevanceRanker
import com.oxygen.ai.core.error.OxygenError
import com.oxygen.ai.data.db.dao.MemoryDao
import com.oxygen.ai.data.db.entities.MemoryEntity
import com.oxygen.ai.data.db.entities.MemoryTagEntity
import com.oxygen.ai.rag.EmbeddingProvider
import com.oxygen.ai.rag.VectorMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class MemoryRepository(
    private val dao: MemoryDao,
    private val embeddings: EmbeddingProvider,
) {
    fun observe(): Flow<List<MemoryRecord>> = dao.observe().map { list -> list.map { it.toRecord() } }

    suspend fun get(id: String): MemoryRecord? = dao.get(id)?.toRecord()

    suspend fun all(): List<MemoryRecord> = dao.allActive().map { it.toRecord() }

    suspend fun save(record: MemoryRecord) {
        runCatching {
            val embedding = embeddings.embed(record.content)
            dao.upsert(record.toEntity(VectorMath.toBytes(embedding)))
            record.tags.forEach { dao.addTag(MemoryTagEntity(record.id, it)) }
        }.onFailure { throw OxygenError.MemoryWriteFailed(it.message ?: "write") }
    }

    suspend fun delete(id: String, hard: Boolean = false) {
        if (hard) dao.hardDelete(id) else dao.softDelete(id, System.currentTimeMillis())
    }

    suspend fun retrieve(query: String, limit: Int, minScore: Float): List<RankedItem> {
        val all = runCatching { dao.allActive() }.getOrElse { throw OxygenError.MemoryReadFailed(it.message ?: "read") }
        if (all.isEmpty()) return emptyList()
        val qVec = embeddings.embed(query)
        val now = System.currentTimeMillis()
        return all.map { entity ->
            val recency = recencyWeight(entity.updatedAt, now)
            val lexical = RelevanceRanker.score(query, entity.content, recency, entity.importance)
            val semantic = entity.embedding?.let { VectorMath.cosine(qVec, VectorMath.fromBytes(it)) } ?: 0f
            val score = 0.55f * semantic + 0.45f * lexical
            RankedItem(
                id = entity.id,
                title = entity.category,
                content = entity.content,
                score = score,
                source = "memory",
            )
        }
            .filter { it.score >= minScore }
            .sortedByDescending { it.score }
            .take(limit)
    }

    suspend fun decideAndApply(
        userText: String,
        assistantText: String,
        conversationId: String,
        policy: ConversationMemoryPolicy,
    ): MemoryDecision {
        if (policy == ConversationMemoryPolicy.OFF) {
            return MemoryDecision(MemoryPolicyAction.IGNORE, null, "memory disabled")
        }
        val candidate = extractCandidate(userText, assistantText) ?: return MemoryDecision(
            MemoryPolicyAction.IGNORE, null, "nothing durable",
        )
        if (policy == ConversationMemoryPolicy.ALWAYS_ASK) {
            return MemoryDecision(MemoryPolicyAction.SAVE, candidate.copy(conversationId = conversationId), "needs confirmation")
        }
        val existing = dao.allActive()
        val mergeTarget = existing.firstOrNull { MemoryMerger.shouldMerge(it.content, candidate.content) }
        return if (mergeTarget != null) {
            val merged = candidate.copy(
                id = mergeTarget.id,
                content = MemoryMerger.mergeContent(mergeTarget.content, candidate.content),
                createdAt = mergeTarget.createdAt,
                conversationId = conversationId,
            )
            save(merged)
            MemoryDecision(MemoryPolicyAction.MERGE, merged, "merged with ${mergeTarget.id}")
        } else {
            val stored = candidate.copy(conversationId = conversationId)
            save(stored)
            MemoryDecision(MemoryPolicyAction.SAVE, stored, "new durable fact")
        }
    }

    private fun extractCandidate(userText: String, assistantText: String): MemoryRecord? {
        val blob = "$userText\n$assistantText"
        val look = listOf(
            Regex("(?i)\\bmy name is ([^.\\n]+)"),
            Regex("(?i)\\bi (?:prefer|like|want) ([^.\\n]+)"),
            Regex("(?i)\\bremember (?:that )?([^.\\n]+)"),
            Regex("(?i)\\bi (?:live|work|am based) in ([^.\\n]+)"),
        )
        for (r in look) {
            val m = r.find(userText) ?: continue
            val content = m.groupValues.last().trim()
            if (content.length < 3) continue
            return newRecord(content, categoryOf(content), 0.7f)
        }
        if (userText.length in 24..280 && userText.contains(Regex("(?i)\\b(always|never|prefer|project|deadline)\\b"))) {
            return newRecord(userText.trim(), MemoryCategory.PREFERENCE, 0.45f)
        }
        return if (blob.contains("[[oxygen-memory]]")) {
            newRecord(assistantText.take(400), MemoryCategory.FACT, 0.4f)
        } else null
    }

    private fun categoryOf(text: String): MemoryCategory {
        val l = text.lowercase()
        return when {
            "prefer" in l || "like" in l -> MemoryCategory.PREFERENCE
            "project" in l -> MemoryCategory.PROJECT
            "deadline" in l || "todo" in l -> MemoryCategory.TASK
            "live" in l || "name" in l -> MemoryCategory.PERSONAL
            else -> MemoryCategory.FACT
        }
    }

    private fun newRecord(content: String, category: MemoryCategory, importance: Float): MemoryRecord {
        val now = System.currentTimeMillis()
        return MemoryRecord(
            id = UUID.randomUUID().toString(),
            content = content,
            category = category,
            importance = importance,
            confidence = 0.7f,
            source = "conversation",
            createdAt = now,
            updatedAt = now,
            conversationId = null,
        )
    }

    private fun recencyWeight(updatedAt: Long, now: Long): Float {
        val days = ((now - updatedAt).coerceAtLeast(0)) / 86_400_000f
        return (1f / (1f + days / 14f)).coerceIn(0f, 1f)
    }
}

private fun MemoryEntity.toRecord() = MemoryRecord(
    id, content, runCatching { MemoryCategory.valueOf(category) }.getOrDefault(MemoryCategory.FACT),
    importance, confidence, source, createdAt, updatedAt, conversationId,
)

private fun MemoryRecord.toEntity(embedding: ByteArray?) = MemoryEntity(
    id, content, category.name, importance, confidence, source, createdAt, updatedAt, conversationId, embedding, false,
)

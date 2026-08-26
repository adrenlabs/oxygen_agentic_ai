package com.oxygen.ai.chat

import com.oxygen.ai.context.PromptMessage
import com.oxygen.ai.context.RankedItem
import com.oxygen.ai.data.db.dao.ConversationDao
import com.oxygen.ai.data.db.dao.MessageDao
import com.oxygen.ai.data.db.entities.ConversationEntity
import com.oxygen.ai.data.db.entities.MessageCitationEntity
import com.oxygen.ai.data.db.entities.MessageEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ConversationRepository(
    private val conversations: ConversationDao,
    private val messages: MessageDao,
) {
    fun observeConversations(): Flow<List<ConversationEntity>> = conversations.observeActive()
    fun observeMessages(id: String): Flow<List<MessageEntity>> = messages.observe(id)

    suspend fun get(id: String) = conversations.get(id)
    suspend fun all() = conversations.all()
    suspend fun search(q: String) = conversations.search(q)

    suspend fun create(
        title: String = "New conversation",
        modelId: String? = null,
        reasoning: String = "MEDIUM",
        taskMode: String = "CHAT",
    ): ConversationEntity {
        val now = System.currentTimeMillis()
        val entity = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now,
            modelId = modelId,
            reasoningProfile = reasoning,
            taskMode = taskMode,
        )
        conversations.upsert(entity)
        return entity
    }

    suspend fun rename(id: String, title: String) = conversations.rename(id, title, System.currentTimeMillis())
    suspend fun archive(id: String, archived: Boolean) = conversations.setArchived(id, archived, System.currentTimeMillis())
    suspend fun delete(id: String) = conversations.delete(id)

    suspend fun addMessage(
        conversationId: String,
        role: String,
        content: String,
        status: String = "COMPLETE",
        modelId: String? = null,
        citations: List<RankedItem> = emptyList(),
        metrics: Triple<Int, Int, Double>? = null,
    ): MessageEntity {
        val entity = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            role = role,
            content = content,
            createdAt = System.currentTimeMillis(),
            status = status,
            modelId = modelId,
            promptTokens = metrics?.first ?: 0,
            generatedTokens = metrics?.second ?: 0,
            tokensPerSecond = metrics?.third ?: 0.0,
        )
        messages.upsert(entity)
        citations.forEach {
            messages.upsertCitation(
                MessageCitationEntity(
                    id = UUID.randomUUID().toString(),
                    messageId = entity.id,
                    kind = it.source,
                    title = it.title,
                    url = it.url,
                    snippet = it.content.take(240),
                    page = it.page,
                    retrievedAt = System.currentTimeMillis(),
                ),
            )
        }
        conversations.get(conversationId)?.let {
            val title = if (it.title == "New conversation" && role == "user") content.take(42) else it.title
            conversations.upsert(it.copy(title = title, updatedAt = System.currentTimeMillis()))
        }
        return entity
    }

    suspend fun updateMessage(entity: MessageEntity) = messages.update(entity)
    suspend fun deleteMessage(id: String) = messages.delete(id)
    suspend fun historyAsPrompt(conversationId: String): List<PromptMessage> =
        messages.forConversation(conversationId).map { PromptMessage(it.role, it.content) }

    suspend fun exportJson(id: String): String {
        val c = conversations.get(id) ?: return "{}"
        val ms = messages.forConversation(id)
        return buildString {
            append("{\"id\":\"").append(c.id).append("\",\"title\":\"").append(c.title.replace("\"", "'"))
            append("\",\"messages\":[")
            append(ms.joinToString(",") { "{\"role\":\"${it.role}\",\"content\":\"${it.content.replace("\"", "'")}\"}" })
            append("]}")
        }
    }
}

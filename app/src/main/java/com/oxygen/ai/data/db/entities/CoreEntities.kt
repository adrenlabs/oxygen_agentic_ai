package com.oxygen.ai.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val archived: Boolean = false,
    val folder: String? = null,
    val tags: List<String> = emptyList(),
    val modelId: String? = null,
    val reasoningProfile: String = "MEDIUM",
    val taskMode: String = "CHAT",
    val systemPrompt: String? = null,
    val memoryPolicy: String = "AUTO",
    val enabledTools: List<String> = emptyList(),
    val ragSources: List<String> = emptyList(),
    val parentConversationId: String? = null,
    val branchFromMessageId: String? = null,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conversationId"), Index("createdAt")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val createdAt: Long,
    val edited: Boolean = false,
    val status: String = "COMPLETE",
    val parentMessageId: String? = null,
    val thinkingVisible: String? = null,
    val promptTokens: Int = 0,
    val generatedTokens: Int = 0,
    val tokensPerSecond: Double = 0.0,
    val modelId: String? = null,
    val errorCode: String? = null,
)

@Entity(
    tableName = "message_citations",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("messageId")],
)
data class MessageCitationEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val kind: String,
    val title: String,
    val url: String? = null,
    val domain: String? = null,
    val snippet: String? = null,
    val documentId: String? = null,
    val page: Int? = null,
    val retrievedAt: Long = 0L,
)

@Entity(tableName = "memories", indices = [Index("category"), Index("updatedAt"), Index("conversationId")])
data class MemoryEntity(
    @PrimaryKey val id: String,
    val content: String,
    val category: String,
    val importance: Float,
    val confidence: Float,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
    val conversationId: String? = null,
    val embedding: ByteArray? = null,
    val deleted: Boolean = false,
)

@Entity(
    tableName = "memory_tags",
    primaryKeys = ["memoryId", "tag"],
    foreignKeys = [
        ForeignKey(
            entity = MemoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["memoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MemoryTagEntity(
    val memoryId: String,
    val tag: String,
)

@Entity(tableName = "memory_links", primaryKeys = ["fromId", "toId"])
data class MemoryLinkEntity(
    val fromId: String,
    val toId: String,
    val relation: String,
)

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val mimeType: String,
    val sourceUri: String,
    val localPath: String,
    val sizeBytes: Long,
    val pageCount: Int = 0,
    val sha256: String,
    val status: String,
    val error: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val chunkCount: Int = 0,
    val indexedAt: Long? = null,
)

@Entity(
    tableName = "document_chunks",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("documentId")],
)
data class DocumentChunkEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val chunkIndex: Int,
    val page: Int?,
    val text: String,
    val tokenEstimate: Int,
    val embedding: ByteArray?,
    val checksum: String,
)

@Entity(tableName = "embedding_metadata")
data class EmbeddingMetadataEntity(
    @PrimaryKey val ownerId: String,
    val ownerType: String,
    val providerId: String,
    val dimensions: Int,
    val createdAt: Long,
)

@Entity(tableName = "tool_permissions", primaryKeys = ["targetType", "targetId"])
data class ToolPermissionEntity(
    val targetType: String,
    val targetId: String,
    val mode: String,
    val updatedAt: Long,
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val recordId: String,
    val collection: String,
    val schemaVersion: Int,
    val deviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Long,
    val checksum: String,
    val syncState: String,
    val remoteFileId: String? = null,
    val lastError: String? = null,
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Entity(tableName = "model_profiles")
data class ModelProfileEntity(
    @PrimaryKey val modelId: String,
    val displayName: String,
    val runtime: String,
    val filePath: String,
    val fileSize: Long,
    val sha256: String,
    val architecture: String,
    val quantization: String,
    val parameterCount: String?,
    val contextLimit: Int,
    val recommendedContext: Int,
    val chatTemplate: String,
    val thinkingSupport: Boolean,
    val toolCallingSupport: Boolean,
    val streamingSupport: Boolean,
    val multimodalSupport: Boolean,
    val favorite: Boolean,
    val lastUsedAt: Long?,
    val createdAt: Long,
    val defaultTemperature: Float,
    val defaultTopP: Float,
    val defaultTopK: Int,
    val defaultMinP: Float,
    val defaultRepeatPenalty: Float,
)

@Entity(tableName = "attachments", indices = [Index("conversationId"), Index("messageId")])
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val messageId: String?,
    val displayName: String,
    val mimeType: String,
    val localPath: String,
    val sizeBytes: Long,
    val classification: String,
    val documentId: String?,
    val createdAt: Long,
)

@Entity(tableName = "mcp_servers")
data class McpServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val transport: String,
    val endpoint: String,
    val enabled: Boolean,
    val permissionMode: String,
    val createdAt: Long,
    val lastSeenAt: Long? = null,
    val lastError: String? = null,
)

@Entity(tableName = "offline_queue")
data class OfflineQueueEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val payloadJson: String,
    val createdAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
)

@Entity(tableName = "telegram_allowlist")
data class TelegramAllowlistEntity(
    @PrimaryKey val userId: Long,
    val username: String?,
    val addedAt: Long,
)

@Entity(tableName = "agent_runs", indices = [Index("conversationId")])
data class AgentRunEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val state: String,
    val createdAt: Long,
    val finishedAt: Long?,
    val iterations: Int,
    val toolCalls: Int,
    val errorCode: String?,
)

@Entity(tableName = "model_downloads")
data class ModelDownloadEntity(
    @PrimaryKey val id: String,
    val url: String,
    val destPath: String,
    val bytesDownloaded: Long,
    val bytesTotal: Long,
    val status: String,
    val sha256Expected: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val error: String?,
)

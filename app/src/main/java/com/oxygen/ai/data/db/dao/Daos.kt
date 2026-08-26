package com.oxygen.ai.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.oxygen.ai.data.db.entities.AgentRunEntity
import com.oxygen.ai.data.db.entities.AttachmentEntity
import com.oxygen.ai.data.db.entities.ConversationEntity
import com.oxygen.ai.data.db.entities.DocumentChunkEntity
import com.oxygen.ai.data.db.entities.DocumentEntity
import com.oxygen.ai.data.db.entities.EmbeddingMetadataEntity
import com.oxygen.ai.data.db.entities.McpServerEntity
import com.oxygen.ai.data.db.entities.MemoryEntity
import com.oxygen.ai.data.db.entities.MemoryLinkEntity
import com.oxygen.ai.data.db.entities.MemoryTagEntity
import com.oxygen.ai.data.db.entities.MessageCitationEntity
import com.oxygen.ai.data.db.entities.MessageEntity
import com.oxygen.ai.data.db.entities.ModelDownloadEntity
import com.oxygen.ai.data.db.entities.ModelProfileEntity
import com.oxygen.ai.data.db.entities.OfflineQueueEntity
import com.oxygen.ai.data.db.entities.SettingsEntity
import com.oxygen.ai.data.db.entities.SyncStateEntity
import com.oxygen.ai.data.db.entities.TelegramAllowlistEntity
import com.oxygen.ai.data.db.entities.ToolPermissionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE archived = 0 ORDER BY updatedAt DESC")
    fun observeActive(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun all(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: String): ConversationEntity?

    @Query(
        "SELECT * FROM conversations WHERE title LIKE '%' || :q || '%' OR id IN " +
            "(SELECT conversationId FROM messages WHERE content LIKE '%' || :q || '%') " +
            "ORDER BY updatedAt DESC",
    )
    suspend fun search(q: String): List<ConversationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE conversations SET archived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, now: Long)

    @Query("UPDATE conversations SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: String, title: String, now: Long)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :cid ORDER BY createdAt ASC")
    fun observe(cid: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :cid ORDER BY createdAt ASC")
    suspend fun forConversation(cid: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun get(id: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MessageEntity)

    @Update
    suspend fun update(entity: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :cid")
    suspend fun deleteForConversation(cid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCitation(entity: MessageCitationEntity)

    @Query("SELECT * FROM message_citations WHERE messageId = :mid")
    suspend fun citations(mid: String): List<MessageCitationEntity>

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun count(): Int
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories WHERE deleted = 0 ORDER BY importance DESC, updatedAt DESC")
    fun observe(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE deleted = 0")
    suspend fun allActive(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun get(id: String): MemoryEntity?

    @Query(
        "SELECT * FROM memories WHERE deleted = 0 AND (" +
            "content LIKE '%' || :q || '%' OR category = :q) " +
            "ORDER BY importance DESC LIMIT :limit",
    )
    suspend fun search(q: String, limit: Int): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MemoryEntity)

    @Query("UPDATE memories SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTag(tag: MemoryTagEntity)

    @Query("SELECT * FROM memory_tags WHERE memoryId = :id")
    suspend fun tags(id: String): List<MemoryTagEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun link(link: MemoryLinkEntity)

    @Query("SELECT COUNT(*) FROM memories WHERE deleted = 0")
    suspend fun count(): Int
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun observe(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents")
    suspend fun all(): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun get(id: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChunk(chunk: DocumentChunkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChunks(chunks: List<DocumentChunkEntity>)

    @Query("SELECT * FROM document_chunks WHERE documentId = :docId ORDER BY chunkIndex ASC")
    suspend fun chunks(docId: String): List<DocumentChunkEntity>

    @Query("SELECT * FROM document_chunks")
    suspend fun allChunks(): List<DocumentChunkEntity>

    @Query("DELETE FROM document_chunks WHERE documentId = :docId")
    suspend fun deleteChunks(docId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEmbeddingMeta(meta: EmbeddingMetadataEntity)
}

@Dao
interface ModelDao {
    @Query("SELECT * FROM model_profiles ORDER BY favorite DESC, lastUsedAt DESC")
    fun observe(): Flow<List<ModelProfileEntity>>

    @Query("SELECT * FROM model_profiles")
    suspend fun all(): List<ModelProfileEntity>

    @Query("SELECT * FROM model_profiles WHERE modelId = :id")
    suspend fun get(id: String): ModelProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ModelProfileEntity)

    @Query("DELETE FROM model_profiles WHERE modelId = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(entity: ModelDownloadEntity)

    @Query("SELECT * FROM model_downloads WHERE id = :id")
    suspend fun download(id: String): ModelDownloadEntity?

    @Query("SELECT * FROM model_downloads ORDER BY updatedAt DESC")
    fun observeDownloads(): Flow<List<ModelDownloadEntity>>
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings")
    suspend fun all(): List<SettingsEntity>

    @Query("SELECT * FROM settings")
    fun observeAll(): Flow<List<SettingsEntity>>

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SettingsEntity)

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun delete(key: String)
}

@Dao
interface PermissionDao {
    @Query("SELECT * FROM tool_permissions")
    suspend fun all(): List<ToolPermissionEntity>

    @Query("SELECT * FROM tool_permissions WHERE targetType = :type AND targetId = :id")
    suspend fun get(type: String, id: String): ToolPermissionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ToolPermissionEntity)
}

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_state WHERE recordId = :id")
    suspend fun get(id: String): SyncStateEntity?

    @Query("SELECT * FROM sync_state WHERE collection = :collection")
    suspend fun forCollection(collection: String): List<SyncStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(entity: OfflineQueueEntity)

    @Query("SELECT * FROM offline_queue ORDER BY createdAt ASC")
    suspend fun queue(): List<OfflineQueueEntity>

    @Query("DELETE FROM offline_queue WHERE id = :id")
    suspend fun dequeue(id: String)
}

@Dao
interface McpDao {
    @Query("SELECT * FROM mcp_servers ORDER BY name")
    fun observe(): Flow<List<McpServerEntity>>

    @Query("SELECT * FROM mcp_servers")
    suspend fun all(): List<McpServerEntity>

    @Query("SELECT * FROM mcp_servers WHERE id = :id")
    suspend fun get(id: String): McpServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: McpServerEntity)

    @Query("DELETE FROM mcp_servers WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE conversationId = :cid ORDER BY createdAt ASC")
    suspend fun forConversation(cid: String): List<AttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface TelegramDao {
    @Query("SELECT * FROM telegram_allowlist")
    suspend fun allowlist(): List<TelegramAllowlistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entity: TelegramAllowlistEntity)

    @Query("DELETE FROM telegram_allowlist WHERE userId = :id")
    suspend fun remove(id: Long)
}

@Dao
interface AgentRunDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AgentRunEntity)
}

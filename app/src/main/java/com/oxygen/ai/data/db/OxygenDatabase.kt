package com.oxygen.ai.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.oxygen.ai.data.db.dao.AgentRunDao
import com.oxygen.ai.data.db.dao.AttachmentDao
import com.oxygen.ai.data.db.dao.ConversationDao
import com.oxygen.ai.data.db.dao.DocumentDao
import com.oxygen.ai.data.db.dao.McpDao
import com.oxygen.ai.data.db.dao.MemoryDao
import com.oxygen.ai.data.db.dao.MessageDao
import com.oxygen.ai.data.db.dao.ModelDao
import com.oxygen.ai.data.db.dao.PermissionDao
import com.oxygen.ai.data.db.dao.SettingsDao
import com.oxygen.ai.data.db.dao.SyncDao
import com.oxygen.ai.data.db.dao.TelegramDao
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

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        MessageCitationEntity::class,
        MemoryEntity::class,
        MemoryTagEntity::class,
        MemoryLinkEntity::class,
        DocumentEntity::class,
        DocumentChunkEntity::class,
        EmbeddingMetadataEntity::class,
        ToolPermissionEntity::class,
        SyncStateEntity::class,
        SettingsEntity::class,
        ModelProfileEntity::class,
        AttachmentEntity::class,
        McpServerEntity::class,
        OfflineQueueEntity::class,
        TelegramAllowlistEntity::class,
        AgentRunEntity::class,
        ModelDownloadEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class OxygenDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao
    abstract fun memories(): MemoryDao
    abstract fun documents(): DocumentDao
    abstract fun models(): ModelDao
    abstract fun settings(): SettingsDao
    abstract fun permissions(): PermissionDao
    abstract fun sync(): SyncDao
    abstract fun mcp(): McpDao
    abstract fun attachments(): AttachmentDao
    abstract fun telegram(): TelegramDao
    abstract fun agentRuns(): AgentRunDao

    companion object {
        fun create(context: Context): OxygenDatabase =
            Room.databaseBuilder(context, OxygenDatabase::class.java, "oxygen.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}

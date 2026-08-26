package com.oxygen.ai.rag

import com.oxygen.ai.context.RankedItem
import com.oxygen.ai.context.RelevanceRanker
import com.oxygen.ai.core.error.OxygenError
import com.oxygen.ai.core.logging.OxygenLog
import com.oxygen.ai.data.db.dao.DocumentDao
import com.oxygen.ai.data.db.entities.DocumentChunkEntity
import com.oxygen.ai.data.db.entities.DocumentEntity
import com.oxygen.ai.data.db.entities.EmbeddingMetadataEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID

data class RetrievalQuery(
    val text: String,
    val limit: Int,
    val minScore: Float,
    val documentIds: List<String> = emptyList(),
)

class RagPipeline(
    private val dao: DocumentDao,
    private val embeddings: EmbeddingProvider,
    private val chunker: Chunker,
    private val parsers: ParserRegistry,
) {
    fun observeDocuments(): Flow<List<DocumentEntity>> = dao.observe()

    suspend fun indexFile(
        file: File,
        displayName: String,
        mimeType: String,
        sourceUri: String,
        onProgress: (Float) -> Unit = {},
    ): DocumentEntity = withContext(Dispatchers.IO) {
        val parser = parsers.find(mimeType, displayName)
            ?: throw OxygenError.RagIndexFailed("Unsupported type $mimeType")
        onProgress(0.05f)
        val parsed = FileInputStream(file).use { parser.parse(it, displayName, mimeType) }
        if (parsed.text.isBlank()) throw OxygenError.RagIndexFailed("No extractable text")
        onProgress(0.25f)
        val pageOf = pageLookup(parsed.pageStarts)
        val chunks = chunker.chunk(parsed.text, pageOf)
        onProgress(0.4f)
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val sha = sha256File(file)
        val entity = DocumentEntity(
            id = id,
            displayName = displayName,
            mimeType = mimeType,
            sourceUri = sourceUri,
            localPath = file.absolutePath,
            sizeBytes = file.length(),
            pageCount = parsed.pageCount,
            sha256 = sha,
            status = "INDEXING",
            error = null,
            createdAt = now,
            updatedAt = now,
            chunkCount = chunks.size,
            indexedAt = null,
        )
        dao.upsert(entity)
        val stored = ArrayList<DocumentChunkEntity>(chunks.size)
        chunks.forEachIndexed { i, chunk ->
            val vec = runCatching { embeddings.embed(chunk.text) }
                .getOrElse { throw OxygenError.EmbeddingFailed(it.message ?: "embed") }
            stored.add(
                DocumentChunkEntity(
                    id = "$id-$i",
                    documentId = id,
                    chunkIndex = chunk.index,
                    page = chunk.page,
                    text = chunk.text,
                    tokenEstimate = chunk.tokenEstimate,
                    embedding = VectorMath.toBytes(vec),
                    checksum = chunk.checksum,
                ),
            )
            if (i % 8 == 0) onProgress(0.4f + 0.55f * (i + 1) / chunks.size)
        }
        dao.upsertChunks(stored)
        dao.upsertEmbeddingMeta(
            EmbeddingMetadataEntity(id, "document", embeddings.id, embeddings.dimensions, now),
        )
        val done = entity.copy(status = "READY", indexedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
        dao.upsert(done)
        onProgress(1f)
        OxygenLog.i("rag", "Indexed $displayName chunks=${chunks.size}")
        done
    }

    suspend fun delete(id: String) {
        dao.deleteChunks(id)
        dao.delete(id)
    }

    suspend fun retrieve(query: RetrievalQuery): List<RankedItem> = withContext(Dispatchers.Default) {
        val chunks = if (query.documentIds.isEmpty()) dao.allChunks()
        else query.documentIds.flatMap { dao.chunks(it) }
        if (chunks.isEmpty()) return@withContext emptyList()
        val q = embeddings.embed(query.text)
        val docs = dao.all().associateBy { it.id }
        chunks.map { chunk ->
            val semantic = chunk.embedding?.let { VectorMath.cosine(q, VectorMath.fromBytes(it)) } ?: 0f
            val lexical = RelevanceRanker.bm25ish(query.text, chunk.text)
            val score = 0.68f * semantic + 0.32f * lexical
            RankedItem(
                id = chunk.id,
                title = docs[chunk.documentId]?.displayName ?: chunk.documentId,
                content = chunk.text,
                score = score,
                source = "rag",
                page = chunk.page,
            )
        }
            .filter { it.score >= query.minScore }
            .sortedByDescending { it.score }
            .take(query.limit)
    }

    private fun pageLookup(starts: List<Int>): (Int) -> Int? {
        if (starts.isEmpty()) return { null }
        return { offset ->
            var page = 1
            for (i in starts.indices) {
                if (starts[i] <= offset) page = i + 1 else break
            }
            page
        }
    }

    private fun sha256File(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

package com.oxygen.ai.search

data class SearchRequest(
    val query: String,
    val count: Int = 5,
    val language: String? = null,
    val timeoutMs: Long = 15_000,
)

data class SearchHit(
    val title: String,
    val url: String,
    val domain: String,
    val snippet: String,
    val source: String,
    val retrievedAt: Long,
)

data class SearchResponse(
    val ok: Boolean,
    val results: List<SearchHit>,
    val error: String? = null,
)

interface SearchProvider {
    val id: String
    suspend fun search(request: SearchRequest): SearchResponse
}

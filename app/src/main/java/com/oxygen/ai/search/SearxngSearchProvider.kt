package com.oxygen.ai.search

import com.oxygen.ai.core.error.OxygenError
import com.oxygen.ai.core.logging.OxygenLog
import com.oxygen.ai.core.net.OnlineChecker
import com.oxygen.ai.security.PromptInjectionDefense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

class SearxngSearchProvider(
    private val endpointProvider: () -> String,
    private val enabled: () -> Boolean,
    private val network: OnlineChecker,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) : SearchProvider {
    override val id: String = "searxng"

    override suspend fun search(request: SearchRequest): SearchResponse {
        if (!enabled()) return SearchResponse(false, emptyList(), "Web search is disabled")
        if (!network.isOnlineNow()) throw OxygenError.Offline("web search")
        val base = endpointProvider().trim().trimEnd('/')
        if (base.isBlank()) throw OxygenError.SearchFailed("No SearXNG endpoint configured")
        val baseUrl = runCatching { base.toHttpUrl() }.getOrElse { throw OxygenError.SearchFailed("Invalid SearXNG endpoint") }
        if (baseUrl.scheme != "https") throw OxygenError.SearchFailed("SearXNG endpoint must use HTTPS")
        return withContext(Dispatchers.IO) {
            try {
                withTimeout(request.timeoutMs) {
                    val url = baseUrl.newBuilder()
                        .addPathSegment("search")
                        .addQueryParameter("q", request.query)
                        .addQueryParameter("format", "json")
                        .addQueryParameter("language", request.language ?: "en")
                        .build()
                    val httpReq = Request.Builder().url(url).header("Accept", "application/json").get().build()
                    client.newCall(httpReq).execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) throw OxygenError.SearchFailed("HTTP ${resp.code}")
                        parse(body, request.count)
                    }
                }
            } catch (e: OxygenError) {
                throw e
            } catch (e: Exception) {
                OxygenLog.e("search", "SearXNG failed", e)
                throw OxygenError.SearchFailed(e.message ?: "search failed")
            }
        }
    }

    fun parse(body: String, count: Int): SearchResponse {
        val root = JSONObject(body)
        val arr = root.optJSONArray("results") ?: return SearchResponse(true, emptyList())
        val now = System.currentTimeMillis()
        val seen = HashSet<String>()
        val hits = ArrayList<SearchHit>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("url")
            if (url.isBlank() || !seen.add(url)) continue
            val title = o.optString("title")
            val snippet = PromptInjectionDefense.stripInstructionRole(o.optString("content"))
            hits.add(
                SearchHit(
                    title = title,
                    url = url,
                    domain = runCatching { URI(url).host ?: "" }.getOrDefault(""),
                    snippet = snippet,
                    source = "searxng",
                    retrievedAt = now,
                ),
            )
            if (hits.size >= count) break
        }
        return SearchResponse(true, hits)
    }
}

class DisabledSearchProvider : SearchProvider {
    override val id: String = "disabled"
    override suspend fun search(request: SearchRequest): SearchResponse =
        SearchResponse(false, emptyList(), "Web search is disabled")
}

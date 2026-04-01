package app.aaps.ui.search

import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.ui.search.SearchableItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of a wiki search operation.
 */
sealed class WikiSearchResult {

    data class Success(val entries: List<SearchIndexEntry>) : WikiSearchResult()
    data object Offline : WikiSearchResult()
}

/**
 * Repository for searching AndroidAPS documentation on ReadTheDocs.
 * Uses the RTD Search API v3 to query the wiki and returns results
 * as [SearchIndexEntry] items with [SearchCategory.WIKI].
 */
@Singleton
class WikiSearchRepository @Inject constructor(
    private val receiverStatusStore: ReceiverStatusStore
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Search the AndroidAPS wiki for the given query.
     *
     * @param query Search query string (minimum 3 characters)
     * @return [WikiSearchResult.Offline] if no connectivity,
     *         [WikiSearchResult.Success] with results (possibly empty) otherwise
     */
    suspend fun search(query: String): WikiSearchResult {
        if (query.length < MIN_QUERY_LENGTH) return WikiSearchResult.Success(emptyList())
        if (!receiverStatusStore.isConnected) return WikiSearchResult.Offline

        return withContext(Dispatchers.IO) {
            try {
                val url = API_URL.toHttpUrl().newBuilder()
                    .addQueryParameter("q", "project:$PROJECT_SLUG $query")
                    .addQueryParameter("page_size", PAGE_SIZE.toString())
                    .build()

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) return@withContext WikiSearchResult.Success(emptyList())

                val body = response.body.string()
                WikiSearchResult.Success(parseResults(JSONObject(body)))
            } catch (_: Exception) {
                WikiSearchResult.Success(emptyList())
            }
        }
    }

    private fun parseResults(json: JSONObject): List<SearchIndexEntry> {
        val results = json.optJSONArray("results") ?: return emptyList()
        val entries = mutableListOf<SearchIndexEntry>()

        for (i in 0 until results.length()) {
            val result = results.getJSONObject(i)
            val domain = result.optString("domain", "")
            val path = result.optString("path", "")
            val pageTitle = result.optString("title", "")
            val blocks = result.optJSONArray("blocks")

            if (blocks != null && blocks.length() > 0) {
                // Create one entry per section block for more granular results
                for (j in 0 until blocks.length()) {
                    val block = blocks.getJSONObject(j)
                    val sectionId = block.optString("id", "")
                    val sectionTitle = block.optString("title", "")
                    val url = buildUrl(domain, path, sectionId)
                    val title = sectionTitle.ifBlank { pageTitle }
                    val snippet = extractSnippet(block)

                    entries += SearchIndexEntry(
                        item = SearchableItem.Wiki(
                            url = url,
                            wikiTitle = title,
                            snippet = snippet
                        ),
                        localizedTitle = title,
                        englishTitle = title,
                        localizedSummary = snippet,
                        englishSummary = snippet,
                        category = SearchCategory.WIKI
                    )
                }
            } else {
                // No blocks — use page-level result
                val url = buildUrl(domain, path, anchorId = "")
                entries += SearchIndexEntry(
                    item = SearchableItem.Wiki(
                        url = url,
                        wikiTitle = pageTitle,
                        snippet = null
                    ),
                    localizedTitle = pageTitle,
                    englishTitle = pageTitle,
                    localizedSummary = null,
                    englishSummary = null,
                    category = SearchCategory.WIKI
                )
            }
        }

        return entries
    }

    private fun buildUrl(domain: String, path: String, anchorId: String): String {
        val base = "$domain$path"
        return if (anchorId.isNotBlank()) "$base#$anchorId" else base
    }

    private fun extractSnippet(block: JSONObject): String? {
        val highlights = block.optJSONObject("highlights") ?: return null
        val contentHighlights = highlights.optJSONArray("content")
        if (contentHighlights != null && contentHighlights.length() > 0) {
            val raw = contentHighlights.getString(0)
            return stripHtmlTags(raw).take(MAX_SNIPPET_LENGTH)
        }
        // Fall back to plain content
        val content = block.optString("content", "")
        return if (content.isNotBlank()) content.take(MAX_SNIPPET_LENGTH) else null
    }

    private fun stripHtmlTags(html: String): String =
        html.replace(Regex("<[^>]*>"), "")

    companion object {

        private const val API_URL = "https://app.readthedocs.org/api/v3/search/"
        private const val PROJECT_SLUG = "androidaps"
        private const val PAGE_SIZE = 10
        private const val MIN_QUERY_LENGTH = 3
        private const val MAX_SNIPPET_LENGTH = 150
    }
}

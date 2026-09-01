package app.aaps.ui.search

import app.aaps.core.interfaces.concurrent.aapsIoDispatcher
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.ui.search.SearchableItem
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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
 *
 * Ktor rather than OkHttp, and kotlinx rather than `org.json`, so the search works on every target.
 * The response is walked as [JsonElement] instead of being mapped to serializable classes: this is a
 * third-party API that may grow fields at any time, and the original code was written to tolerate
 * that. Nothing here is a stored or transmitted format of ours.
 */
@SingleIn(AppScope::class)
class WikiSearchRepository @Inject constructor(
    private val receiverStatusStore: ReceiverStatusStore
) {

    private val client = HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = TIMEOUT_MS
            requestTimeoutMillis = TIMEOUT_MS
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

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

        return withContext(aapsIoDispatcher) {
            try {
                val response = client.get(API_URL) {
                    parameter("q", "project:$PROJECT_SLUG $query")
                    parameter("page_size", PAGE_SIZE)
                }
                if (!response.status.isSuccess()) return@withContext WikiSearchResult.Success(emptyList())

                val root = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
                    ?: return@withContext WikiSearchResult.Success(emptyList())
                WikiSearchResult.Success(parseResults(root))
            } catch (_: Exception) {
                // Any failure is reported as "nothing found" rather than an error: the wiki is an
                // extra on top of the local index, and a search box is a bad place for a network
                // complaint. Same behaviour as the OkHttp version this replaced.
                WikiSearchResult.Success(emptyList())
            }
        }
    }

    private fun parseResults(root: JsonObject): List<SearchIndexEntry> {
        val results = root["results"] as? JsonArray ?: return emptyList()
        val entries = mutableListOf<SearchIndexEntry>()

        for (element in results) {
            val result = element as? JsonObject ?: continue
            val domain = result.str("domain")
            val path = result.str("path")
            val pageTitle = result.str("title")
            val blocks = result["blocks"] as? JsonArray

            if (blocks != null && blocks.isNotEmpty()) {
                // Create one entry per section block for more granular results
                for (blockElement in blocks) {
                    val block = blockElement as? JsonObject ?: continue
                    val url = buildUrl(domain, path, block.str("id"))
                    val title = block.str("title").ifBlank { pageTitle }
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

    /** Reads a string field, treating a missing field, a null and a non-string alike as "". */
    private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull ?: ""

    private fun buildUrl(domain: String, path: String, anchorId: String): String {
        val base = "$domain$path"
        return if (anchorId.isNotBlank()) "$base#$anchorId" else base
    }

    private fun extractSnippet(block: JsonObject): String? {
        val highlights = block["highlights"] as? JsonObject ?: return null
        val contentHighlights = highlights["content"] as? JsonArray
        if (contentHighlights != null && contentHighlights.isNotEmpty()) {
            val raw = (contentHighlights[0] as? JsonPrimitive)?.contentOrNull ?: ""
            return stripHtmlTags(raw).take(MAX_SNIPPET_LENGTH)
        }
        // Fall back to plain content
        val content = block.str("content")
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
        private const val TIMEOUT_MS = 10_000L
    }
}

package app.aaps.ui.search

import app.aaps.core.ui.search.SearchableItem
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.concurrent.Volatile

/**
 * Searches the AAPS wiki on Read the Docs, in the language the app runs in and in English.
 *
 * Every translated wiki is a Read the Docs project of its own (`androidaps-de`, `androidaps-zh-tw`, ...).
 * Which ones exist is asked from Read the Docs, not listed here, so a new wiki language works without an
 * app update. Only the translations the wiki owners linked to `androidaps` are used: project names are
 * open to anyone, and a guessed `androidaps-xx` could be a stranger's project.
 *
 * Both searches run at the same time. Pages in the app language come first, then the English pages that
 * are not already there. That is the rule the in-app search follows too: the translated text first,
 * English as a fallback for a user who types an English term.
 *
 * Responses are walked as JSON elements instead of being mapped to serializable classes: this is a
 * third-party API that may grow fields at any time. Nothing here is a stored or transmitted format of ours.
 *
 * @param appLanguage the language the app shows its text in, as a BCP 47 tag
 */
internal class WikiSearchClient(
    private val http: HttpClient,
    private val appLanguage: () -> String?
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** The linked translations, once read. A failed read is not kept, so the next search asks again. */
    @Volatile private var translations: List<WikiTranslation>? = null

    suspend fun search(query: String): List<SearchIndexEntry> = coroutineScope {
        val english = async { searchProject(ENGLISH_PROJECT, query) }
        val own = translationFor(appLanguage(), translations())
        val local = if (own != null) searchProject(own, query) else emptyList()
        // The same page in English would only repeat what the user already has in their language.
        val shown = local.mapTo(HashSet()) { it.key }
        (local + english.await().filterNot { it.key in shown }).flatMap { it.entries }
    }

    private suspend fun translations(): List<WikiTranslation> =
        translations ?: readTranslations()?.also { translations = it }.orEmpty()

    /**
     * Reads the linked translations from the data the wiki's own language menu uses.
     *
     * Not from `api/v3/projects/androidaps/translations/`: that answers only a few requests a minute
     * per address and then refuses with 429 for a minute. Phones on one mobile network often share an
     * address, so its users would silently get English only. This one serves every wiki page view.
     */
    private suspend fun readTranslations(): List<WikiTranslation>? =
        request {
            val response = http.get(TRANSLATIONS_URL) {
                parameter("api-version", 1)
                parameter("project-slug", ENGLISH_PROJECT)
                parameter("version-slug", WIKI_VERSION)
            }
            if (!response.status.isSuccess()) return@request null
            val projects = (json.parseToJsonElement(response.bodyAsText()) as? JsonObject)?.get("projects") as? JsonObject
            val results = projects?.get("translations") as? JsonArray ?: return@request null
            results.mapNotNull { element ->
                val project = element as? JsonObject ?: return@mapNotNull null
                val language = (project["language"] as? JsonObject)?.str("code")
                val slug = project.str("slug")
                if (language.isNullOrBlank() || slug.isBlank()) null
                else WikiTranslation(slug = slug, languageCode = language.lowercase().replace('_', '-'))
            }
        }

    private suspend fun searchProject(project: String, query: String): List<WikiPage> =
        request {
            val response = http.get(SEARCH_URL) {
                parameter("q", "project:$project $query")
                parameter("page_size", PAGE_SIZE)
            }
            if (!response.status.isSuccess()) return@request null
            (json.parseToJsonElement(response.bodyAsText()) as? JsonObject)?.let { parseResults(it) }
        }.orEmpty()

    /**
     * Runs one request. Any failure is "nothing found" rather than an error: the wiki is an extra on
     * top of the local index, and a search box is a bad place for a network complaint.
     */
    private suspend fun <T> request(block: suspend () -> T?): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    private fun parseResults(root: JsonObject): List<WikiPage> {
        val results = root["results"] as? JsonArray ?: return emptyList()
        return results.mapNotNull { element ->
            val result = element as? JsonObject ?: return@mapNotNull null
            val domain = result.str("domain")
            val path = result.str("path")
            val pageTitle = result.str("title")
            val blocks = (result["blocks"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()

            val entries =
                if (blocks.isNotEmpty()) {
                    // One entry per section block for more granular results
                    blocks.map { block ->
                        val title = block.str("title").ifBlank { pageTitle }
                        entry(url = buildUrl(domain, path, block.str("id")), title = title, snippet = extractSnippet(block))
                    }
                } else {
                    // No blocks: use the page-level result
                    listOf(entry(url = buildUrl(domain, path, anchorId = ""), title = pageTitle, snippet = null))
                }
            WikiPage(key = pageKey(path), entries = entries)
        }
    }

    private fun entry(url: String, title: String, snippet: String?) =
        SearchIndexEntry(
            item = SearchableItem.Wiki(url = url, wikiTitle = title, snippet = snippet),
            localizedTitle = title,
            englishTitle = title,
            localizedSummary = snippet,
            englishSummary = snippet,
            category = SearchCategory.WIKI
        )

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

    /** A translated wiki: its Read the Docs project and its language, like `zh-tw`. */
    private class WikiTranslation(val slug: String, val languageCode: String)

    /** The entries of one result page, with a key that finds the same page in another language. */
    private class WikiPage(val key: String, val entries: List<SearchIndexEntry>)

    companion object {

        private const val SEARCH_URL = "https://app.readthedocs.org/api/v3/search/"
        private const val ENGLISH_PROJECT = "androidaps"
        private const val WIKI_VERSION = "latest"
        private const val TRANSLATIONS_URL = "https://androidaps.readthedocs.io/_/addons/"
        private const val PAGE_SIZE = 10
        private const val MAX_SNIPPET_LENGTH = 150

        /**
         * The project of the wiki translation for [languageTag], or null when there is none and only
         * English is searched.
         *
         * Language and region first, so `zh-Hant-TW` finds `zh-tw` and not `zh-cn`. Then the language
         * alone, so `de-AT` finds `de`. A stored value like `zh_TW` uses `_`, which is read the same way.
         */
        private fun translationFor(languageTag: String?, translations: List<WikiTranslation>): String? {
            val parts = languageTag?.lowercase()?.split('-', '_') ?: return null
            val language = parts.first()
            // The region is the two-letter part. A four-letter part is a script, like Hant.
            val region = parts.drop(1).firstOrNull { it.length == 2 }
            return translations.firstOrNull { region != null && it.languageCode == "$language-$region" }?.slug
                ?: translations.firstOrNull { it.languageCode == language }?.slug
        }

        /** The page path without its "/<language>/<version>/" start: `/de/latest/A/B.html` is `A/B.html`. */
        private fun pageKey(path: String): String = path.trimStart('/').split('/').drop(2).joinToString("/")
    }
}

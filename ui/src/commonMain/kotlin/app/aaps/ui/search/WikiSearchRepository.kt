package app.aaps.ui.search

import androidx.compose.ui.text.intl.Locale
import app.aaps.core.interfaces.concurrent.aapsIoDispatcher
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.TextRefValueRegistry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.withContext

/**
 * Result of a wiki search operation.
 */
sealed class WikiSearchResult {

    data class Success(val entries: List<SearchIndexEntry>) : WikiSearchResult()
    data object Offline : WikiSearchResult()
}

/**
 * Repository for searching AAPS documentation on ReadTheDocs.
 * Searches the wiki in the app language and in English (see [WikiSearchClient]) and returns results
 * as [SearchIndexEntry] items with [SearchCategory.WIKI].
 *
 * Ktor rather than OkHttp, and kotlinx rather than `org.json`, so the search works on every target.
 */
@SingleIn(AppScope::class)
@Inject
class WikiSearchRepository(
    private val receiverStatusStore: ReceiverStatusStore
) {

    private val client = WikiSearchClient(
        http = HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = TIMEOUT_MS
                requestTimeoutMillis = TIMEOUT_MS
            }
        },
        appLanguage = ::appLanguage
    )

    /**
     * Search the AAPS wiki for the given query.
     *
     * @param query Search query string (minimum 3 characters)
     * @return [WikiSearchResult.Offline] if no connectivity,
     *         [WikiSearchResult.Success] with results (possibly empty) otherwise
     */
    suspend fun search(query: String): WikiSearchResult {
        if (query.length < MIN_QUERY_LENGTH) return WikiSearchResult.Success(emptyList())
        if (!receiverStatusStore.isConnected) return WikiSearchResult.Offline

        return withContext(aapsIoDispatcher) { WikiSearchResult.Success(client.search(query)) }
    }

    /**
     * The language the app shows its text in, as a BCP 47 tag.
     *
     * iOS and desktop keep it in [TextRefValueRegistry.locale]. Android does not set that: its text
     * comes from `Resources`, and `LocaleHelper` moves the default locale to the chosen language, so
     * [Locale.current] is the app language there. It is also the phone language when none was chosen.
     */
    private fun appLanguage(): String = TextRefValueRegistry.locale ?: Locale.current.toLanguageTag()

    companion object {

        private const val MIN_QUERY_LENGTH = 3
        private const val TIMEOUT_MS = 10_000L
    }
}

package app.aaps.ui.search

import app.aaps.core.ui.search.SearchableItem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The wiki search against scripted Read the Docs answers.
 *
 * What is easy to get wrong here is not the HTTP call but the choices around it: which wiki belongs to
 * the app language, that no project name is guessed, and how the two result lists are merged.
 */
class WikiSearchClientTest {

    /** The project of every search request, in the order they were sent. */
    private val searched = MutableStateFlow(emptyList<String>())

    /** The query string of every translations list request. */
    private val translationRequests = MutableStateFlow(emptyList<String>())

    private var translationsStatus = HttpStatusCode.OK

    /** What each project answers. A project not listed finds nothing; a null answer is a server error. */
    private val answers = mutableMapOf<String, String?>()

    private fun client(language: String?): WikiSearchClient {
        val engine = MockEngine { request ->
            val url = request.url
            if (url.encodedPath.endsWith("/_/addons/")) {
                translationRequests.update { it + url.encodedQuery }
                respond(ByteReadChannel(TRANSLATIONS), translationsStatus, JSON)
            } else {
                val project = url.parameters["q"].orEmpty().substringBefore(' ').removePrefix("project:")
                searched.update { it + project }
                val answer = answers.getOrElse(project) { results() }
                if (answer == null) respond(ByteReadChannel("{}"), HttpStatusCode.InternalServerError, JSON)
                else respond(ByteReadChannel(answer), HttpStatusCode.OK, JSON)
            }
        }
        return WikiSearchClient(HttpClient(engine), appLanguage = { language })
    }

    private fun List<SearchIndexEntry>.urls() = map { (it.item as SearchableItem.Wiki).url }

    @Test
    fun `pages in the app language come first and then the english pages it does not have`() = runTest {
        answers["androidaps-de"] = results(page("de", "ExtendedCarbs.html"), page("de", "CobCalculation.html"))
        answers["androidaps"] = results(page("en", "ExtendedCarbs.html"), page("en", "Autosens.html"))

        val urls = client("de-DE").search("carbs").urls()

        assertEquals(listOf("$DOMAIN/de/latest/ExtendedCarbs.html", "$DOMAIN/de/latest/CobCalculation.html", "$DOMAIN/en/latest/Autosens.html"), urls)
        assertEquals(setOf("androidaps-de", "androidaps"), searched.value.toSet())
    }

    @Test
    fun `the wiki is found by language and region`() = runTest {
        val expected = mapOf(
            "zh-Hant-TW" to "androidaps-zh-tw",
            "zh-CN" to "androidaps-zh-cn",
            // The stored form of the language setting, with an underscore
            "zh_TW" to "androidaps-zh-tw",
            "de-AT" to "androidaps-de",
            "he-IL" to "androidaps-he",
            "cs" to "androidaps-cs"
        )
        for ((language, project) in expected) {
            searched.value = emptyList()
            client(language).search("carbs")
            assertEquals(setOf(project, "androidaps"), searched.value.toSet(), language)
        }
    }

    @Test
    fun `a language without a wiki searches english only and guesses no project name`() = runTest {
        for (language in listOf("sk-SK", "it", "en-US", null)) {
            searched.value = emptyList()
            client(language).search("carbs")
            assertEquals(listOf("androidaps"), searched.value, language)
        }
    }

    @Test
    fun `the translations list is read once for the english wiki`() = runTest {
        val client = client("de")

        client.search("carbs")
        client.search("bolus")

        assertEquals(listOf("api-version=1&project-slug=androidaps&version-slug=latest"), translationRequests.value)
    }

    @Test
    fun `a failed translations list still finds english and is asked again next time`() = runTest {
        answers["androidaps-de"] = results(page("de", "ExtendedCarbs.html"))
        answers["androidaps"] = results(page("en", "Autosens.html"))
        val client = client("de")

        translationsStatus = HttpStatusCode.InternalServerError
        assertEquals(listOf("$DOMAIN/en/latest/Autosens.html"), client.search("carbs").urls())

        translationsStatus = HttpStatusCode.OK
        assertEquals(listOf("$DOMAIN/de/latest/ExtendedCarbs.html", "$DOMAIN/en/latest/Autosens.html"), client.search("carbs").urls())
        assertEquals(2, translationRequests.value.size)
    }

    @Test
    fun `a failed search in one language keeps the results of the other`() = runTest {
        answers["androidaps-de"] = null
        answers["androidaps"] = results(page("en", "Autosens.html"))

        assertEquals(listOf("$DOMAIN/en/latest/Autosens.html"), client("de").search("carbs").urls())
    }

    @Test
    fun `every section of a page is its own result linked by its anchor`() = runTest {
        answers["androidaps"] = results(
            """{"domain":"$DOMAIN","path":"/en/latest/Autosens.html","title":"Autosens","blocks":[
                {"id":"how-it-works","title":"How it works","highlights":{"content":["uses <span>carbs</span> too"]}},
                {"id":"settings","title":"","highlights":{"content":[]},"content":"plain text"}]}"""
        )

        val entries = client("en").search("carbs")

        assertEquals(listOf("$DOMAIN/en/latest/Autosens.html#how-it-works", "$DOMAIN/en/latest/Autosens.html#settings"), entries.urls())
        assertEquals(listOf("How it works", "Autosens"), entries.map { it.localizedTitle })
        assertEquals(listOf("uses carbs too", "plain text"), entries.map { it.localizedSummary })
    }

    private fun page(language: String, path: String) =
        """{"domain":"$DOMAIN","path":"/$language/latest/$path","title":"$path","blocks":[]}"""

    private fun results(vararg pages: String) = """{"results":[${pages.joinToString(",")}]}"""

    private companion object {

        const val DOMAIN = "https://androidaps.readthedocs.io"
        val JSON = headersOf(HttpHeaders.ContentType, "application/json")
        val TRANSLATIONS = """{"api_version":"1","projects":{"current":{"slug":"androidaps"},"translations":[
            {"slug":"androidaps-cs","language":{"code":"cs"}},
            {"slug":"androidaps-de","language":{"code":"de"}},
            {"slug":"androidaps-he","language":{"code":"he"}},
            {"slug":"androidaps-zh-cn","language":{"code":"zh-cn"}},
            {"slug":"androidaps-zh-tw","language":{"code":"zh-tw"}}]}}"""
    }
}

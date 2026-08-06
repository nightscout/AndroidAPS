package app.aaps.core.nssdk.networking

import app.aaps.core.nssdk.NSAndroidClientImpl
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Pins the exact URL every endpoint puts on the wire.
 *
 * This is the highest value test in the module, because a wrong URL does not fail - Nightscout
 * answers **200 with the wrong rows**. Two of the details being pinned here are invisible unless you
 * read the Retrofit annotations very carefully:
 *
 * 1. **Static query pairs live inside the `@GET` string**, not in `@Query` parameters. For example
 *    `v3/profile?sort${'$'}desc=date&limit=1`. A port that rebuilds the URL from the method parameters
 *    silently drops them - and losing `limit=1` there makes `LoadProfileStoreWorker` pick the
 *    **wrong profile store**, which means different basal, ISF and IC, with a normal looking log line.
 * 2. **`${'$'}` must stay a literal `${'$'}`.** `date${'$'}gt`, `created_at${'$'}gt` and `sort${'$'}desc` are Nightscout API v3
 *    operators, and Retrofit is told `encoded = true` so they pass through untouched. Most URL
 *    builders percent-encode `${'$'}` to `%24` by default, which changes the query rather than failing.
 *
 * The test drives a **real HTTP server on localhost** rather than a library specific fake, so this
 * same file runs unchanged against Retrofit today and against Ktor after the port. That is the whole
 * point: a fake that only exists after the swap could never prove anything about the behaviour
 * before it.
 *
 * Only the request line is asserted. Whether the response parses is irrelevant here, so each call is
 * wrapped in `runCatching`.
 */
class NsSdkUrlContractTest {

    private lateinit var server: MockWebServer
    private lateinit var client: NSAndroidClientImpl

    /**
     * A plain temp directory, not JUnit's `@TempDir`.
     *
     * Nothing closes the OkHttp client (there is no `close()` on `NSAndroidClient`), so its `Cache`
     * keeps the `journal` file open and `@TempDir` fails the test on Windows while trying to delete
     * it - the assertions pass, the cleanup does not. Deleting best effort here keeps the failure
     * signal honest. When the disk cache goes away with the Ktor port, this can become `@TempDir`.
     */
    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = NSAndroidClientImpl(
            // Carries a scheme, so it is used as it stands and points at the local server.
            baseUrl = server.url("/").toString().trimEnd('/'),
            accessToken = "token",
            logging = false,
            logger = { },
            dispatcher = Dispatchers.Unconfined
        )
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    /** Runs [call], ignores how it ends, and returns the request line the server saw. */
    private suspend fun pathOf(body: String = """{"result":[]}""", call: suspend () -> Unit): String {
        server.enqueue(MockResponse.Builder().code(200).body(body).build())
        runCatching { call() }
        return server.takeRequest().target
    }

    // ---------------------------------------------------------------- entries

    @Test
    fun `entries endpoints keep their static query parts`() = runTest {
        assertThat(pathOf { client.getSgvs() })
            .isEqualTo("/api/v3/entries?sort\$desc=date&type=sgv")

        assertThat(pathOf { client.getSgvsNewerThan(1700000000000, 250) })
            .isEqualTo("/api/v3/entries?sort=date&date\$gt=1700000000000&limit=250")

        assertThat(pathOf { client.getSgvsModifiedSince(1700000000000, 250) })
            .isEqualTo("/api/v3/entries/history/1700000000000?limit=250")
    }

    /** `getSgvs()` deliberately sends no `limit` - the server default applies. */
    @Test
    fun `getSgvs sends no limit`() = runTest {
        assertThat(pathOf { client.getSgvs() }).doesNotContain("limit")
    }

    // ---------------------------------------------------------------- treatments

    @Test
    fun `treatments endpoints keep their static query parts`() = runTest {
        assertThat(pathOf { client.getTreatmentsNewerThan("2024-01-01T00:00:00.000Z", 250) })
            .isEqualTo("/api/v3/treatments?sort=created_at&created_at\$gt=2024-01-01T00:00:00.000Z&limit=250")

        assertThat(pathOf { client.getTreatmentsModifiedSince(1700000000000, 250) })
            .isEqualTo("/api/v3/treatments/history/1700000000000?limit=250")
    }

    // ---------------------------------------------------------------- profile

    /**
     * The dangerous one. Without `sort${'$'}desc=date&limit=1` the server returns an unordered list and
     * `LoadProfileStoreWorker` takes `profiles[profiles.size - 1]` from it.
     */
    @Test
    fun `getLastProfileStore keeps sort and limit`() = runTest {
        assertThat(pathOf { client.getLastProfileStore() })
            .isEqualTo("/api/v3/profile?sort\$desc=date&limit=1")
    }

    /** `limit=10` comes from a default on the interface and is invisible at the call site. */
    @Test
    fun `getProfileModifiedSince sends the interface default limit`() = runTest {
        assertThat(pathOf { client.getProfileModifiedSince(1700000000000) })
            .isEqualTo("/api/v3/profile/history/1700000000000?limit=10")
    }

    // ---------------------------------------------------------------- settings

    @Test
    fun `settings read endpoints`() = runTest {
        assertThat(pathOf(body = """{"result":{}}""") { client.getSettings("aaps") })
            .isEqualTo("/api/v3/settings/aaps")

        assertThat(pathOf { client.getSettingsModifiedSince(1700000000000, 100) })
            .isEqualTo("/api/v3/settings/history/1700000000000?limit=100")

        assertThat(pathOf { client.searchSettings(100) })
            .isEqualTo("/api/v3/settings?limit=100")
    }

    /**
     * Absence of `permanent` is what makes a delete a soft delete (a tombstone). Sending
     * `permanent=false` would be a different operation, so the parameter must be missing entirely.
     */
    @Test
    fun `a soft delete sends no permanent parameter`() = runTest {
        val path = pathOf(body = """{}""") { client.deleteSettings("aaps_x") }

        assertThat(path).isEqualTo("/api/v3/settings/aaps_x")
        assertThat(path).doesNotContain("permanent")
    }

    @Test
    fun `a permanent delete sends permanent true`() = runTest {
        assertThat(pathOf(body = """{}""") { client.deleteSettingsPermanent("aaps_x") })
            .isEqualTo("/api/v3/settings/aaps_x?permanent=true")
    }

    /**
     * How a path parameter is encoded. Identifiers come from Nightscout and are normally ids with no
     * special characters, so this is a latent edge rather than a live case - but it decides whether a
     * odd identifier addresses one document or a different path entirely, so the port has to match it.
     */
    @Test
    fun `an identifier is encoded into the path`() = runTest {
        val path = pathOf(body = """{"result":{}}""") { client.getSettings("a/b c") }

        assertThat(path).startsWith("/api/v3/settings/")
        assertThat(path.removePrefix("/api/v3/settings/")).isEqualTo("a%2Fb%20c")
    }

    // ---------------------------------------------------------------- base url shapes

    /** A sub-path install must keep its sub-path on the data endpoints. */
    @Test
    fun `a sub-path install keeps the sub-path`() {
        assertThat(NsUrl.toBaseUrl("host.com/ns")).isEqualTo("https://host.com/ns/api/")
        assertThat(NsUrl.toBaseUrl("host.com")).isEqualTo("https://host.com/api/")
        assertThat(NsUrl.toBaseUrl("host.com/")).isEqualTo("https://host.com/api/")
    }

    /** An explicit scheme is honoured - this is what lets the tests above reach localhost. */
    @Test
    fun `an explicit scheme is kept`() {
        assertThat(NsUrl.toBaseUrl("http://localhost:8080")).isEqualTo("http://localhost:8080/api/")
        assertThat(NsUrl.toBaseUrl("https://host.com")).isEqualTo("https://host.com/api/")
    }
}

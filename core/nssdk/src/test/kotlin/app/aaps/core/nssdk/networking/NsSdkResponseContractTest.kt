package app.aaps.core.nssdk.networking

import android.content.Context
import app.aaps.core.nssdk.NSAndroidClientImpl
import app.aaps.core.nssdk.exceptions.InvalidFormatNightscoutException
import app.aaps.core.nssdk.localmodel.entry.Direction
import app.aaps.core.nssdk.localmodel.entry.NSSgvV3
import app.aaps.core.nssdk.localmodel.entry.NsUnits
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File
import java.nio.file.Files

/**
 * Pins how a response is turned into values: the **ETag**, which drives the sync cursor, and the
 * **`{"result": ...}` envelope**, which is not applied consistently across endpoints.
 *
 * The envelope is the nastier of the two. Some endpoints answer with the document directly and some
 * wrap it, and there is no rule - it has to be copied per endpoint. Because `nsSdkJson` sets
 * `ignoreUnknownKeys = true`, guessing wrong does **not** fail: the wrapper is treated as an unknown
 * key, every field comes back null, and the caller sees a successful response full of nothing. For a
 * create that means the returned `identifier` is null, and `NSClientV3Plugin` then re-uploads that
 * record as a brand new document on every sync, forever.
 *
 * The ETag matters because `lastServerModified` is what advances the paging cursor. A null there
 * makes the worker skip the cursor update, so the same window is fetched again next time.
 */
class NsSdkResponseContractTest {

    private lateinit var server: MockWebServer
    private lateinit var client: NSAndroidClientImpl
    private lateinit var cacheDir: File

    @BeforeEach
    fun setUp() {
        cacheDir = Files.createTempDirectory("nssdk-response-cache").toFile()
        server = MockWebServer()
        server.start()
        val context = mock<Context> { on { getCacheDir() } doReturn cacheDir }
        client = NSAndroidClientImpl(
            baseUrl = server.url("/").toString().trimEnd('/'),
            accessToken = "token",
            context = context,
            logging = false,
            logger = { },
            dispatcher = Dispatchers.Unconfined
        )
    }

    @AfterEach
    fun tearDown() {
        server.close()
        cacheDir.deleteRecursively()
    }

    private fun sgv() = NSSgvV3(
        date = 1785992179555, device = "test", identifier = "id-1", utcOffset = 0,
        isValid = true, sgv = 120.0, units = NsUnits.MG_DL, direction = Direction.FLAT,
        noise = null, filtered = null, unfiltered = null
    )

    // ================================================================ ETag

    /**
     * Nightscout sends a weak ETag, `W/"<millis>"`. The client strips the first three characters and
     * the closing quote with `substring(3, length - 1)` and parses what is left as a Long.
     */
    @Test
    fun `a weak ETag becomes lastServerModified`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200)
                .addHeader("ETag", """W/"1785992179555"""")
                .body("""{"result":[]}""").build()
        )

        val response = client.getSgvsModifiedSince(1700000000000, 100)

        assertThat(response.lastServerModified).isEqualTo(1785992179555L)
    }

    /** No ETag means no cursor update. The worker leaves `lastLoadedSrvModified` where it was. */
    @Test
    fun `an absent ETag gives a null lastServerModified`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"result":[]}""").build())

        val response = client.getSgvsModifiedSince(1700000000000, 100)

        assertThat(response.lastServerModified).isNull()
    }

    /**
     * The parse is positional, not a pattern match, so a header that is not `W/"..."` breaks it.
     * Recorded as it is: the exception escapes, and because it is not one of the three excluded
     * types it is retried first - four requests, then it surfaces.
     *
     * Real Nightscout always sends the weak form, so this is a latent edge rather than a live bug.
     * It is pinned so the port neither hides it nor makes it worse.
     */
    @Test
    fun `a malformed ETag throws after the retries - current behaviour, pinned`() = runTest {
        repeat(8) {
            server.enqueue(
                MockResponse.Builder().code(200)
                    .addHeader("ETag", "not-an-etag")
                    .body("""{"result":[]}""").build()
            )
        }

        assertThrows<Exception> { client.getSgvsModifiedSince(1700000000000, 100) }
        assertThat(server.requestCount).isEqualTo(4)
    }

    // ================================================================ envelope

    /**
     * `createEntry` answers with the create/update document **directly**, no `result` wrapper.
     * `identifier` must survive - without it the reading is uploaded again as a new document forever.
     */
    @Test
    fun `entries create reads a bare body`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(201)
                .body("""{"identifier":"abc123","isDeduplication":false,"lastModified":1785992179555}""").build()
        )

        val response = client.createSgv(sgv())

        assertThat(response.identifier).isEqualTo("abc123")
        assertThat(response.lastModified).isEqualTo(1785992179555L)
    }

    /**
     * The failure mode, stated explicitly. Feeding the WRAPPED shape to that same bare endpoint does
     * not throw - `ignoreUnknownKeys` swallows `result` and every field comes back null.
     *
     * This is what a wrong envelope choice looks like during the port: a green build, a 201, and a
     * record that is re-uploaded on every sync.
     */
    @Test
    fun `a wrapped body on a bare endpoint yields nulls instead of failing`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(201)
                .body("""{"result":{"identifier":"abc123","lastModified":1785992179555}}""").build()
        )

        val response = client.createSgv(sgv())

        assertThat(response.response).isEqualTo(201)   // looks fine
        assertThat(response.identifier).isNull()       // but the identifier is gone
    }

    /**
     * `updateSvg` **ignores the response body completely** - it always returns `identifier = null`
     * and reports only the status. So the envelope shape does not matter for this endpoint, and a
     * port must not "helpfully" start reading the identifier here: callers rely on getting null.
     */
    @Test
    fun `entries update reports only the status and never an identifier`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200)
                .body("""{"result":{"identifier":"abc123","lastModified":1785992179555}}""").build()
        )

        val response = client.updateSvg(sgv())

        assertThat(response.response).isEqualTo(200)
        assertThat(response.identifier).isNull()
        assertThat(response.lastModified).isNull()
    }

    /** 404 on an update is a success too - the record is simply not there any more. */
    @Test
    fun `entries update treats 404 as success`() = runTest {
        server.enqueue(MockResponse.Builder().code(404).body("""{"status":404}""").build())

        val response = client.updateSvg(sgv())

        assertThat(response.response).isEqualTo(404)
        assertThat(server.requestCount).isEqualTo(1)
    }

    /**
     * A record with no identifier cannot be updated, and that is refused **before any request is
     * made**. `InvalidFormatNightscoutException` is one of the three types excluded from retry, so
     * it surfaces immediately rather than after four attempts.
     */
    @Test
    fun `updating a record without an identifier throws before any request`() = runTest {
        val noIdentifier = NSSgvV3(
            date = 1785992179555, device = "test", identifier = null, utcOffset = 0,
            isValid = true, sgv = 120.0, units = NsUnits.MG_DL, direction = Direction.FLAT,
            noise = null, filtered = null, unfiltered = null
        )

        assertThrows<InvalidFormatNightscoutException> { client.updateSvg(noIdentifier) }
        assertThat(server.requestCount).isEqualTo(0)
    }

    /** Settings create is bare, like entries create. */
    @Test
    fun `settings create reads a bare body`() = runTest {
        server.enqueue(MockResponse.Builder().code(201).body("""{"identifier":"aaps"}""").build())

        val response = client.createSettings(JsonObject(mapOf("a" to JsonPrimitive(1))))

        assertThat(response.identifier).isEqualTo("aaps")
    }

    /** Settings read is wrapped, and the document inside is schema-less. */
    @Test
    fun `settings read unwraps the result`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200)
                .body("""{"result":{"identifier":"aaps","runningConfig":{"pump":"Dana"}}}""").build()
        )

        val response = client.getSettings("aaps")

        assertThat(response.values).isNotNull()
        assertThat(response.values!!.keys).containsAtLeast("identifier", "runningConfig")
    }

    /** List reads are wrapped, and an empty list must stay an empty list rather than becoming null. */
    @Test
    fun `an empty wrapped list stays empty`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"result":[]}""").build())

        val response = client.searchSettings(100)

        assertThat(response.values).isNotNull()
        assertThat(response.values).isEmpty()
    }

    /** A missing `result` on a wrapped endpoint gives an empty list, not a crash. */
    @Test
    fun `a wrapped endpoint with no result gives an empty list`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{}""").build())

        val response = client.searchSettings(100)

        assertThat(response.values).isEmpty()
    }
}

package app.aaps.core.nssdk.networking

import android.content.Context
import app.aaps.core.nssdk.NSAndroidClientImpl
import app.aaps.core.nssdk.localmodel.entry.Direction
import app.aaps.core.nssdk.localmodel.entry.NSSgvV3
import app.aaps.core.nssdk.localmodel.entry.NsUnits
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File
import java.nio.file.Files

/**
 * Pins the behaviour that depends on reading the **raw text of an error body**.
 *
 * Nightscout refuses a record whose `utcOffset` it does not like, with
 * `400 Bad or missing utcOffset field`. The client recognises that by a plain `contains` on the
 * body text and re-sends the record with `utcOffset = 0`
 * (`NSAndroidClientImpl.createSgv` / `createCalibration` / `createTreatment`).
 *
 * If a rewrite loses that text - by reading the body only on success, by consuming the stream before
 * it is inspected, or by decoding it into a typed model - the retry never happens. The 400 then
 * falls through to a plain `CreateUpdateResponse(400, ...)`, `NSClientV3Plugin` logs one `FAIL` line
 * and **advances the sync cursor anyway**, so that reading or treatment is never uploaded again.
 * No exception, no repeat attempt, one line in a log nobody reads.
 *
 * Ktor has no separate `errorBody()`. The body must be read once, up front, and reused - which is
 * exactly the trap this test exists to catch.
 */
class NsSdkErrorBodyContractTest {

    private lateinit var server: MockWebServer
    private lateinit var client: NSAndroidClientImpl
    private lateinit var cacheDir: File

    @BeforeEach
    fun setUp() {
        cacheDir = Files.createTempDirectory("nssdk-errorbody-cache").toFile()
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

    private fun sgv(utcOffset: Long) = NSSgvV3(
        date = 1785992179555,
        device = "test",
        identifier = null,
        utcOffset = utcOffset,
        isValid = true,
        sgv = 120.0,
        units = NsUnits.MG_DL,
        direction = Direction.FLAT,
        noise = null,
        filtered = null,
        unfiltered = null
    )

    // ---------------------------------------------------------------- the utcOffset fallback

    /**
     * The whole point. A 400 naming `utcOffset` must cause a second POST carrying `utcOffset: 0`,
     * and the caller must see the result of that second attempt, not the first failure.
     */
    @Test
    fun `a utcOffset rejection is retried once with utcOffset zero`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(400)
                .body("""{"status":400,"message":"Bad or missing utcOffset field"}""").build()
        )
        server.enqueue(MockResponse.Builder().code(201).body("""{"identifier":"abc123"}""").build())

        val response = client.createSgv(sgv(utcOffset = 120))

        assertThat(response.response).isEqualTo(201)
        assertThat(response.identifier).isEqualTo("abc123")
        assertThat(server.requestCount).isEqualTo(2)

        // the first attempt carried the real offset, the second carried zero
        val first = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        val second = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertThat(first["utcOffset"]?.jsonPrimitive?.content).isEqualTo("120")
        assertThat(second["utcOffset"]?.jsonPrimitive?.content).isEqualTo("0")
    }

    /**
     * The fallback must not loop. A record already at `utcOffset = 0` is not re-sent, because the
     * guard is `nsSgvV3.utcOffset != 0L`.
     */
    @Test
    fun `a record already at zero is not retried`() = runTest {
        repeat(4) {
            server.enqueue(
                MockResponse.Builder().code(400)
                    .body("""{"status":400,"message":"Bad or missing utcOffset field"}""").build()
            )
        }

        val response = client.createSgv(sgv(utcOffset = 0))

        assertThat(response.response).isEqualTo(400)
        assertThat(server.requestCount).isEqualTo(1)
    }

    /** A different 400 must NOT trigger the fallback - only the utcOffset wording does. */
    @Test
    fun `an unrelated 400 is returned as is`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(400).body("""{"status":400,"message":"something else"}""").build()
        )

        val response = client.createSgv(sgv(utcOffset = 120))

        assertThat(response.response).isEqualTo(400)
        assertThat(response.errorResponse).contains("something else")
        assertThat(server.requestCount).isEqualTo(1)
    }

    // ---------------------------------------------------------------- the other body match

    /**
     * `cannot be modified by the client` means the record exists with a field AAPS cannot change.
     * It is reported back with the body text and must not be retried.
     */
    @Test
    fun `a cannot-be-modified rejection is returned with its body and not retried`() = runTest {
        val body = """{"status":400,"message":"field cannot be modified by the client"}"""
        server.enqueue(MockResponse.Builder().code(400).body(body).build())

        val response = client.createSgv(sgv(utcOffset = 120))

        assertThat(response.response).isEqualTo(400)
        assertThat(response.errorResponse).isEqualTo(body)
        assertThat(server.requestCount).isEqualTo(1)
    }

    // ---------------------------------------------------------------- the body must survive at all

    /** The verbatim server body reaches the caller - not a summary, not null. */
    @Test
    fun `the error body is passed through verbatim`() = runTest {
        val body = """{"status":422,"message":"unprocessable","detail":["a","b"]}"""
        server.enqueue(MockResponse.Builder().code(422).body(body).build())

        val response = client.createSgv(sgv(utcOffset = 120))

        assertThat(response.errorResponse).isEqualTo(body)
    }

    /** `app` is stamped onto every uploaded entry - Nightscout uses it to attribute the record. */
    @Test
    fun `uploaded entries are stamped with the app name`() = runTest {
        server.enqueue(MockResponse.Builder().code(201).body("""{"identifier":"abc"}""").build())

        client.createSgv(sgv(utcOffset = 120))

        val sent = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertThat(sent["app"]?.jsonPrimitive?.content).isEqualTo("AAPS")
    }
}

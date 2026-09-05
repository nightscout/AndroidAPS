package app.aaps.core.nssdk.networking

import app.aaps.core.nssdk.NSAndroidClientImpl
import app.aaps.core.nssdk.exceptions.InvalidParameterNightscoutException
import app.aaps.core.nssdk.exceptions.UnsuccessfulNightscoutException
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

/**
 * Pins how each HTTP status is turned into a result, and **how many requests that costs**.
 *
 * The request count is the important half. `callWrapper` wraps every call in `retry`, which makes
 * **4 attempts** unless the exception's exact class is one of three excluded types
 * (`NSAndroidClientImpl.callWrapper`, `utils/CoroutineUtils.kt`). So the number of requests is a
 * direct, observable readout of whether an error path throws or returns:
 *
 * | path | 4xx behaviour | requests |
 * | --- | --- | --- |
 * | read (`getSgvs`, `searchSettings`) | throws `InvalidParameterNightscoutException` | **4** |
 * | write (`createSettings`) | returns `CreateUpdateResponse(response = 4xx)` | **1** |
 * | `getSettings` 404 | returns `ReadResponse(404, values = null)` | **1** |
 * | `deleteSettings` 404 | treated as an already-done delete | **1** |
 *
 * This asymmetry is what Ktor's `expectSuccess = true` would destroy: every non-2xx would throw,
 * the write ladders would become dead code, and because `ClientRequestException` is not in the
 * exclusion list it would be retried 4 times before surfacing. `NSClientV3Plugin` would then stop
 * advancing the sync cursor for that collection - quietly, with one log line.
 *
 * Driven through a real localhost server, so this file runs unchanged before and after the port.
 */
class NsSdkStatusContractTest {

    private lateinit var server: MockWebServer
    private lateinit var client: NSAndroidClientImpl

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = NSAndroidClientImpl(
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

    /** Answer every request with the same status and body - enough for the retry cases. */
    private fun alwaysRespond(code: Int, body: String = "") {
        repeat(8) { server.enqueue(MockResponse.Builder().code(code).body(body).build()) }
    }

    // ---------------------------------------------------------------- read paths

    @Test
    fun `a 4xx on a read throws InvalidParameter and is retried four times`() = runTest {
        alwaysRespond(400, """{"status":400,"message":"bad request"}""")

        assertThrows<InvalidParameterNightscoutException> { client.getSgvs() }
        assertThat(server.requestCount).isEqualTo(4)
    }

    @Test
    fun `a 5xx on a read throws Unsuccessful and is retried four times`() = runTest {
        alwaysRespond(500, "server exploded")

        assertThrows<UnsuccessfulNightscoutException> { client.getSgvs() }
        assertThat(server.requestCount).isEqualTo(4)
    }

    /** A successful call must cost exactly one request - the baseline the counts above are read against. */
    @Test
    fun `a successful read makes exactly one request`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"result":[]}""").build())

        val response = client.getSgvs()

        assertThat(response.values).isEmpty()
        assertThat(server.requestCount).isEqualTo(1)
    }

    /**
     * A bare 304 is an error, and always was.
     *
     * Nightscout only answers 304 to a **conditional** request. OkHttp sent those because it had a
     * disk cache entry with an ETag, and it then merged the 304 with the cached body and handed
     * Retrofit a **200** whose `raw().networkResponse.code` was 304 - which is the only way
     * `ReadResponse.code` could ever hold 304, and what the paging workers used as their stop signal.
     *
     * There is no cache now, so no conditional request is sent and no 304 comes back. If one ever did
     * arrive it would fail `isSuccessful` (200..299) and throw, exactly as it would have before when
     * there was no cached entry to merge with. Pinned so that stays true.
     */
    @Test
    fun `a bare 304 throws, as it did without a cache entry`() = runTest {
        alwaysRespond(304)

        assertThrows<UnsuccessfulNightscoutException> { client.getSgvsModifiedSince(1700000000000, 100) }
    }

    // ---------------------------------------------------------------- 404 as a normal answer

    /** `getSettings` turns 404 into an empty result, because "no settings doc yet" is not an error. */
    @Test
    fun `getSettings maps 404 to a null value without throwing`() = runTest {
        alwaysRespond(404, """{"status":404}""")

        val response = client.getSettings("aaps")

        assertThat(response.code).isEqualTo(404)
        assertThat(response.values).isNull()
        assertThat(server.requestCount).isEqualTo(1)
    }

    /**
     * A delete of something already gone is a success. `NSClientV3Plugin` relies on this to stop
     * retrying a tombstoned identifier forever.
     */
    @Test
    fun `deleteSettings treats 404 as done`() = runTest {
        alwaysRespond(404, """{"status":404}""")

        val response = client.deleteSettings("aaps_x")

        assertThat(response.response).isEqualTo(404)
        assertThat(server.requestCount).isEqualTo(1)
    }

    // ---------------------------------------------------------------- write paths

    /**
     * A write never throws on 4xx. It returns the status **and the verbatim server body**, which the
     * caller inspects - see `NsSdkErrorBodyContractTest` for the `utcOffset` path that depends on it.
     */
    @Test
    fun `a 4xx on a write returns the status and body, and makes one request`() = runTest {
        val body = """{"status":400,"message":"Bad or missing utcOffset field"}"""
        alwaysRespond(400, body)

        val response = client.createSettings(JsonObject(mapOf("a" to JsonPrimitive(1))))

        assertThat(response.response).isEqualTo(400)
        assertThat(response.errorResponse).isEqualTo(body)
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `a create accepts 200 and 201`() = runTest {
        server.enqueue(MockResponse.Builder().code(201).body("""{"identifier":"abc"}""").build())
        val created = client.createSettings(JsonObject(mapOf("a" to JsonPrimitive(1))))
        assertThat(created.response).isEqualTo(201)
        assertThat(created.identifier).isEqualTo("abc")

        server.enqueue(MockResponse.Builder().code(200).body("""{"identifier":"def"}""").build())
        val updated = client.createSettings(JsonObject(mapOf("a" to JsonPrimitive(1))))
        assertThat(updated.response).isEqualTo(200)
        assertThat(updated.identifier).isEqualTo("def")
    }

    /**
     * `identifier` coming back from a create is load bearing: `NSClientV3Plugin` stores it as the
     * Nightscout id, and without one the record is uploaded again as a new document, forever.
     */
    @Test
    fun `a create returns the identifier from the body`() = runTest {
        server.enqueue(MockResponse.Builder().code(201).body("""{"identifier":"1bebd84b-8d61-58b2"}""").build())

        val response = client.createSettings(JsonObject(mapOf("a" to JsonPrimitive(1))))

        assertThat(response.identifier).isEqualTo("1bebd84b-8d61-58b2")
    }
}

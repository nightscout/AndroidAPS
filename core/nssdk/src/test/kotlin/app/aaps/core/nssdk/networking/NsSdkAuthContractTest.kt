package app.aaps.core.nssdk.networking

import android.content.Context
import app.aaps.core.nssdk.NSAndroidClientImpl
import app.aaps.core.nssdk.exceptions.DateHeaderOutOfToleranceException
import app.aaps.core.nssdk.exceptions.InvalidAccessTokenException
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pins the authentication behaviour of `NSAuthInterceptor`.
 *
 * This is the riskiest part of the move to Ktor, because Ktor's ready made `Auth` / `bearer`
 * provider differs from this interceptor in four ways, and two of them lose data silently:
 *
 * 1. **The header is always sent, even when the token is empty.** `bearer` omits it instead. An
 *    unauthenticated request to Nightscout does not fail - it answers **200 with the anonymous
 *    role**, which flows into `lastStatus` -> `hasWritePermission` -> `DataSyncWorker` skipping the
 *    entire upload block. Nothing is logged and nothing throws; uploads simply stop.
 * 2. **Refresh fires on 401 and 403.** `bearer` only handles 401.
 * 3. The failing body is inspected **before** refreshing, to detect a clock-skew rejection. A Ktor
 *    refresh hook never sees the body, so that error would disappear.
 * 4. `bearer` serialises refreshes behind a `Mutex`; this code does not. That one is an improvement
 *    and may be adopted, but on purpose and with its own test - not as a side effect.
 *
 * Everything here is asserted through a real localhost server, so the file runs unchanged before and
 * after the port.
 */
class NsSdkAuthContractTest {

    private lateinit var server: MockWebServer
    private lateinit var client: NSAndroidClientImpl
    private lateinit var cacheDir: File

    private val refreshCalls = AtomicInteger(0)
    private var lastRefreshTarget: String? = null
    private var refreshHadAuthHeader: Boolean? = null

    @BeforeEach
    fun setUp() {
        cacheDir = Files.createTempDirectory("nssdk-auth-cache").toFile()
        server = MockWebServer()
        server.start()
        val context = mock<Context> { on { getCacheDir() } doReturn cacheDir }
        client = NSAndroidClientImpl(
            baseUrl = server.url("/").toString().trimEnd('/'),
            accessToken = "REFRESH_TOKEN",
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

    /**
     * Routes by path: the token refresh endpoint is answered with [refresh], everything else with
     * [data]. Using a dispatcher rather than a queue keeps the multi request flows readable.
     */
    private fun route(data: () -> MockResponse, refresh: () -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val target = request.target
                return if (target.contains("/authorization/request/")) {
                    refreshCalls.incrementAndGet()
                    lastRefreshTarget = target
                    refreshHadAuthHeader = request.headers["Authorization"] != null
                    refresh()
                } else data()
            }
        }
    }

    private fun ok(body: String = """{"result":[]}""") = MockResponse.Builder().code(200).body(body).build()
    private fun status(code: Int, body: String = "") = MockResponse.Builder().code(code).body(body).build()

    // ---------------------------------------------------------------- headers on every request

    /**
     * The empty-token case, stated on its own because it is the silent one. On the very first call
     * `jwtToken` is still `""`, so the code builds `"Bearer $jwtToken"` = `"Bearer "` - and the HTTP
     * layer strips the trailing space, so what actually goes on the wire is the bare word `Bearer`.
     *
     * The exact text matters: the point is that the header **is present**. Omitting it makes
     * Nightscout answer 200 with the anonymous role instead of failing.
     */
    @Test
    fun `Authorization is sent even before any token exists`() = runTest {
        server.enqueue(ok())

        client.getSgvs()

        val sent = server.takeRequest()
        assertThat(sent.headers["Authorization"]).isEqualTo("Bearer")
    }

    /** `Date` is bare epoch milliseconds, not an RFC 1123 date. Nightscout compares it to its own clock. */
    @Test
    fun `Date is sent as bare epoch milliseconds`() = runTest {
        server.enqueue(ok())

        client.getSgvs()

        val date = server.takeRequest().headers["Date"]
        assertThat(date).isNotNull()
        assertThat(date!!.all { it.isDigit() }).isTrue()
        // sane range rather than an exact value: milliseconds, not seconds
        assertThat(date.toLong()).isGreaterThan(1_600_000_000_000L)
    }

    // ---------------------------------------------------------------- refresh triggers

    @Test
    fun `a 401 triggers exactly one refresh and one replay`() = runTest {
        val dataCalls = AtomicInteger(0)
        route(
            data = { if (dataCalls.incrementAndGet() == 1) status(401, "unauthorized") else ok() },
            refresh = { ok("""{"token":"NEW_JWT","iat":1,"exp":2}""") }
        )

        val response = client.getSgvs()

        assertThat(response.values).isEmpty()
        assertThat(refreshCalls.get()).isEqualTo(1)
        assertThat(dataCalls.get()).isEqualTo(2)   // original + one replay, never more
    }

    /** 403 must refresh too - Ktor's bearer provider would ignore it. */
    @Test
    fun `a 403 also triggers a refresh`() = runTest {
        val dataCalls = AtomicInteger(0)
        route(
            data = { if (dataCalls.incrementAndGet() == 1) status(403, "forbidden") else ok() },
            refresh = { ok("""{"token":"NEW_JWT","iat":1,"exp":2}""") }
        )

        client.getSgvs()

        assertThat(refreshCalls.get()).isEqualTo(1)
        assertThat(dataCalls.get()).isEqualTo(2)
    }

    /** After a successful refresh the replay carries the NEW token. */
    @Test
    fun `the replay carries the refreshed token`() = runTest {
        val seenAuth = mutableListOf<String?>()
        val dataCalls = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.target.contains("/authorization/request/")) ok("""{"token":"NEW_JWT","iat":1,"exp":2}""")
                else {
                    seenAuth += request.headers["Authorization"]
                    if (dataCalls.incrementAndGet() == 1) status(401, "unauthorized") else ok()
                }
        }

        client.getSgvs()

        assertThat(seenAuth).hasSize(2)
        assertThat(seenAuth[0]).isEqualTo("Bearer")             // no token yet, trailing space stripped
        assertThat(seenAuth[1]).isEqualTo("Bearer NEW_JWT")     // refreshed
    }

    // ---------------------------------------------------------------- the refresh call itself

    /** The refresh must not carry an Authorization header, or it would recurse into itself. */
    @Test
    fun `the refresh request carries no Authorization header and uses the refresh token in the path`() = runTest {
        val dataCalls = AtomicInteger(0)
        route(
            data = { if (dataCalls.incrementAndGet() == 1) status(401, "unauthorized") else ok() },
            refresh = { ok("""{"token":"NEW_JWT","iat":1,"exp":2}""") }
        )

        client.getSgvs()

        assertThat(refreshHadAuthHeader).isFalse()
        assertThat(lastRefreshTarget).isEqualTo("/api/v2/authorization/request/REFRESH_TOKEN")
    }

    // ---------------------------------------------------------------- refresh outcomes

    /** A refresh that is itself rejected means the refresh token is bad - a distinct, non-retried error. */
    @Test
    fun `a rejected refresh throws InvalidAccessToken and is not retried`() = runTest {
        route(data = { status(401, "unauthorized") }, refresh = { status(401, "nope") })

        assertThrows<InvalidAccessTokenException> { client.getSgvs() }
        assertThat(refreshCalls.get()).isEqualTo(1)   // excluded from retry, so exactly one attempt
    }

    /**
     * A refresh that fails for another reason (server error) returns the ORIGINAL 401, which the read
     * path then maps to a 4xx error. That one is not excluded from retry, so the whole flow repeats.
     */
    @Test
    fun `a refresh that errors falls back to the original response`() = runTest {
        route(data = { status(401, "unauthorized") }, refresh = { status(500, "boom") })

        assertThrows<Exception> { client.getSgvs() }
        // 4 attempts of (data + refresh), because InvalidParameterNightscoutException is retried
        assertThat(refreshCalls.get()).isEqualTo(4)
    }

    // ---------------------------------------------------------------- clock skew

    /**
     * The clock-skew path. Nightscout rejects a request whose `Date` is too far from its own clock,
     * and refreshing would not help, so the body is checked BEFORE any refresh is attempted.
     *
     * A Ktor refresh hook never sees the response body, which is why this cannot be expressed with
     * the stock `bearer` provider.
     */
    @Test
    fun `a clock skew rejection throws before any refresh is attempted`() = runTest {
        route(data = { status(401, "Date header out of tolerance") }, refresh = { ok() })

        assertThrows<DateHeaderOutOfToleranceException> { client.getSgvs() }
        assertThat(refreshCalls.get()).isEqualTo(0)   // never even tried
    }

    /** The check is a plain substring match on the body, so surrounding JSON does not hide it. */
    @Test
    fun `the clock skew message is matched inside a JSON body`() = runTest {
        route(
            data = { status(401, """{"status":401,"message":"Date header out of tolerance, check your clock"}""") },
            refresh = { ok() }
        )

        assertThrows<DateHeaderOutOfToleranceException> { client.getSgvs() }
        assertThat(refreshCalls.get()).isEqualTo(0)
    }
}

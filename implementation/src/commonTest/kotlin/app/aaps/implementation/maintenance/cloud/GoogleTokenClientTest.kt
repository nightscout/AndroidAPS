package app.aaps.implementation.maintenance.cloud

import app.aaps.implementation.maintenance.formats.FakeKeyValueStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The token half of a Google sign in, against scripted answers.
 *
 * Ktor's mock engine stands in for Google, so these run on every platform with no network and no
 * account - which matters because this is the code that decides whether a user is asked to sign in
 * again or told to try later, and both of those are hard to produce on demand against the real
 * thing.
 */
class GoogleTokenClientTest {

    private val store = FakeKeyValueStore()
    private val tokens = GoogleTokenStore(store)
    private var clock = 1_000_000L
    private val requests = mutableListOf<String>()

    private fun clientReplying(vararg replies: Pair<HttpStatusCode, String>): GoogleTokenClient {
        var call = 0
        val engine = MockEngine { request ->
            requests += (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
            val (status, body) = replies[minOf(call++, replies.size - 1)]
            respond(
                content = ByteReadChannel(body),
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        return GoogleTokenClient(
            http = HttpClient(engine),
            tokens = tokens,
            clientId = "the-client",
            redirectUri = "http://localhost:8080/oauth/callback",
            now = { clock }
        )
    }

    private val fullTokens = """{"access_token":"access-1","refresh_token":"refresh-1","expires_in":3600}"""

    // ----- Finishing a sign in -----

    @Test
    fun `a sign in stores the tokens it was given`() = runTest {
        tokens.codeVerifier = "the-verifier"
        val sut = clientReplying(HttpStatusCode.OK to fullTokens)

        assertNull(sut.exchangeCode("the-code"))

        assertEquals("refresh-1", tokens.refreshToken)
        assertEquals("access-1", tokens.accessToken)
        assertEquals(clock + 3_600_000, tokens.expiresAt)
    }

    /** The verifier is spent. Leaving it would let a second sign in start with a stale one. */
    @Test
    fun `a finished sign in forgets the verifier`() = runTest {
        tokens.codeVerifier = "the-verifier"
        tokens.state = "the-state"
        val sut = clientReplying(HttpStatusCode.OK to fullTokens)

        sut.exchangeCode("the-code")

        assertNull(tokens.codeVerifier)
        assertNull(tokens.state)
    }

    /** PKCE is the point: the code alone must not be enough, so the verifier has to be sent. */
    @Test
    fun `the verifier is sent with the code`() = runTest {
        tokens.codeVerifier = "the-verifier"
        val sut = clientReplying(HttpStatusCode.OK to fullTokens)

        sut.exchangeCode("the-code")

        assertTrue(requests.single().contains("code_verifier=the-verifier"), requests.single())
    }

    /**
     * Without a refresh token the sign in works for an hour and then stops for good. Storing what
     * came back would hide that until days later, when it looks like the feature broke by itself.
     */
    @Test
    fun `a sign in with no refresh token is refused rather than half kept`() = runTest {
        tokens.codeVerifier = "the-verifier"
        val sut = clientReplying(HttpStatusCode.OK to """{"access_token":"access-1","expires_in":3600}""")

        val failure = sut.exchangeCode("the-code")

        assertIs<TokenFailure.Failed>(failure)
        assertNull(tokens.accessToken)
        assertNull(tokens.refreshToken)
    }

    @Test
    fun `a sign in that was not started here is refused`() = runTest {
        val sut = clientReplying(HttpStatusCode.OK to fullTokens)

        assertIs<TokenFailure.Failed>(sut.exchangeCode("the-code"))
    }

    // ----- Keeping the access token fresh -----

    @Test
    fun `a token that is still good is used without asking Google`() = runTest {
        tokens.accessToken = "access-1"
        tokens.refreshToken = "refresh-1"
        tokens.expiresAt = clock + 3_600_000
        val sut = clientReplying(HttpStatusCode.OK to fullTokens)

        assertEquals("access-1", sut.validAccessToken().getOrNull())
        assertTrue(requests.isEmpty(), "Google should not have been asked")
    }

    @Test
    fun `an expired token is refreshed`() = runTest {
        tokens.accessToken = "old"
        tokens.refreshToken = "refresh-1"
        tokens.expiresAt = clock - 1
        val sut = clientReplying(HttpStatusCode.OK to """{"access_token":"access-2","expires_in":3600}""")

        assertEquals("access-2", sut.validAccessToken().getOrNull())
        assertEquals(clock + 3_600_000, tokens.expiresAt)
    }

    /**
     * A token with seconds left would be sent and refused mid request, and the user would see a
     * failure that a refresh a moment earlier would have avoided.
     */
    @Test
    fun `a token about to expire is refreshed early`() = runTest {
        tokens.accessToken = "old"
        tokens.refreshToken = "refresh-1"
        tokens.expiresAt = clock + 10_000
        val sut = clientReplying(HttpStatusCode.OK to """{"access_token":"access-2","expires_in":3600}""")

        assertEquals("access-2", sut.validAccessToken().getOrNull())
    }

    @Test
    fun `a refresh keeps the refresh token when Google does not send a new one`() = runTest {
        tokens.refreshToken = "refresh-1"
        tokens.expiresAt = 0
        val sut = clientReplying(HttpStatusCode.OK to """{"access_token":"access-2","expires_in":3600}""")

        sut.validAccessToken()

        assertEquals("refresh-1", tokens.refreshToken)
    }

    @Test
    fun `a refresh takes a new refresh token when Google sends one`() = runTest {
        tokens.refreshToken = "refresh-1"
        tokens.expiresAt = 0
        val sut = clientReplying(HttpStatusCode.OK to """{"access_token":"a","refresh_token":"refresh-2","expires_in":3600}""")

        sut.validAccessToken()

        assertEquals("refresh-2", tokens.refreshToken)
    }

    @Test
    fun `never having signed in says so`() = runTest {
        val sut = clientReplying(HttpStatusCode.OK to fullTokens)

        val failure = assertIs<TokenException>(sut.validAccessToken().exceptionOrNull())
        assertIs<TokenFailure.NotSignedIn>(failure.failure)
    }

    /**
     * `invalid_grant` is Google saying the refresh token is finished - revoked, or the password
     * changed. The user has to sign in again, and being told to "try later" forever would be wrong.
     */
    @Test
    fun `a revoked sign in is reported as needing a new one`() = runTest {
        tokens.refreshToken = "refresh-1"
        tokens.expiresAt = 0
        val sut = clientReplying(HttpStatusCode.BadRequest to """{"error":"invalid_grant"}""")

        val failure = assertIs<TokenException>(sut.validAccessToken().exceptionOrNull())
        assertIs<TokenFailure.SignInExpired>(failure.failure)
    }

    /** Nothing is left to try with, so the dead sign in is cleared instead of failing on every screen. */
    @Test
    fun `a revoked sign in is forgotten`() = runTest {
        tokens.refreshToken = "refresh-1"
        tokens.accessToken = "access-1"
        tokens.expiresAt = 0
        val sut = clientReplying(HttpStatusCode.BadRequest to """{"error":"invalid_grant"}""")

        sut.validAccessToken()

        assertNull(tokens.refreshToken)
        assertNull(tokens.accessToken)
    }

    /** A server having a bad day is not a reason to make the user sign in again. */
    @Test
    fun `a temporary failure keeps the sign in`() = runTest {
        tokens.refreshToken = "refresh-1"
        tokens.expiresAt = 0
        val sut = clientReplying(HttpStatusCode.ServiceUnavailable to """{"error":"backend_error"}""")

        val failure = assertIs<TokenException>(sut.validAccessToken().exceptionOrNull())
        assertIs<TokenFailure.Failed>(failure.failure)
        assertEquals("refresh-1", tokens.refreshToken)
    }

    @Test
    fun `an answer that is not json is a failure and not a crash`() = runTest {
        tokens.refreshToken = "refresh-1"
        tokens.expiresAt = 0
        val sut = clientReplying(HttpStatusCode.OK to "<html>a proxy said no</html>")

        assertIs<TokenException>(sut.validAccessToken().exceptionOrNull())
        assertEquals("refresh-1", tokens.refreshToken)
    }

    // ----- What is stored -----

    @Test
    fun `signing out leaves nothing behind`() {
        tokens.refreshToken = "r"
        tokens.accessToken = "a"
        tokens.expiresAt = 123
        tokens.codeVerifier = "v"
        tokens.state = "s"

        tokens.clear()

        assertNull(tokens.refreshToken)
        assertNull(tokens.accessToken)
        assertNull(tokens.codeVerifier)
        assertNull(tokens.state)
        assertEquals(0L, tokens.expiresAt)
    }

    /** The same names Android writes, so one device's sign in is readable by the other build. */
    @Test
    fun `the tokens are stored under the names Android uses`() {
        tokens.refreshToken = "r"
        tokens.accessToken = "a"
        tokens.expiresAt = 123

        assertEquals("r", store.getString("google_drive_refresh_token", ""))
        assertEquals("a", store.getString("google_drive_access_token", ""))
        assertEquals(123L, store.getLong("google_drive_token_expiry", 0))
    }
}

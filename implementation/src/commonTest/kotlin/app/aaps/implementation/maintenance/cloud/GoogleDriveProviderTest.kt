package app.aaps.implementation.maintenance.cloud

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.objects.crypto.platformCryptoPrimitives
import app.aaps.implementation.maintenance.formats.FakeKeyValueStore
import app.aaps.core.keys.interfaces.TextRef
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The order the pieces of a Google sign in go in, and what happens when one of them says no.
 *
 * The pieces themselves are tested next door. What is checked here is the wiring, and the two things
 * about it that are worth pinning: that a sign in nobody finished does not leave a port open, and
 * that a dead sign in is told apart from a bad connection - because those lead the user to two
 * different screens and only one of them is worth showing.
 */
class GoogleDriveProviderTest {

    private val store = FakeKeyValueStore()
    private val tokens = GoogleTokenStore(store)
    private val listener = FakeRedirectListener()
    private var clock = 1_000_000L

    private fun providerReplying(vararg replies: Pair<HttpStatusCode, String>): GoogleDriveProvider {
        var call = 0
        val engine = MockEngine { _ ->
            val (status, body) = replies.getOrElse(minOf(call++, replies.size - 1)) { HttpStatusCode.OK to "{}" }
            respond(ByteReadChannel(body), status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val http = HttpClient(engine)
        val tokenClient = GoogleTokenClient(http, tokens, "the-client", "http://localhost:8080/oauth/callback", now = { clock })
        return GoogleDriveProvider(
            aapsLogger = SilentLogger(),
            store = store,
            listener = listener,
            auth = GoogleAuthRequest(platformCryptoPrimitives()),
            tokens = tokens,
            tokenClient = tokenClient,
            api = GoogleDriveApi(http, accessToken = { tokenClient.validAccessToken() }),
            storageType = "google_drive",
            displayName = "Google Drive",
            icon = Icons.Default.Cloud,
            authorizedText = TextRef.Literal("authorized"),
            reAuthRequiredText = TextRef.Literal("sign in again"),
            clientId = "the-client"
        )
    }

    // ----- Starting a sign in -----

    @Test
    fun `starting a sign in returns a url carrying the challenge and the state`() = runTest {
        val url = assertNotNull(providerReplying().startAuth())

        assertTrue(url.startsWith(GoogleDriveProvider.AUTH_ENDPOINT), url)
        assertTrue(url.contains("code_challenge="), url)
        assertTrue(url.contains("state=${tokens.state}"), url)
        assertTrue(url.contains("code_challenge_method=S256"), url)
    }

    /** The verifier has to outlive the browser, or the code cannot be swapped when it comes back. */
    @Test
    fun `starting a sign in remembers the verifier and the state`() = runTest {
        providerReplying().startAuth()

        assertNotNull(tokens.codeVerifier)
        assertNotNull(tokens.state)
    }

    @Test
    fun `starting a sign in opens the listener first`() = runTest {
        providerReplying().startAuth()

        assertTrue(listener.started, "the redirect would have arrived at a closed port")
    }

    /** A port already held is a sign in that cannot finish, so it must not be offered. */
    @Test
    fun `a port that cannot be opened gives no url`() = runTest {
        listener.startSucceeds = false

        assertNull(providerReplying().startAuth())
    }

    /** Only files AAPS created. Asking for more would be asking to read the user's whole Drive. */
    @Test
    fun `the sign in asks only for the files AAPS creates`() = runTest {
        val url = assertNotNull(providerReplying().startAuth())

        assertTrue(url.contains("drive.file"), url)
    }

    // ----- Finishing one -----

    @Test
    fun `the code is handed back when the redirect matches`() = runTest {
        val provider = providerReplying()
        provider.startAuth()
        listener.result = OAuthCallback.Result.Code("the-code", tokens.state)

        assertEquals("the-code", provider.waitForAuthCode(1000))
    }

    /** However it ends, the port is given back - or the next sign in cannot start. */
    @Test
    fun `the listener is closed after the wait`() = runTest {
        val provider = providerReplying()
        provider.startAuth()
        listener.result = OAuthCallback.Result.Code("the-code", tokens.state)

        provider.waitForAuthCode(1000)

        assertTrue(listener.stopped)
    }

    @Test
    fun `a sign in nobody finished still closes the listener`() = runTest {
        val provider = providerReplying()
        provider.startAuth()
        listener.result = null

        assertNull(provider.waitForAuthCode(1000))
        assertTrue(listener.stopped, "an abandoned sign in left the port open")
    }

    @Test
    fun `a refusal is not an error and gives no code`() = runTest {
        val provider = providerReplying()
        provider.startAuth()
        listener.result = OAuthCallback.Result.Denied("access_denied")

        assertNull(provider.waitForAuthCode(1000))
        assertTrue(listener.stopped)
    }

    @Test
    fun `waiting without having started is refused`() = runTest {
        assertNull(providerReplying().waitForAuthCode(1000))
    }

    @Test
    fun `completing a sign in stores the tokens`() = runTest {
        val provider = providerReplying(HttpStatusCode.OK to """{"access_token":"a","refresh_token":"r","expires_in":3600}""")
        provider.startAuth()

        assertTrue(provider.completeAuth("the-code"))
        assertTrue(provider.hasValidCredentials())
    }

    @Test
    fun `a sign in Google refused is not stored`() = runTest {
        val provider = providerReplying(HttpStatusCode.BadRequest to """{"error":"invalid_grant"}""")
        provider.startAuth()

        assertFalse(provider.completeAuth("the-code"))
        assertFalse(provider.hasValidCredentials())
        assertTrue(provider.hasConnectionError())
    }

    // ----- Using it, and failing -----

    @Test
    fun `a listing comes back as files`() = runTest {
        tokens.refreshToken = "r"
        tokens.accessToken = "a"
        tokens.expiresAt = clock + 3_600_000
        val provider = providerReplying(HttpStatusCode.OK to """{"files":[{"id":"1","name":"export.json"}]}""")

        val result = provider.listSettingsFiles(pageSize = 10, pageToken = null)

        assertEquals(listOf("export.json"), result.files.map { it.name })
    }

    /**
     * A revoked sign in has to send the user back through one, so the stored credentials go. Leaving
     * them would fail the same way on every screen with no way out.
     */
    @Test
    fun `a revoked sign in is cleared when Drive refuses`() = runTest {
        tokens.refreshToken = "r"
        tokens.accessToken = "a"
        tokens.expiresAt = clock + 3_600_000
        val provider = providerReplying(HttpStatusCode.Unauthorized to "")

        provider.listSettingsFiles(pageSize = 10, pageToken = null)

        assertFalse(provider.hasValidCredentials(), "the dead sign in should have been cleared")
    }

    /** A server having a bad day must not make the user sign in again. */
    @Test
    fun `a connection problem keeps the sign in`() = runTest {
        tokens.refreshToken = "r"
        tokens.accessToken = "a"
        tokens.expiresAt = clock + 3_600_000
        val provider = providerReplying(HttpStatusCode.ServiceUnavailable to "")

        provider.listSettingsFiles(pageSize = 10, pageToken = null)

        assertTrue(provider.hasValidCredentials(), "a temporary failure should not end the sign in")
        assertTrue(provider.hasConnectionError())
    }

    @Test
    fun `a working call clears an earlier connection error`() = runTest {
        tokens.refreshToken = "r"
        tokens.accessToken = "a"
        tokens.expiresAt = clock + 3_600_000
        val provider = providerReplying(HttpStatusCode.OK to """{"user":{}}""")
        store.putBoolean("google_drive_connection_error", true)

        assertTrue(provider.testConnection())
        assertFalse(provider.hasConnectionError())
    }

    @Test
    fun `signing out leaves no credentials`() = runTest {
        tokens.refreshToken = "r"
        val provider = providerReplying()

        provider.clearCredentials()

        assertFalse(provider.hasValidCredentials())
    }

    @Test
    fun `the chosen folder is remembered`() {
        val provider = providerReplying()

        provider.setSelectedFolderId("folder-1")

        assertEquals("folder-1", provider.getSelectedFolderId())
    }

    /** With nothing chosen, Drive's own root is the sensible place rather than a blank id. */
    @Test
    fun `with no folder chosen the root is used`() {
        assertEquals("root", providerReplying().getSelectedFolderId())
    }

    private class FakeRedirectListener : AuthRedirectListener {

        var startSucceeds = true
        var started = false
        var stopped = false
        var result: OAuthCallback.Result? = null
        var seenState: String? = null

        override fun start(port: Int): Boolean {
            started = startSucceeds
            return startSucceeds
        }

        override suspend fun awaitCallback(expectedState: String, timeoutMs: Long): OAuthCallback.Result? {
            seenState = expectedState
            return result
        }

        override fun stop() {
            stopped = true
        }
    }

    private class SilentLogger : AAPSLogger {
        override fun debug(tag: LTag, message: String) {}
        override fun debug(message: String) {}
        override fun debug(enable: Boolean, tag: LTag, message: String) {}
        override fun debug(tag: LTag, accessor: () -> String) {}
        override fun debug(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun warn(tag: LTag, message: String) {}
        override fun warn(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun info(tag: LTag, message: String) {}
        override fun info(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun error(tag: LTag, message: String) {}
        override fun error(tag: LTag, message: String, throwable: Throwable) {}
        override fun error(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun error(message: String) {}
        override fun error(message: String, throwable: Throwable) {}
        override fun error(format: String, vararg arguments: Any?) {}
        override fun debug(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun info(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun warn(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun error(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
    }
}

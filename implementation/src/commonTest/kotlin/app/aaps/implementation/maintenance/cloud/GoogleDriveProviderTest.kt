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
    private val notifications = RecordingNotificationManager()
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
            api = GoogleDriveApi(http, accessToken = { force -> tokenClient.validAccessToken(force) }),
            storageType = "google_drive",
            displayName = "Google Drive",
            icon = Icons.Default.Cloud,
            authorizedText = TextRef.Literal("authorized"),
            reAuthRequiredText = TextRef.Literal("sign in again"),
            notificationManager = notifications,
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
     *
     * Drive answers 401, the forced refresh that follows is told `invalid_grant`, and that second
     * answer is the one that settles it.
     */
    @Test
    fun `a revoked sign in is cleared when Drive refuses`() = runTest {
        tokens.refreshToken = "r"
        tokens.accessToken = "a"
        tokens.expiresAt = clock + 3_600_000
        val provider = providerReplying(
            HttpStatusCode.Unauthorized to "",
            HttpStatusCode.BadRequest to """{"error":"invalid_grant"}"""
        )

        provider.listSettingsFiles(pageSize = 10, pageToken = null)

        assertFalse(provider.hasValidCredentials(), "the dead sign in should have been cleared")
    }

    /**
     * The one this retry exists for.
     *
     * Whether the stored access token is still good is decided by this device's clock. A phone a few
     * minutes fast believes a token is fine that Drive has already retired, and the 401 that comes
     * back is indistinguishable from a revoked sign in. Before the retry that cost the user their
     * refresh token - a working sign in thrown away because the clock was wrong - and automatic
     * backups stopped until somebody noticed and signed in again.
     */
    @Test
    fun `a token refused early is refreshed and the call goes through`() = runTest {
        tokens.refreshToken = "r"
        tokens.accessToken = "stale"
        tokens.expiresAt = clock + 3_600_000
        val provider = providerReplying(
            HttpStatusCode.Unauthorized to "",
            HttpStatusCode.OK to """{"access_token":"fresh","expires_in":3600}""",
            HttpStatusCode.OK to """{"files":[{"id":"1","name":"export.json"}]}"""
        )

        val result = provider.listSettingsFiles(pageSize = 10, pageToken = null)

        assertEquals(listOf("export.json"), result.files.map { it.name }, "the retry should have returned the listing")
        assertTrue(provider.hasValidCredentials(), "the sign in was good and must survive")
        assertEquals("fresh", tokens.accessToken)
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

    /**
     * The folder is read from the name Android already writes, like the tokens beside it.
     *
     * Getting this name wrong does not look like a bug: the user stays signed in, every screen works,
     * and the only sign is that exports quietly start landing in the root of their Drive instead of
     * in the folder they picked. Asserted on the stored name rather than through the provider,
     * because a rename would keep every other test in this file passing.
     */
    @Test
    fun `the folder is kept under the name Android writes`() {
        store.putString("google_drive_folder_id", "folder-from-android")

        assertEquals("folder-from-android", providerReplying().getSelectedFolderId())
    }


    /**
     * Every name the shared code stores under has to be the one Android already writes, or a user who
     * signed in on a phone keeps something and loses something else on their iPhone - silently, and
     * differently for each value that got a new name. The folder was exactly that: a name of my own
     * invention meant the sign in survived and the chosen folder did not, so the next export would
     * have gone to the root of their Drive.
     */
    @Test
    fun `nothing is stored under a name of our own invention`() {
        val provider = providerReplying()
        tokens.refreshToken = "r"
        tokens.accessToken = "a"
        tokens.expiresAt = 1
        tokens.codeVerifier = "v"
        tokens.state = "s"
        provider.setSelectedFolderId("folder-1")

        assertEquals(
            setOf(
                "google_drive_refresh_token",
                "google_drive_access_token",
                "google_drive_token_expiry",
                "google_drive_code_verifier",
                "google_drive_oauth_state",
                "google_drive_folder_id"
            ),
            store.getAll().keys
        )
    }

    /**
     * A connection error describes now, not forever. Android holds it in memory for that reason, and
     * storing it would leave a user who lost signal once looking at a warning on a working connection
     * until something cleared it.
     */
    @Test
    fun `a connection error is not written down`() = runTest {
        tokens.refreshToken = "r"
        tokens.accessToken = "a"
        tokens.expiresAt = clock + 3_600_000
        val provider = providerReplying(HttpStatusCode.ServiceUnavailable to "")

        provider.listSettingsFiles(pageSize = 10, pageToken = null)

        assertTrue(provider.hasConnectionError())
        assertTrue(store.getAll().keys.none { it.contains("connection") }, "it survived a restart: ${store.getAll().keys}")
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

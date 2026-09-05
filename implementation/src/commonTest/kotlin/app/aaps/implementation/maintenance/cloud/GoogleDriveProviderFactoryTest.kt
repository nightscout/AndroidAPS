package app.aaps.implementation.maintenance.cloud

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.implementation.maintenance.formats.FakeKeyValueStore
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
import kotlin.test.assertTrue

/**
 * The wiring every platform shares, checked once.
 *
 * `IosGoogleDriveProvider` and `DesktopGoogleDriveProvider` are now nothing but an engine choice
 * around [googleDriveProvider], and this covers what they used to each assemble by hand. It exists
 * because the hand-assembled copies were **not equal**: one passed
 * `{ tokenClient.validAccessToken() }` for the access token, which compiles - the lambda's `it` is
 * simply unused - and silently drops the `forceRefresh` flag.
 *
 * That difference is invisible until a device's clock runs a few minutes fast. Drive refuses a token
 * it considers expired, the retry asks for a forced refresh, the flag is dropped, the identical
 * stale token is sent again, and the second 401 is believed - so a working sign in is thrown away
 * and automatic backups stop. The per-platform tests could not catch it, because each built its own
 * wiring rather than using the platform class.
 */
class GoogleDriveProviderFactoryTest {

    private val store = FakeKeyValueStore()

    private fun providerReplying(vararg replies: Pair<HttpStatusCode, String>): GoogleDriveProvider {
        var call = 0
        val engine = MockEngine { _ ->
            val (status, body) = replies.getOrElse(minOf(call++, replies.size - 1)) { HttpStatusCode.OK to "{}" }
            respond(ByteReadChannel(body), status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return googleDriveProvider(
            aapsLogger = SilentLogger(),
            store = store,
            listener = NeverCalledListener(),
            http = HttpClient(engine),
            now = { 1_000_000L }
        )
    }

    /**
     * The regression this file is named for.
     *
     * Drive answers 401, the forced refresh returns a new token, and the retry goes through. If the
     * flag were dropped the refresh would not happen, the second call would carry the same stale
     * token, and the sign in would be cleared instead.
     */
    @Test
    fun `a token refused early is refreshed and the call goes through`() = runTest {
        val tokens = GoogleTokenStore(store)
        tokens.refreshToken = "r"
        tokens.accessToken = "stale"
        tokens.expiresAt = 1_000_000L + 3_600_000
        val provider = providerReplying(
            HttpStatusCode.Unauthorized to "",
            HttpStatusCode.OK to """{"access_token":"fresh","expires_in":3600}""",
            HttpStatusCode.OK to """{"files":[{"id":"1","name":"export.json"}]}"""
        )

        val result = provider.listSettingsFiles(pageSize = 10, pageToken = null)

        assertEquals(listOf("export.json"), result.files.map { it.name }, "the forced refresh was dropped")
        assertTrue(provider.hasValidCredentials(), "a good sign in must survive a stale access token")
    }

    /** One registration, so the id and the redirect it is registered against cannot disagree. */
    @Test
    fun `the redirect uri is built from the port Google has registered`() {
        assertEquals("http://localhost:${GoogleDriveProvider.REDIRECT_PORT}/oauth/callback", GoogleDriveProvider.REDIRECT_URI)
        assertTrue(GoogleDriveProvider.CLIENT_ID.endsWith(".apps.googleusercontent.com"), GoogleDriveProvider.CLIENT_ID)
    }

    /** Every platform offers Drive under the one storage type, or the picker would list two. */
    @Test
    fun `the provider identifies itself as Google Drive`() {
        val provider = providerReplying()

        assertEquals(StorageTypes.GOOGLE_DRIVE, provider.storageType)
        assertEquals("Google Drive", provider.displayName)
    }

    /** Nothing here starts a sign in, so the listener must never be touched. */
    private class NeverCalledListener : AuthRedirectListener {

        override fun start(port: Int): Boolean = throw AssertionError("no sign in should be started here")
        override suspend fun awaitCallback(expectedState: String, timeoutMs: Long): OAuthCallback.Result? =
            throw AssertionError("no sign in should be started here")

        override fun stop() = Unit
    }

    private class SilentLogger : AAPSLogger {

        override fun debug(message: String) {}
        override fun debug(enable: Boolean, tag: LTag, message: String) {}
        override fun debug(tag: LTag, message: String) {}
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

package app.aaps.plugins.sync.tidepool.auth

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.plugins.sync.tidepool.compose.TidepoolRepository
import app.aaps.plugins.sync.tidepool.keys.TidepoolStringNonKey
import com.google.common.truth.Truth.assertThat
import net.openid.appauth.AuthorizationException
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Robolectric test for [AuthFlowOut]: the AppAuth [net.openid.appauth.AuthorizationService] is built
 * in the constructor and needs a real Android [Context] + org.json, so this runs under Robolectric.
 * Covers the derived connection-status logic and the auth-state persistence paths (save / erase /
 * init / clearAllSavedData); the browser-launch + token-exchange paths are Android-interactive and
 * remain out of unit-test scope.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AuthFlowOutTest {

    private val aapsLogger: AAPSLogger = mock()
    private val preferences: Preferences = mock()
    private val cryptoUtil: CryptoUtil = mock()
    private val context: Context = RuntimeEnvironment.getApplication()
    private val tidepoolRepository = TidepoolRepository(aapsLogger)

    private lateinit var sut: AuthFlowOut

    @Before
    fun setUp() {
        // initAuthState() reads these; default to "no stored state".
        whenever(preferences.get(TidepoolStringNonKey.ServiceConfiguration)).thenReturn("")
        whenever(preferences.get(TidepoolStringNonKey.AuthState)).thenReturn("")
        sut = AuthFlowOut(aapsLogger, preferences, context, cryptoUtil, tidepoolRepository)
    }

    /** What the Tidepool screen shows in its log */
    private fun logLines(): List<String> = tidepoolRepository.logList.value.map { it.action }

    @Test
    fun `fresh state without a token reports NOT_LOGGED_IN`() {
        assertThat(sut.connectionStatus).isEqualTo(AuthFlowOut.ConnectionStatus.NOT_LOGGED_IN)
    }

    @Test
    fun `updateConnectionStatus overrides the derived status`() {
        sut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.SESSION_ESTABLISHED)
        assertThat(sut.connectionStatus).isEqualTo(AuthFlowOut.ConnectionStatus.SESSION_ESTABLISHED)
    }

    @Test
    fun `updateConnectionStatus with a message logs it and shows the new status`() {
        sut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.BLOCKED, "blocked!")
        assertThat(logLines()).contains("blocked!")
        assertThat(tidepoolRepository.connectionStatus.value).isEqualTo(AuthFlowOut.ConnectionStatus.BLOCKED)
    }

    @Test
    fun `updateConnectionStatus without a message only shows the new status`() {
        sut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.FETCHING_TOKEN)
        assertThat(logLines()).isEmpty()
        assertThat(tidepoolRepository.connectionStatus.value).isEqualTo(AuthFlowOut.ConnectionStatus.FETCHING_TOKEN)
    }

    @Test
    fun `a reset to NONE shows the derived status`() {
        sut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.NONE)
        // No token was ever received, so NONE reads as NOT_LOGGED_IN
        assertThat(tidepoolRepository.connectionStatus.value).isEqualTo(AuthFlowOut.ConnectionStatus.NOT_LOGGED_IN)
    }

    @Test
    fun `saveAuthState persists a non-empty serialized auth state`() {
        val valueCaptor = argumentCaptor<String>()
        sut.saveAuthState()
        verify(preferences).put(eq(TidepoolStringNonKey.AuthState), valueCaptor.capture())
        assertThat(valueCaptor.firstValue).isNotEmpty()
    }

    @Test
    fun `eraseAuthState clears the stored state and re-derives NOT_LOGGED_IN`() {
        sut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.SESSION_ESTABLISHED)
        sut.eraseAuthState("bye")
        verify(preferences).put(TidepoolStringNonKey.AuthState, "")
        assertThat(logLines()).contains("bye")
        assertThat(sut.connectionStatus).isEqualTo(AuthFlowOut.ConnectionStatus.NOT_LOGGED_IN)
    }

    @Test
    fun `clearAllSavedData wipes the service configuration and erases the auth state`() {
        sut.clearAllSavedData()
        verify(preferences).put(TidepoolStringNonKey.ServiceConfiguration, "")
        verify(preferences).put(TidepoolStringNonKey.AuthState, "")
        assertThat(logLines()).contains("Credentials cleared")
    }

    @Test
    fun `initAuthState wipes a corrupt stored auth state`() {
        whenever(preferences.get(TidepoolStringNonKey.AuthState)).thenReturn("not-valid-json")
        sut.initAuthState()
        verify(preferences).put(TidepoolStringNonKey.AuthState, "")
        verify(preferences).put(TidepoolStringNonKey.ServiceConfiguration, "")
    }

    @Test
    fun `initAuthState does not log the tokens`() {
        // Logs get shared ("Send logs"), and a refresh token in one gives access to the Tidepool data (#5206)
        whenever(preferences.get(TidepoolStringNonKey.AuthState)).thenReturn("""{"refreshToken":"secret-refresh-token"}""")

        sut.initAuthState()

        val messages = argumentCaptor<String>()
        verify(aapsLogger, atLeastOnce()).debug(eq(LTag.TIDEPOOL), messages.capture())
        assertThat(messages.allValues.any { it.startsWith("Using auth state") }).isTrue()
        assertThat(messages.allValues.none { it.contains("secret-refresh-token") }).isTrue()
    }

    // isTransientTokenError: tells a failed token refresh that can be retried (bad network) from one that
    // really needs a new login. See https://github.com/nightscout/AndroidAPS/issues/4989.

    @Test
    fun `network error is transient`() {
        // What AppAuth reports when the silent refresh cannot reach the server: {"type":0,"code":3}
        val networkError = AuthorizationException.fromTemplate(AuthorizationException.GeneralErrors.NETWORK_ERROR, IOException("timeout"))
        assertThat(AuthFlowOut.isTransientTokenError(networkError)).isTrue()
    }

    @Test
    fun `server error is transient`() {
        val serverError = AuthorizationException.fromTemplate(AuthorizationException.GeneralErrors.SERVER_ERROR, IOException("503"))
        assertThat(AuthFlowOut.isTransientTokenError(serverError)).isTrue()
    }

    @Test
    fun `refused credentials are not transient`() {
        assertThat(AuthFlowOut.isTransientTokenError(AuthorizationException.TokenRequestErrors.INVALID_GRANT)).isFalse()
    }

    @Test
    fun `missing refresh token is not transient`() {
        // AppAuth reports this when there is nothing to refresh with, so a new login is really needed
        assertThat(AuthFlowOut.isTransientTokenError(AuthorizationException.AuthorizationRequestErrors.CLIENT_ERROR)).isFalse()
    }

    @Test
    fun `answer that is not JSON is transient`() {
        // What AppAuth reports when the answer is not JSON, for example a hotel login page or an HTML
        // error page of a proxy. The connection is bad, the saved login is not.
        assertThat(AuthFlowOut.isTransientTokenError(AuthorizationException.GeneralErrors.JSON_DESERIALIZATION_ERROR)).isTrue()
    }

    @Test
    fun `other general errors are not transient`() {
        // A general error that is not about the connection (here: bad ID token, type 0 code 9) must still
        // ask for a new login, otherwise the code check would do nothing
        assertThat(AuthFlowOut.isTransientTokenError(AuthorizationException.GeneralErrors.ID_TOKEN_VALIDATION_ERROR)).isFalse()
    }

    @Test
    fun `no error is not transient`() {
        assertThat(AuthFlowOut.isTransientTokenError(null)).isFalse()
    }
}

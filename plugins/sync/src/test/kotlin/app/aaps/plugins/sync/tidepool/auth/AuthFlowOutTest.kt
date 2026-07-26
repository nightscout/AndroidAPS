package app.aaps.plugins.sync.tidepool.auth

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.plugins.sync.tidepool.events.EventTidepoolStatus
import app.aaps.plugins.sync.tidepool.events.EventTidepoolUpdateGUI
import app.aaps.plugins.sync.tidepool.keys.TidepoolStringNonKey
import com.google.common.truth.Truth.assertThat
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
    private val rxBus: RxBus = mock()
    private val context: Context = RuntimeEnvironment.getApplication()

    private lateinit var sut: AuthFlowOut

    @Before
    fun setUp() {
        // initAuthState() reads these; default to "no stored state".
        whenever(preferences.get(TidepoolStringNonKey.ServiceConfiguration)).thenReturn("")
        whenever(preferences.get(TidepoolStringNonKey.AuthState)).thenReturn("")
        sut = AuthFlowOut(aapsLogger, preferences, context, cryptoUtil, rxBus)
    }

    private fun sentEvents(): List<Event> {
        val captor = argumentCaptor<Event>()
        verify(rxBus, atLeastOnce()).send(captor.capture())
        return captor.allValues
    }

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
    fun `updateConnectionStatus with a message emits a status event and a gui refresh`() {
        sut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.BLOCKED, "blocked!")
        val events = sentEvents()
        assertThat(events.filterIsInstance<EventTidepoolStatus>().map { it.status }).contains("blocked!")
        assertThat(events.any { it is EventTidepoolUpdateGUI }).isTrue()
    }

    @Test
    fun `updateConnectionStatus without a message emits only a gui refresh`() {
        sut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.FETCHING_TOKEN)
        val events = sentEvents()
        assertThat(events.filterIsInstance<EventTidepoolStatus>()).isEmpty()
        assertThat(events.any { it is EventTidepoolUpdateGUI }).isTrue()
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
        assertThat(sentEvents().filterIsInstance<EventTidepoolStatus>().map { it.status }).contains("bye")
        assertThat(sut.connectionStatus).isEqualTo(AuthFlowOut.ConnectionStatus.NOT_LOGGED_IN)
    }

    @Test
    fun `clearAllSavedData wipes the service configuration and erases the auth state`() {
        sut.clearAllSavedData()
        verify(preferences).put(TidepoolStringNonKey.ServiceConfiguration, "")
        verify(preferences).put(TidepoolStringNonKey.AuthState, "")
        assertThat(sentEvents().filterIsInstance<EventTidepoolStatus>().map { it.status }).contains("Credentials cleared")
    }

    @Test
    fun `initAuthState wipes a corrupt stored auth state`() {
        whenever(preferences.get(TidepoolStringNonKey.AuthState)).thenReturn("not-valid-json")
        sut.initAuthState()
        verify(preferences).put(TidepoolStringNonKey.AuthState, "")
        verify(preferences).put(TidepoolStringNonKey.ServiceConfiguration, "")
    }
}

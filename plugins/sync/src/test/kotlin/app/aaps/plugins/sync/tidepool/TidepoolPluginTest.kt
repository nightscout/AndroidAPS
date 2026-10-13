package app.aaps.plugins.sync.tidepool

import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.plugins.sync.nsclient.ReceiverDelegate
import app.aaps.plugins.sync.nsShared.events.EventConnectivityOptionChanged
import app.aaps.plugins.sync.tidepool.auth.AuthFlowOut
import app.aaps.plugins.sync.tidepool.comm.TidepoolUploader
import app.aaps.plugins.sync.tidepool.comm.UploadChunk
import app.aaps.plugins.sync.tidepool.utils.RateLimit
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import net.openid.appauth.AuthState
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TidepoolPluginTest : TestBaseWithProfile() {

    @Mock lateinit var tidepoolUploader: TidepoolUploader
    @Mock lateinit var uploadChunk: UploadChunk
    @Mock lateinit var receiverDelegate: ReceiverDelegate
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var authFlowOut: AuthFlowOut
    @Mock lateinit var l: L
    @Mock lateinit var authState: AuthState

    private lateinit var tidepoolPlugin: TidepoolPlugin
    private lateinit var rateLimit: RateLimit

    @BeforeEach fun prepare() {
        rateLimit = RateLimit(dateUtil)
        tidepoolPlugin = TidepoolPlugin(
            aapsLogger, rh, preferences, aapsSchedulers, rxBus, context, fabricPrivacy, tidepoolUploader, uploadChunk, rateLimit, receiverDelegate, uiInteraction, authFlowOut
        )
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        tidepoolPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }

    @Test
    fun notLoggedInStateTriggersLoginWhenConnectivityRestored() {
        whenever(authFlowOut.authState).thenReturn(authState)
        whenever(receiverDelegate.allowed).thenReturn(true)
        whenever(authFlowOut.connectionStatus).thenReturn(AuthFlowOut.ConnectionStatus.NOT_LOGGED_IN)

        val realUploader = TidepoolUploader(aapsLogger, rxBus, context, preferences, uploadChunk,
                                            dateUtil, receiverDelegate, config, l, authFlowOut)
        val plugin = TidepoolPlugin(aapsLogger, rh, preferences, aapsSchedulers, rxBus, context,
                                    fabricPrivacy, realUploader, uploadChunk, rateLimit,
                                    receiverDelegate, uiInteraction, authFlowOut)
        plugin.onStart()
        rxBus.send(EventConnectivityOptionChanged("Connected", true))

        verify(authFlowOut).updateConnectionStatus(eq(AuthFlowOut.ConnectionStatus.FETCHING_TOKEN), eq("Connecting"))
        plugin.onStop()
    }

    @Test
    fun uploadSkippedWhenConnectivityNotAllowed() {
        whenever(authFlowOut.authState).thenReturn(authState)
        whenever(receiverDelegate.allowed).thenReturn(false)
        whenever(authFlowOut.connectionStatus).thenReturn(AuthFlowOut.ConnectionStatus.NOT_LOGGED_IN)

        val realUploader = TidepoolUploader(aapsLogger, rxBus, context, preferences, uploadChunk,
                                            dateUtil, receiverDelegate, config, l, authFlowOut)
        val plugin = TidepoolPlugin(aapsLogger, rh, preferences, aapsSchedulers, rxBus, context,
                                    fabricPrivacy, realUploader, uploadChunk, rateLimit,
                                    receiverDelegate, uiInteraction, authFlowOut)
        plugin.onStart()
        rxBus.send(EventConnectivityOptionChanged("Blocked", false))

        // When connectivity is not allowed, doUpload should return early without attempting login
        verify(authFlowOut, never()).updateConnectionStatus(eq(AuthFlowOut.ConnectionStatus.FETCHING_TOKEN), any())
        plugin.onStop()
    }
}

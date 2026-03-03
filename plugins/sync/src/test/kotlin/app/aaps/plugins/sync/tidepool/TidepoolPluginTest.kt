package app.aaps.plugins.sync.tidepool

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.plugins.sync.nsclient.ReceiverDelegate
import app.aaps.plugins.sync.nsclient.ReceiverDelegate.ConnectivityStatus
import app.aaps.plugins.sync.tidepool.auth.AuthFlowOut
import app.aaps.plugins.sync.tidepool.comm.TidepoolUploader
import app.aaps.plugins.sync.tidepool.comm.UploadChunk
import app.aaps.plugins.sync.tidepool.compose.TidepoolRepository
import app.aaps.plugins.sync.tidepool.utils.RateLimit
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import net.openid.appauth.AuthState
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
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
    @Mock lateinit var tidepoolRepository: TidepoolRepository
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var l: L
    @Mock lateinit var authState: AuthState

    private lateinit var tidepoolPlugin: TidepoolPlugin
    private lateinit var rateLimit: RateLimit
    private val connectivityFlow = MutableStateFlow(ConnectivityStatus("Status not available", allowed = false, connected = false))

    @BeforeEach fun prepare() {
        rateLimit = RateLimit(dateUtil)
        whenever(receiverDelegate.connectivityStatusFlow).thenReturn(connectivityFlow)
        whenever(persistenceLayer.observeChanges(anyOrNull<Class<*>>())).thenReturn(emptyFlow())
        tidepoolPlugin = TidepoolPlugin(
            aapsLogger, rh, preferences, aapsSchedulers, rxBus, fabricPrivacy, tidepoolUploader, uploadChunk, rateLimit, receiverDelegate, authFlowOut, tidepoolRepository, dateUtil, persistenceLayer
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

        val realUploader = TidepoolUploader(
            aapsLogger, rxBus, context, preferences, uploadChunk,
            dateUtil, receiverDelegate, config, l, authFlowOut
        )
        val plugin = TidepoolPlugin(
            aapsLogger, rh, preferences, aapsSchedulers, rxBus,
            fabricPrivacy, realUploader, uploadChunk, rateLimit,
            receiverDelegate, authFlowOut, tidepoolRepository, dateUtil, persistenceLayer
        )
        plugin.onStart()
        Thread.sleep(500) // Ensure flow collector has started and processed initial value
        connectivityFlow.value = ConnectivityStatus("Connected", allowed = true, connected = true)
        Thread.sleep(1000) // Allow flow collector on Dispatchers.IO to process

        verify(authFlowOut).updateConnectionStatus(eq(AuthFlowOut.ConnectionStatus.FETCHING_TOKEN), eq("Connecting"))
        plugin.onStop()
    }

    @Test
    fun uploadSkippedWhenConnectivityNotAllowed() {
        whenever(authFlowOut.authState).thenReturn(authState)
        whenever(receiverDelegate.allowed).thenReturn(false)
        whenever(authFlowOut.connectionStatus).thenReturn(AuthFlowOut.ConnectionStatus.NOT_LOGGED_IN)

        val realUploader = TidepoolUploader(
            aapsLogger, rxBus, context, preferences, uploadChunk,
            dateUtil, receiverDelegate, config, l, authFlowOut
        )
        val plugin = TidepoolPlugin(
            aapsLogger, rh, preferences, aapsSchedulers, rxBus,
            fabricPrivacy, realUploader, uploadChunk, rateLimit,
            receiverDelegate, authFlowOut, tidepoolRepository, dateUtil, persistenceLayer
        )
        plugin.onStart()
        connectivityFlow.value = ConnectivityStatus("Blocked", allowed = false, connected = false)

        // When connectivity is not allowed, doUpload should return early without attempting login
        verify(authFlowOut, never()).updateConnectionStatus(eq(AuthFlowOut.ConnectionStatus.FETCHING_TOKEN), any())
        plugin.onStop()
    }
}

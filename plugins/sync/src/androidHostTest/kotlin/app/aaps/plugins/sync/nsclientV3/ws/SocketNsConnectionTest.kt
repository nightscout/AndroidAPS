package app.aaps.plugins.sync.nsclientV3.ws

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.plugins.sync.nsclientV3.DataSyncSelectorV3
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.ReceiverDelegate
import app.aaps.plugins.sync.nsclientV3.keys.NsclientBooleanKey
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * That a socket which throws on the way up does not take the plugin with it.
 *
 * socket.io reports a URL it cannot parse by returning null from the factory, which [SocketNsConnection]
 * has always handled. It reports a URL it *can* parse but cannot use by throwing instead, and that
 * half was lost in the move to shared code: `NSClientV3Service` on Android still catches
 * `RuntimeException` around the whole set-up, and so did dev before the split.
 *
 * It matters because of where `start` is called from. `NSClientV3Plugin.onStart` calls it early, so a
 * throw escaping here skipped the observers and the upload collector registered after it - leaving the
 * plugin enabled and half built, with nothing in the log to say why, until the app was restarted.
 */
class SocketNsConnectionTest : TestBaseWithProfile() {

    @Mock lateinit var receiverDelegate: ReceiverDelegate
    @Mock lateinit var dataSyncSelectorV3: DataSyncSelectorV3
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var nsClientRepository: NSClientRepository
    @Mock lateinit var nsSocketFactory: NsSocketFactory
    @Mock lateinit var nsFrameHandler: NsFrameHandler
    @Mock lateinit var nsConnectHandler: NsConnectHandler
    @Mock lateinit var l: L

    private lateinit var sut: SocketNsConnection

    @BeforeEach
    fun prepare() {
        // Everything `start` checks before it builds a socket, set so it gets that far.
        whenever(preferences.get(StringKey.NsClientUrl)).thenReturn("https://ns.example.com")
        whenever(preferences.get(BooleanKey.NsClient3UseWs)).thenReturn(true)
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAnnouncements)).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAlarms)).thenReturn(false)

        // `isAllowed` reads this. The plugin is final, so a real one with a mocked delegate is the
        // way to get a true out of it - the same construction the scheduling test uses.
        whenever(receiverDelegate.allowed).thenReturn(true)
        whenever(receiverDelegate.connectivityStatusFlow)
            .thenReturn(MutableStateFlow(ReceiverDelegate.ConnectivityStatus("", allowed = true, connected = true)))
        whenever(persistenceLayer.observeChanges(any<kotlin.reflect.KClass<*>>())).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeAnyChange()).thenReturn(emptyFlow())
        val nsLoadExecutor = mock<NsLoadExecutor>()
        whenever(nsLoadExecutor.idle).thenReturn(emptyFlow())
        val plugin = NSClientV3Plugin(
            aapsLogger, rh, preferences, rxBus,
            receiverDelegate, config, dateUtil, dataSyncSelectorV3, persistenceLayer,
            mock(), mock(), decimalFormatter, l, nsClientRepository, mock(),
            mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), nsLoadExecutor
        )

        sut = SocketNsConnection(
            aapsLogger, preferences, config, { plugin },
            nsFrameHandler, nsConnectHandler, nsClientRepository, nsSocketFactory
        )
    }

    @Test
    fun `a socket that throws is logged instead of escaping start`() {
        whenever(nsSocketFactory.create(any())).thenThrow(RuntimeException("bad transport"))

        // The assertion is that this returns at all. Before the guard it threw, and the throw
        // surfaced inside NSClientV3Plugin.onStart rather than here.
        sut.start("TEST")

        verify(nsClientRepository).addLog("● WS", "RuntimeException: bad transport")
    }

    /** A failed start must not leave a half-built connection behind that `start` would then skip. */
    @Test
    fun `a socket that throws leaves nothing holding a connection`() {
        whenever(nsSocketFactory.create(any())).thenThrow(RuntimeException("bad transport"))

        sut.start("TEST")

        assertThat(sut.hasLiveSocket).isFalse()
        assertThat(sut.socketConnected).isNull()
    }

    /** The URL it cannot parse at all still takes the older branch, not the new one. */
    @Test
    fun `an unusable url is reported as wrong syntax`() {
        whenever(nsSocketFactory.create(any())).thenReturn(null)

        sut.start("TEST")

        verify(nsClientRepository).addLog("● WS", "Wrong URL syntax")
    }
}

package app.aaps.plugins.sync.nsclientV3

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.plugins.sync.nsclientV3.keys.NsclientBooleanKey
import app.aaps.plugins.sync.nsclientV3.ws.NsConnection
import app.aaps.plugins.sync.nsclientV3.ws.NsLoadExecutor
import app.aaps.plugins.sync.nsclientV3.ws.NsLoadStep
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * When a load round is allowed to start, and what happens when one is asked for while another runs.
 *
 * None of this could be tested before: the plugin called `WorkManager.getInstance(context)`
 * statically, so a test could neither see what was enqueued nor pretend a round was already going.
 * With the scheduling behind [NsLoadExecutor] the decisions are visible.
 */
class NSClientV3PluginSchedulingTest : TestBaseWithProfile() {

    @Mock lateinit var nsLoadExecutor: NsLoadExecutor
    @Mock lateinit var nsConnection: NsConnection
    @Mock lateinit var receiverDelegate: ReceiverDelegate
    @Mock lateinit var dataSyncSelectorV3: DataSyncSelectorV3
    @Mock lateinit var nsClientRepository: NSClientRepository
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var l: L

    private lateinit var sut: NSClientV3Plugin

    @BeforeEach
    fun prepare() {
        whenever(nsLoadExecutor.idle).thenReturn(emptyFlow())
        whenever(nsConnection.connected).thenReturn(MutableStateFlow(false))
        whenever(receiverDelegate.connectivityStatusFlow)
            .thenReturn(MutableStateFlow(ReceiverDelegate.ConnectivityStatus("", allowed = true, connected = true)))
        whenever(receiverDelegate.allowed).thenReturn(true)
        whenever(persistenceLayer.observeChanges(any<kotlin.reflect.KClass<*>>())).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeAnyChange()).thenReturn(emptyFlow())
        sut = NSClientV3Plugin(
            aapsLogger, rh, preferences, rxBus,
            receiverDelegate, config, dateUtil, dataSyncSelectorV3, persistenceLayer,
            mock(), mock(), decimalFormatter, l, nsClientRepository, mock(),
            mock(), mock(), mock(), mock(), mock(), mock(), mock(), nsConnection, nsLoadExecutor
        )
    }

    /** The round is one chain under one name, and it ends with the upload step. */
    @Test
    fun `a load round runs the whole chain, ending with the upload`() = runTest {
        whenever(nsLoadExecutor.isRunning).thenReturn(false)

        sut.executeLoop("TEST")

        val captor = org.mockito.kotlin.argumentCaptor<List<NsLoadStep>>()
        verify(nsLoadExecutor).runChain(captor.capture())
        assertThat(captor.firstValue.first()).isEqualTo(NsLoadStep.STATUS)
        assertThat(captor.firstValue.last()).isEqualTo(NsLoadStep.DATA_SYNC)
        assertThat(captor.firstValue).containsNoDuplicates()
    }

    /**
     * A round asked for while one is in flight is queued, not dropped and not run beside it. Two
     * rounds would interleave on the same high-water marks.
     */
    @Test
    fun `a load asked for while a round is running does not start a second one`() = runTest {
        whenever(nsLoadExecutor.isRunning).thenReturn(true)

        sut.executeLoop("TEST")

        verify(nsLoadExecutor, never()).runChain(any())
    }

    @Test
    fun `an upload asked for while a round is running does not start a second one`() = runTest {
        whenever(nsLoadExecutor.isRunning).thenReturn(true)

        sut.executeUpload("TEST")

        verify(nsLoadExecutor, never()).runReplacing(any())
    }

    @Test
    fun `an upload on its own runs only the upload step`() = runTest {
        whenever(nsLoadExecutor.isRunning).thenReturn(false)

        sut.executeUpload("TEST")

        verify(nsLoadExecutor).runReplacing(NsLoadStep.DATA_SYNC)
    }

    /** Paused means nothing is scheduled at all, however the round was triggered. */
    @Test
    fun `nothing is scheduled while the client is paused`() = runTest {
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(true)

        sut.executeLoop("TEST")
        sut.executeUpload("TEST")

        verify(nsLoadExecutor, never()).runChain(any())
        verify(nsLoadExecutor, never()).runReplacing(any())
    }

    @Test
    fun `nothing is scheduled while the client is not allowed`() = runTest {
        whenever(receiverDelegate.allowed).thenReturn(false)

        sut.executeLoop("TEST")
        sut.executeUpload("TEST")

        verify(nsLoadExecutor, never()).runChain(any())
        verify(nsLoadExecutor, never()).runReplacing(any())
    }
}

package app.aaps.plugins.sync.nsclientV3.ws

import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.nssdk.interfaces.RunningConfiguration
import app.aaps.core.nssdk.remotemodel.LastModified
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.NsIncomingDataProcessor
import app.aaps.plugins.sync.nsclientV3.SettingsIdentifiers
import app.aaps.plugins.sync.nsclientV3.clientcontrol.ClientControlPublisher
import app.aaps.plugins.sync.nsclientV3.clientcontrol.OrphanDetector
import app.aaps.plugins.sync.nsclientV3.compose.NSClientRepositoryImpl
import app.aaps.plugins.sync.nsclientV3.data.NSDeviceStatusHandler
import app.aaps.shared.tests.TestBaseWithProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * That the shared connection routes the `settings` collection the way the Android service does.
 *
 * `settings` is how client control travels. It was in this class's subscribe list from the day the
 * class was written, with no branch to receive it, so every frame was dropped: pairing and every
 * command after it were inert on the two platforms that use this class - iOS and desktop - while
 * Android, which has its own [app.aaps.plugins.sync.nsclientV3.services.NSClientV3Service], worked.
 * Nothing failed loudly, because a `when` with no matching branch is not an error.
 *
 * These mirror the settings cases in `NSClientV3ServiceHandlersTest` deliberately. Two routings for
 * one protocol is what allowed them to drift in the first place, so the tests are written to be
 * compared side by side until one of the two implementations goes away.
 */
class NsFrameHandlerSettingsRoutingTest : TestBaseWithProfile() {

    @Mock lateinit var nsIncomingDataProcessor: NsIncomingDataProcessor
    @Mock lateinit var storeDataForDb: StoreDataForDb
    @Mock lateinit var nsDeviceStatusHandler: NSDeviceStatusHandler
    @Mock lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Mock lateinit var runningConfiguration: RunningConfiguration
    @Mock lateinit var orphanDetector: OrphanDetector

    private lateinit var sut: NsFrameHandler

    @BeforeEach
    fun init() {
        whenever(nsClientV3Plugin.lastLoadedSrvModified).thenReturn(LastModified(LastModified.Collections()))
        sut = NsFrameHandler(
            aapsLogger = aapsLogger,
            preferences = preferences,
            config = config,
            nsClientV3Plugin = { nsClientV3Plugin },
            nsIncomingDataProcessor = { nsIncomingDataProcessor },
            runningConfiguration = { runningConfiguration },
            orphanDetector = { orphanDetector },
            storeDataForDb = storeDataForDb,
            notificationManager = notificationManager,
            nsClientRepository = NSClientRepositoryImpl(rxBus, aapsLogger),
            nsDeviceStatusHandler = nsDeviceStatusHandler,
            dateUtil = dateUtil,
            appScope = CoroutineScope(Dispatchers.Unconfined)
        )
    }

    /** Wraps a doc the way the Nightscout websocket does: collection name plus the document. */
    private fun envelope(collection: String, doc: String): String =
        """{"colName":"$collection","doc":$doc}"""

    /**
     * An ACK identifier starts with the same prefix as a command envelope, so the ACK branch has to
     * be tested before the command branch. Getting this order wrong makes the receiver try to
     * verify an ack as an inbound command.
     */
    @Test
    fun `client routes an ack document to the ack handler and not to the command receiver`() = runTest {
        whenever(config.AAPSCLIENT).thenReturn(true)
        val identifier = ClientControlPublisher.IDENTIFIER_ACK_PREFIX + "123"

        sut.onDataCreateUpdate(envelope("settings", """{"identifier":"$identifier","srvModified":1000}"""))

        verify(nsClientV3Plugin).handleClientControlAckEvent(any())
        verify(nsClientV3Plugin, never()).handleClientControlSettingsEvent(any(), any())
    }

    /** The live bolus-progress mirror shares the ACK's ordering rule for the same reason. */
    @Test
    fun `client routes a progress document to the progress handler`() = runTest {
        whenever(config.AAPSCLIENT).thenReturn(true)
        val identifier = ClientControlPublisher.IDENTIFIER_PROGRESS_PREFIX + "p1"

        sut.onDataCreateUpdate(envelope("settings", """{"identifier":"$identifier","srvModified":1000}"""))

        verify(nsClientV3Plugin).handleClientControlProgressEvent(any())
        verify(nsClientV3Plugin, never()).handleClientControlSettingsEvent(any(), any())
    }

    /** The one that makes pairing work: a master has to receive the client's command envelope. */
    @Test
    fun `master routes a command document to the command receiver`() = runTest {
        whenever(config.AAPSCLIENT).thenReturn(false)
        val identifier = ClientControlPublisher.IDENTIFIER_PREFIX + "cmd1"

        sut.onDataCreateUpdate(envelope("settings", """{"identifier":"$identifier","srvModified":1000}"""))

        verify(nsClientV3Plugin).handleClientControlSettingsEvent(eq(identifier), any())
    }

    /**
     * Nightscout echoes every write back to whoever made it. A client must not pick up its own
     * outgoing command: the master would see an unknown clientId and tombstone the document.
     */
    @Test
    fun `client ignores the echo of its own outgoing command`() = runTest {
        whenever(config.AAPSCLIENT).thenReturn(true)
        val identifier = ClientControlPublisher.IDENTIFIER_PREFIX + "cmd1"

        sut.onDataCreateUpdate(envelope("settings", """{"identifier":"$identifier","srvModified":1000}"""))

        verify(nsClientV3Plugin, never()).handleClientControlSettingsEvent(any(), any())
    }

    /** A cold config doc is applied and feeds the master-liveness clock. */
    @Test
    fun `client applies a cold config document`() = runTest {
        whenever(config.AAPSCLIENT).thenReturn(true)
        val doc = """{"identifier":"${SettingsIdentifiers.COLD}","srvModified":1000,"runningConfig":{"version":"1.0"}}"""

        sut.onDataCreateUpdate(envelope("settings", doc))

        verify(runningConfiguration).applyCold(any())
        verify(nsClientV3Plugin).bumpMasterSignal(1000L)
        verify(orphanDetector).onSettingsDoc(any(), eq(1000L))
    }

    /** The hot doc goes to applyHot, never applyCold - applyCold would clear a running scene. */
    @Test
    fun `client applies a hot state document without touching the cold path`() = runTest {
        whenever(config.AAPSCLIENT).thenReturn(true)
        val doc = """{"identifier":"${SettingsIdentifiers.STATE}","srvModified":2000,"runningConfig":{"version":"1.0"}}"""

        sut.onDataCreateUpdate(envelope("settings", doc))

        verify(runningConfiguration).applyHot(any())
        verify(runningConfiguration, never()).applyCold(any())
        verify(nsClientV3Plugin).bumpMasterSignal(2000L)
    }

    /** A master must not adopt config pushed at it; the cold and hot branches are client-only. */
    @Test
    fun `master ignores a cold config document`() = runTest {
        whenever(config.AAPSCLIENT).thenReturn(false)
        val doc = """{"identifier":"${SettingsIdentifiers.COLD}","srvModified":1000,"runningConfig":{"version":"1.0"}}"""

        sut.onDataCreateUpdate(envelope("settings", doc))

        verify(runningConfiguration, never()).applyCold(any())
    }
}

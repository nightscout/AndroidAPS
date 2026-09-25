package app.aaps.plugins.sync.nsclientV3.services

import app.aaps.core.nssdk.interfaces.RunningConfiguration
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.LongComposedKey
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.NsIncomingDataProcessor
import app.aaps.plugins.sync.nsclientV3.SettingsIdentifiers
import app.aaps.plugins.sync.nsclientV3.clientcontrol.ClientControlPublisher
import app.aaps.plugins.sync.nsclientV3.clientcontrol.OrphanDetector
import app.aaps.plugins.sync.nsclientV3.compose.NSClientRepositoryImpl
import app.aaps.plugins.sync.nsclientV3.data.NSDeviceStatusHandler
import app.aaps.plugins.sync.nsclientV3.ws.NsFrameHandler
import app.aaps.core.nssdk.remotemodel.LastModified
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.mockingDetails
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Pins what the websocket event handlers do with an incoming document.
 *
 * These are characterization tests: they describe the behaviour that is live today, so the planned
 * move of the handlers to commonMain (and the org.json to kotlinx swap underneath them) can be
 * checked against something. The lifecycle of the sockets themselves is covered by
 * [NSClientV3ServiceTest]; nothing here connects anything.
 */
class NSClientV3ServiceHandlersTest : TestBaseWithProfile() {

    @Mock lateinit var nsIncomingDataProcessor: NsIncomingDataProcessor
    @Mock lateinit var storeDataForDb: StoreDataForDb
    @Mock lateinit var nsDeviceStatusHandler: NSDeviceStatusHandler
    @Mock lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Mock lateinit var runningConfiguration: RunningConfiguration
    @Mock lateinit var orphanDetector: OrphanDetector

    private lateinit var sut: NSClientV3Service
    private val wsConnectedState = MutableStateFlow(false)
    private lateinit var lastModified: LastModified

    @BeforeEach
    fun init() {
        lastModified = LastModified(LastModified.Collections())
        whenever(nsClientV3Plugin.wsConnectedFlow).thenReturn(wsConnectedState)
        whenever(nsClientV3Plugin.lastLoadedSrvModified).thenReturn(lastModified)
        sut = NSClientV3Service().also {
            it.aapsLogger = aapsLogger
            it.preferences = preferences
            it.fabricPrivacy = fabricPrivacy
            it.nsClientV3Plugin = nsClientV3Plugin
            it.config = config
            it.nsIncomingDataProcessor = nsIncomingDataProcessor
            it.storeDataForDb = storeDataForDb
            it.notificationManager = notificationManager
            it.nsDeviceStatusHandler = nsDeviceStatusHandler
            it.nsClientRepository = NSClientRepositoryImpl(rxBus, aapsLogger)
            it.runningConfiguration = runningConfiguration
            it.orphanDetector = orphanDetector
            it.appScope = CoroutineScope(Dispatchers.Unconfined)
            // The real shared handler, not a mock. That is the point of this file now: every case
            // below was written against the service's own routing, and they all still have to pass
            // against the one implementation that replaced it.
            it.nsFrameHandler = NsFrameHandler(
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
    }

    /** Wraps a doc the way the Nightscout websocket does: collection name plus the document. */
    private fun envelope(collection: String, doc: String): String =
        """{"colName":"$collection","doc":$doc}"""

    private fun sgvDoc(srvModified: Long = 1000L) =
        """{"identifier":"abc","srvModified":$srvModified,"date":1000,"sgv":100,"units":"mg/dl","device":"test","type":"sgv"}"""

    // ---------------------------------------------------------------- create / update

    @Test
    fun `entries document is processed as glucose and queued for storage`() = runTest {
        sut.onDataCreateUpdate(envelope("entries", sgvDoc()))

        verify(nsIncomingDataProcessor).processSgvs(any(), eq(false))
        verify(storeDataForDb).requestStoreGlucoseValues()
    }

    @Test
    fun `treatments document is processed and queued without full sync`() = runTest {
        val doc = """{"identifier":"t1","srvModified":1000,"date":1000,"eventType":"Note","notes":"x"}"""

        sut.onDataCreateUpdate(envelope("treatments", doc))

        verify(storeDataForDb).requestStoreTreatments(fullSync = false)
    }

    @Test
    fun `devicestatus document goes to the device status handler as live data`() = runTest {
        val doc = """{"identifier":"d1","srvModified":1000,"created_at":"2024-01-01T00:00:00Z"}"""

        sut.onDataCreateUpdate(envelope("devicestatus", doc))

        verify(nsDeviceStatusHandler).handleNewData(any(), eq(true))
    }

    /**
     * The high-water mark must not move while the catch-up load after a (re)connect is still
     * running. If it did, the Load*Worker chain would ask for "modified since <just bumped>" and
     * skip exactly the offline window it is there to backfill.
     */
    @Test
    fun `srvModified is not advanced while the initial load is still running`() = runTest {
        whenever(nsClientV3Plugin.initialLoadFinished).thenReturn(false)

        sut.onDataCreateUpdate(envelope("entries", sgvDoc(srvModified = 5000L)))

        assertThat(lastModified.collections.entries).isEqualTo(0L)
        verify(nsClientV3Plugin, never()).storeLastLoadedSrvModified()
    }

    @Test
    fun `srvModified is advanced and stored once the initial load has finished`() = runTest {
        whenever(nsClientV3Plugin.initialLoadFinished).thenReturn(true)

        sut.onDataCreateUpdate(envelope("entries", sgvDoc(srvModified = 5000L)))

        assertThat(lastModified.collections.entries).isEqualTo(5000L)
        verify(nsClientV3Plugin).storeLastLoadedSrvModified()
    }

    // ------------------------------------------------- settings / client control routing

    /**
     * An ACK identifier starts with the same prefix as a command envelope, so the ACK branch has to
     * be tested before the command branch. Getting this order wrong makes the receiver try to
     * verify an ack as an inbound command.
     */
    @Test
    fun `client routes an ack document to the ack handler and not to the command receiver`() = runTest {
        whenever(config.AAPSCLIENT).thenReturn(true)
        val identifier = ClientControlPublisher.IDENTIFIER_ACK_PREFIX + "123"
        val doc = """{"identifier":"$identifier","srvModified":1000}"""

        sut.onDataCreateUpdate(envelope("settings", doc))

        verify(nsClientV3Plugin).handleClientControlAckEvent(any())
        verify(nsClientV3Plugin, never()).handleClientControlSettingsEvent(any(), any())
    }

    @Test
    fun `master routes a command document to the command receiver`() = runTest {
        whenever(config.AAPSCLIENT).thenReturn(false)
        val identifier = ClientControlPublisher.IDENTIFIER_PREFIX + "cmd1"
        val doc = """{"identifier":"$identifier","srvModified":1000}"""

        sut.onDataCreateUpdate(envelope("settings", doc))

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
        val doc = """{"identifier":"$identifier","srvModified":1000}"""

        sut.onDataCreateUpdate(envelope("settings", doc))

        verify(nsClientV3Plugin, never()).handleClientControlSettingsEvent(any(), any())
    }

    // The four below have twins in SocketNsConnectionSettingsRoutingTest and
    // SocketNsConnectionHandlersTest. They were missing here, which is how the two routings were
    // able to drift: a case covered on one side only proves nothing about the other.

    /** The live bolus-progress mirror shares the ACK's ordering rule for the same reason. */
    @Test
    fun `client routes a progress document to the progress handler`() = runTest {
        whenever(config.AAPSCLIENT).thenReturn(true)
        val identifier = ClientControlPublisher.IDENTIFIER_PROGRESS_PREFIX + "p1"
        val doc = """{"identifier":"$identifier","srvModified":1000}"""

        sut.onDataCreateUpdate(envelope("settings", doc))

        verify(nsClientV3Plugin).handleClientControlProgressEvent(any())
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

    @Test
    fun `profile document is processed without full sync`() = runTest {
        val doc = """{"identifier":"p1","srvModified":1000,"defaultProfile":"x","store":{}}"""

        sut.onDataCreateUpdate(envelope("profile", doc))

        verify(nsIncomingDataProcessor).processProfile(any(), eq(false))
    }

    // ------------------------------------------------------------------------- delete

    @Test
    fun `deleted treatment is queued for removal`() = runTest {
        sut.onDataDelete("""{"colName":"treatments","identifier":"t1"}""")

        verify(storeDataForDb).addToDeleteTreatment("t1")
        verify(storeDataForDb).requestUpdateDeletedTreatments()
    }

    @Test
    fun `deleted entry is queued for removal`() = runTest {
        sut.onDataDelete("""{"colName":"entries","identifier":"e1"}""")

        verify(storeDataForDb).addToDeleteGlucoseValue("e1")
        verify(storeDataForDb).requestUpdateDeletedGlucoseValues()
    }

    /** A document with no collection name matches no branch. It must not delete anything. */
    @Test
    fun `delete without a collection name removes nothing`() = runTest {
        sut.onDataDelete("""{"identifier":"x1"}""")

        assertThat(mockingDetails(storeDataForDb).invocations).isEmpty()
    }

    // ------------------------------------------------------------------------- alarms

    @Test
    fun `alarm is ignored when alarm notifications are switched off`() = runTest {
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAlarms)).thenReturn(false)

        sut.onAlarm("""{"level":1,"title":"Warning HIGH","message":"m"}""")

        assertThat(mockingDetails(notificationManager).invocations).isEmpty()
    }

    @Test
    fun `alarm is shown when notifications are on and it is not snoozed`() = runTest {
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAlarms)).thenReturn(true)
        whenever(preferences.get(eq(LongComposedKey.NotificationSnoozedTo), any())).thenReturn(0L)

        sut.onAlarm("""{"level":1,"title":"Warning HIGH","message":"m"}""")

        assertThat(mockingDetails(notificationManager).invocations).isNotEmpty()
    }

    @Test
    fun `alarm is suppressed while its snooze is still running`() = runTest {
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAlarms)).thenReturn(true)
        whenever(preferences.get(eq(LongComposedKey.NotificationSnoozedTo), any()))
            .thenReturn(System.currentTimeMillis() + 60_000L)

        sut.onAlarm("""{"level":1,"title":"Warning HIGH","message":"m"}""")

        assertThat(mockingDetails(notificationManager).invocations).isEmpty()
    }

    @Test
    fun `announcement is ignored when announcement notifications are switched off`() = runTest {
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAnnouncements)).thenReturn(false)

        sut.onAnnouncement("""{"level":0,"title":"Announcement","message":"m"}""")

        assertThat(mockingDetails(notificationManager).invocations).isEmpty()
    }

    @Test
    fun `clear alarm dismisses both the alarm and the urgent alarm`() = runTest {
        sut.onClearAlarm("""{"clear":true,"title":"All Clear","message":"m"}""")

        verify(notificationManager).dismiss(NotificationId.NS_ALARM)
        verify(notificationManager).dismiss(NotificationId.NS_URGENT_ALARM)
    }
}

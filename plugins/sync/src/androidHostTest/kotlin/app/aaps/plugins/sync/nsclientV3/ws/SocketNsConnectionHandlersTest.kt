package app.aaps.plugins.sync.nsclientV3.ws

import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.nssdk.interfaces.RunningConfiguration
import app.aaps.core.nssdk.remotemodel.LastModified
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.NsIncomingDataProcessor
import app.aaps.plugins.sync.nsclientV3.clientcontrol.OrphanDetector
import app.aaps.plugins.sync.nsclientV3.compose.NSClientRepositoryImpl
import app.aaps.plugins.sync.nsclientV3.data.NSDeviceStatusHandler
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.mockingDetails
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * What the shared connection does with an incoming frame, collection by collection.
 *
 * These are characterization tests, and they exist for one job: the routing here duplicates
 * `NSClientV3Service` on Android, the two are to be merged into one shared implementation, and a
 * merge is only safe if both sides are pinned first. Every case below is deliberately the twin of
 * one in `NSClientV3ServiceHandlersTest`, so the two files can be read side by side and any drift
 * shows up as a test that exists on one side only.
 *
 * The `settings` collection has its own file, [SocketNsConnectionSettingsRoutingTest], because it
 * carries client control and needed more cases than the rest put together.
 */
class SocketNsConnectionHandlersTest : TestBaseWithProfile() {

    @Mock lateinit var nsIncomingDataProcessor: NsIncomingDataProcessor
    @Mock lateinit var storeDataForDb: StoreDataForDb
    @Mock lateinit var nsDeviceStatusHandler: NSDeviceStatusHandler
    @Mock lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Mock lateinit var runningConfiguration: RunningConfiguration
    @Mock lateinit var orphanDetector: OrphanDetector
    @Mock lateinit var nsSocketFactory: NsSocketFactory

    private lateinit var sut: SocketNsConnection
    private lateinit var lastModified: LastModified

    @BeforeEach
    fun init() {
        lastModified = LastModified(LastModified.Collections())
        whenever(nsClientV3Plugin.lastLoadedSrvModified).thenReturn(lastModified)
        sut = SocketNsConnection(
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
            nsSocketFactory = nsSocketFactory,
            dateUtil = dateUtil,
            appScope = CoroutineScope(Dispatchers.Unconfined)
        )
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

    /** Not covered on the Android side; pinned here so the merged version keeps it. */
    @Test
    fun `profile document is processed without full sync`() = runTest {
        val doc = """{"identifier":"p1","srvModified":1000,"defaultProfile":"x","store":{}}"""

        sut.onDataCreateUpdate(envelope("profile", doc))

        verify(nsIncomingDataProcessor).processProfile(any(), eq(false))
    }

    /**
     * `srvModified` is mandatory in a Nightscout v3 document, so a frame without one is malformed
     * and is dropped rather than routed with an invented value. Defaulting it to 0 was not inert:
     * 0 would be written to the high-water mark, and `OrphanDetector.onSettingsDoc` reads 0 as
     * "no timestamp, skip the race guard" - one malformed frame could declare a freshly paired
     * client an orphan.
     */
    @Test
    fun `a document with no srvModified is dropped, not routed`() = runTest {
        whenever(nsClientV3Plugin.initialLoadFinished).thenReturn(true)

        sut.onDataCreateUpdate(envelope("entries", """{"identifier":"abc","date":1000,"sgv":100}"""))

        verify(nsIncomingDataProcessor, never()).processSgvs(any(), any())
        verify(nsClientV3Plugin, never()).storeLastLoadedSrvModified()
        assertThat(lastModified.collections.entries).isEqualTo(0L)
    }

    @Test
    fun `an unknown collection is ignored`() = runTest {
        sut.onDataCreateUpdate(envelope("somethingelse", """{"identifier":"x","srvModified":1000}"""))

        assertThat(mockingDetails(storeDataForDb).invocations).isEmpty()
        assertThat(mockingDetails(nsDeviceStatusHandler).invocations).isEmpty()
    }

    // ------------------------------------------------------------ high-water mark

    /**
     * The mark must not move while the catch-up load after a (re)connect is still running. If it
     * did, the next round would ask for "modified since <just bumped>" and skip exactly the offline
     * window it is there to backfill.
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

        sut.onAlarm("""{"level":1,"title":"Warning HIGH","message":"m"}""", BooleanKey.NsClientNotificationsFromAlarms)

        assertThat(mockingDetails(notificationManager).invocations).isEmpty()
    }

    @Test
    fun `alarm is shown when notifications are on`() = runTest {
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAlarms)).thenReturn(true)

        sut.onAlarm("""{"level":1,"title":"Warning HIGH","message":"m"}""", BooleanKey.NsClientNotificationsFromAlarms)

        assertThat(mockingDetails(notificationManager).invocations).isNotEmpty()
    }

    /**
     * An alarm the user silenced must stay silenced. This implementation used to check only the
     * on/off preference, so a snoozed alarm came back on the next push - on iOS and desktop only,
     * because Android has always read [LongComposedKey.NotificationSnoozedTo].
     */
    @Test
    fun `alarm is suppressed while its snooze is still running`() = runTest {
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAlarms)).thenReturn(true)
        whenever(preferences.get(eq(LongComposedKey.NotificationSnoozedTo), any())).thenReturn(2_000L)
        whenever(dateUtil.now()).thenReturn(1_000L)   // still inside the snooze

        sut.onAlarm("""{"level":1,"title":"Warning HIGH","message":"m"}""", BooleanKey.NsClientNotificationsFromAlarms)

        assertThat(mockingDetails(notificationManager).invocations).isEmpty()
    }

    /** ...and comes back once the snooze has run out. */
    @Test
    fun `alarm is shown again after its snooze has expired`() = runTest {
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAlarms)).thenReturn(true)
        whenever(preferences.get(eq(LongComposedKey.NotificationSnoozedTo), any())).thenReturn(2_000L)
        whenever(dateUtil.now()).thenReturn(3_000L)   // past it

        sut.onAlarm("""{"level":1,"title":"Warning HIGH","message":"m"}""", BooleanKey.NsClientNotificationsFromAlarms)

        assertThat(mockingDetails(notificationManager).invocations).isNotEmpty()
    }

    /**
     * The key is composed with the level, so silencing a warning must not silence an urgent alarm.
     * Pinned because the composed argument is easy to drop in a refactor and nothing else would say.
     */
    @Test
    fun `the snooze is looked up per alarm level`() = runTest {
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAlarms)).thenReturn(true)
        whenever(preferences.get(eq(LongComposedKey.NotificationSnoozedTo), any())).thenReturn(0L)

        sut.onAlarm("""{"level":2,"title":"Urgent HIGH","message":"m"}""", BooleanKey.NsClientNotificationsFromAlarms)

        verify(preferences).get(LongComposedKey.NotificationSnoozedTo, "2")
    }

    // ------------------------------------------------------------------ snooze actions

    /**
     * Ported from Android. Without these there was no way to snooze or acknowledge a Nightscout
     * alarm anywhere but Android: this is the only writer of the snooze key that [onAlarm] reads,
     * and the only caller of `handleClearAlarm`, which acks the alarm back to Nightscout.
     */
    @Test
    fun `an alarm offers three snooze actions`() = runTest {
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAlarms)).thenReturn(true)
        whenever(preferences.get(eq(LongComposedKey.NotificationSnoozedTo), any())).thenReturn(0L)

        sut.onAlarm("""{"level":1,"title":"Warning HIGH","message":"m"}""", BooleanKey.NsClientNotificationsFromAlarms)

        val actions = argumentCaptor<List<NotificationAction>>()
        verify(notificationManager).post(
            id = eq(NotificationId.NS_ALARM), text = any(), level = any(),
            validMinutes = any(), sound = any(), actions = actions.capture(), validityCheck = anyOrNull()
        )
        assertThat(actions.firstValue).hasSize(3)
    }

    /** Acting on one acks the alarm back to Nightscout with the chosen interval. */
    @Test
    fun `snoozing acknowledges the alarm with the chosen interval`() = runTest {
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAlarms)).thenReturn(true)
        whenever(preferences.get(eq(LongComposedKey.NotificationSnoozedTo), any())).thenReturn(0L)
        whenever(dateUtil.now()).thenReturn(1_000L)

        sut.onAlarm("""{"level":1,"title":"Warning HIGH","message":"m"}""", BooleanKey.NsClientNotificationsFromAlarms)
        capturedAlarmActions().first().action()   // the 15 minute button

        verify(nsClientV3Plugin).handleClearAlarm(any(), eq(15 * 60 * 1000L))
    }

    /**
     * The snooze is written for every level, not just the one that fired. Nightscout cascades a
     * level-2 ack down to level 1 but keeps emitting lower-level forecast alarms, which would
     * otherwise slip past a single-level snooze and re-alarm.
     *
     * This is also what makes the read in [onAlarm] work at all: it looks the snooze up by the
     * frame's level, so a level-1 alarm only stays quiet because "1" was written here too.
     */
    @Test
    fun `snoozing writes the deadline for every alarm level`() = runTest {
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAlarms)).thenReturn(true)
        whenever(preferences.get(eq(LongComposedKey.NotificationSnoozedTo), any())).thenReturn(0L)
        whenever(dateUtil.now()).thenReturn(1_000L)

        sut.onAlarm("""{"level":2,"title":"Urgent HIGH","message":"m"}""", BooleanKey.NsClientNotificationsFromAlarms)
        capturedAlarmActions()[1].action()   // the 30 minute button

        val deadline = 1_000L + 30 * 60 * 1000L
        for (level in 0..2)
            verify(preferences).put(LongComposedKey.NotificationSnoozedTo, level.toString(), value = deadline)
    }

    private fun capturedAlarmActions(): List<NotificationAction> {
        val actions = argumentCaptor<List<NotificationAction>>()
        verify(notificationManager).post(
            id = any(), text = any(), level = any(),
            validMinutes = any(), sound = any(), actions = actions.capture(), validityCheck = anyOrNull()
        )
        return actions.firstValue
    }

    @Test
    fun `announcement is ignored when announcement notifications are switched off`() = runTest {
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAnnouncements)).thenReturn(false)

        sut.onAnnouncement("""{"level":0,"title":"t","message":"m"}""")

        assertThat(mockingDetails(notificationManager).invocations).isEmpty()
    }

    @Test
    fun `clear alarm dismisses both the alarm and the urgent alarm`() = runTest {
        sut.onClearAlarm("""{"clear":true,"title":"All Clear","message":"m"}""")

        verify(notificationManager).dismiss(NotificationId.NS_ALARM)
        verify(notificationManager).dismiss(NotificationId.NS_URGENT_ALARM)
    }
}

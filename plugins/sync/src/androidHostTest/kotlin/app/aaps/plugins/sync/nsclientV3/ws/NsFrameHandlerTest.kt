package app.aaps.plugins.sync.nsclientV3.ws

import app.aaps.core.interfaces.nsclient.StoreDataForDb
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
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
 * The `settings` collection has its own file, [NsFrameHandlerSettingsRoutingTest], because it
 * carries client control and needed more cases than the rest put together.
 */
class NsFrameHandlerTest : TestBaseWithProfile() {

    @Mock lateinit var nsIncomingDataProcessor: NsIncomingDataProcessor
    @Mock lateinit var storeDataForDb: StoreDataForDb
    @Mock lateinit var nsDeviceStatusHandler: NSDeviceStatusHandler
    @Mock lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Mock lateinit var runningConfiguration: RunningConfiguration
    @Mock lateinit var orphanDetector: OrphanDetector

    private lateinit var sut: NsFrameHandler
    private lateinit var lastModified: LastModified

    @BeforeEach
    fun init() {
        lastModified = LastModified(LastModified.Collections())
        whenever(nsClientV3Plugin.lastLoadedSrvModified).thenReturn(lastModified)
        sut = NsFrameHandler(
            aapsLogger = aapsLogger,
            config = config,
            nsClientV3Plugin = { nsClientV3Plugin },
            nsIncomingDataProcessor = { nsIncomingDataProcessor },
            runningConfiguration = { runningConfiguration },
            orphanDetector = { orphanDetector },
            clientKeyExchange = { mock() },
            masterDocVerifier = { mock() },
            storeDataForDb = storeDataForDb,
            nsClientRepository = NSClientRepositoryImpl(rxBus, aapsLogger),
            nsDeviceStatusHandler = nsDeviceStatusHandler,
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

        verify(nsIncomingDataProcessor).processSgvs(any(), eq(false), eq(false))
        verify(storeDataForDb).requestStoreGlucoseValues()
    }

    @Test
    fun `treatments document is processed and queued without full sync`() = runTest {
        val doc = """{"identifier":"t1","srvModified":1000,"date":1000,"eventType":"Note","notes":"x"}"""

        sut.onDataCreateUpdate(envelope("treatments", doc))

        verify(storeDataForDb).requestStoreTreatments(fullSync = false)
    }

    /**
     * A live frame must never ask for the first-load cursor to be advanced.
     *
     * The record carries the time of "now". During a first load, letting it move the cursor makes
     * the REST load ask for "treatments since now" instead of since now minus 100 days, so the
     * whole history behind it is never read - and because the profile switch lives in that history,
     * the client ends up with no active profile. The same frame also reaches the high-water mark,
     * which is already guarded for this reason; this cursor was missed.
     */
    @Test
    fun `a live entries frame does not advance the first load cursor`() = runTest {
        sut.onDataCreateUpdate(envelope("entries", sgvDoc()))

        verify(nsIncomingDataProcessor).processSgvs(any(), eq(false), eq(false))
    }

    @Test
    fun `a live treatments frame does not advance the first load cursor`() = runTest {
        val doc = """{"identifier":"t1","srvModified":1000,"date":1000,"eventType":"Note","notes":"x"}"""

        sut.onDataCreateUpdate(envelope("treatments", doc))

        verify(nsIncomingDataProcessor).processTreatments(any(), eq(false), eq(false))
    }

    @Test
    fun `devicestatus document goes to the device status handler as live data`() = runTest {
        val doc = """{"identifier":"d1","srvModified":1000,"created_at":"2024-01-01T00:00:00Z"}"""

        sut.onDataCreateUpdate(envelope("devicestatus", doc))

        verify(nsDeviceStatusHandler).handleNewData(any(), eq(true))
    }

    /**
     * Seen on 4.0.0-dev-e: an uploader that writes `"uploader":"GG-Host"`, a name where an object is
     * expected. Every such document crashed the app. It is now read, with no uploader battery.
     */
    @Test
    fun `devicestatus with the uploader written as a string is still handled`() = runTest {
        val doc = """{"identifier":"d1","srvModified":1000,"uploaderBattery":100,"uploader":"GG-Host"}"""

        sut.onDataCreateUpdate(envelope("devicestatus", doc))

        verify(nsDeviceStatusHandler).handleNewData(any(), eq(true))
    }

    /**
     * Any uploader can write to Nightscout, so a document AAPS cannot decode is dropped. It used to
     * throw out of the socket.io listener and crash the app.
     */
    @Test
    fun `a devicestatus that cannot be decoded is dropped, not thrown`() = runTest {
        sut.onDataCreateUpdate(envelope("devicestatus", """{"identifier":"d1","srvModified":1000,"pump":"broken"}"""))

        verify(nsDeviceStatusHandler, never()).handleNewData(any(), any())
    }

    /** The same for every collection that is decoded into a model, not only device status. */
    @Test
    fun `a treatment that cannot be decoded is dropped, not thrown`() = runTest {
        sut.onDataCreateUpdate(envelope("treatments", """{"identifier":"t1","srvModified":1000,"date":{"x":1},"eventType":"Note"}"""))

        verify(storeDataForDb, never()).requestStoreTreatments(any())
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

        verify(nsIncomingDataProcessor, never()).processSgvs(any(), any(), any())
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
        whenever(nsIncomingDataProcessor.acceptsGlucose(false)).thenReturn(true)

        sut.onDataDelete("""{"colName":"entries","identifier":"e1"}""")

        verify(storeDataForDb).addToDeleteGlucoseValue("e1")
        verify(storeDataForDb).requestUpdateDeletedGlucoseValues()
    }

    /**
     * A phone that does not take BG from Nightscout must not take BG deletes from there either. This path
     * had no check at all, so anyone who could write to Nightscout could remove a master's readings.
     */
    @Test
    fun `deleted entry is ignored when BG is not taken from Nightscout`() = runTest {
        whenever(nsIncomingDataProcessor.acceptsGlucose(false)).thenReturn(false)

        sut.onDataDelete("""{"colName":"entries","identifier":"e1"}""")

        verify(storeDataForDb, never()).addToDeleteGlucoseValue(any())
        verify(storeDataForDb, never()).requestUpdateDeletedGlucoseValues()
    }

    /** A document with no collection name matches no branch. It must not delete anything. */
    @Test
    fun `delete without a collection name removes nothing`() = runTest {
        sut.onDataDelete("""{"identifier":"x1"}""")

        assertThat(mockingDetails(storeDataForDb).invocations).isEmpty()
    }


}

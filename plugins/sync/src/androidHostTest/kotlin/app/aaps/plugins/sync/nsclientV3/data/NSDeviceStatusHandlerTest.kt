package app.aaps.plugins.sync.nsclientV3.data

import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNsClientStatusUpdated
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.localmodel.devicestatus.NSDeviceStatus
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import kotlin.time.Instant

internal class NSDeviceStatusHandlerTest {

    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var config: Config
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var processedDeviceStatusData: ProcessedDeviceStatusData
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var overviewData: OverviewData
    @Mock private lateinit var calculationWorkflow: CalculationWorkflow
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Mock private lateinit var iobCobCalculator: IobCobCalculator

    /** What the client makes of a suggested result: one per time, see [givenResults]. */
    private val apsResultFactory = mock<APSResult>()
    private val openAPSData = ProcessedDeviceStatusData.OpenAPSData()

    private lateinit var sut: NSDeviceStatusHandler

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(config.AAPSCLIENT).thenReturn(true)
        whenever(processedDeviceStatusData.openAPSData).thenReturn(openAPSData)
        runBlocking { whenever(persistenceLayer.getLoopSensitivities(any(), any())).thenReturn(emptyList()) }
        sut = NSDeviceStatusHandler(
            preferences, config, dateUtil, processedDeviceStatusData, aapsLogger,
            persistenceLayer, overviewData, calculationWorkflow, rxBus,
            CoroutineScope(Dispatchers.Unconfined), { nsClientV3Plugin }, { apsResultFactory }, { iobCobCalculator }
        )
    }

    /**
     * Regression: the master-alive heartbeat must be the NEWEST devicestatus's own created_at, NOT the
     * receipt time. Otherwise a stale historical devicestatus pulled by the catch-up worker at app start
     * (master already offline) would falsely mark the master freshly alive and unlock client editing.
     */
    @Test
    fun bumpsHeartbeatWithNewestCreatedAtNotReceiptTime() {
        whenever(dateUtil.now()).thenReturn(9_999L)                       // receipt time — must NOT be used
        whenever(dateUtil.fromISODateString("old")).thenReturn(1_000L)
        whenever(dateUtil.fromISODateString("new")).thenReturn(5_000L)

        sut.handleNewData(arrayOf(NSDeviceStatus(createdAt = "old"), NSDeviceStatus(createdAt = "new")), live = true)

        verify(nsClientV3Plugin).bumpDevicestatusHeartbeat(5_000L)
        verify(nsClientV3Plugin, never()).bumpDevicestatusHeartbeat(9_999L)
    }

    /**
     * Core of the agreed design: only a LIVE WS push proves the master is online now. The catch-up/initial
     * batch load (live = false, the default) must NOT bump — otherwise the master's last historical
     * devicestatus pulled at app start (a few minutes old, still inside the 9-min window) would mark the
     * master reachable before any live ping and falsely unlock client editing.
     */
    @Test
    fun doesNotBumpOnBatchLoad() {
        whenever(dateUtil.fromISODateString("new")).thenReturn(5_000L)
        sut.handleNewData(arrayOf(NSDeviceStatus(createdAt = "new")))   // live defaults to false
        verify(nsClientV3Plugin, never()).bumpDevicestatusHeartbeat(any())
    }

    /** A master device must not stamp a client heartbeat — even for a live push. */
    @Test
    fun doesNotBumpOnMaster() {
        whenever(config.AAPSCLIENT).thenReturn(false)
        sut.handleNewData(arrayOf(NSDeviceStatus(createdAt = "new")), live = true)
        verify(nsClientV3Plugin, never()).bumpDevicestatusHeartbeat(any())
    }

    /** created_at absent/unparseable but the numeric `date` (ms) is set → fall back to it. */
    @Test
    fun bumpsWithDateWhenCreatedAtAbsent() {
        sut.handleNewData(arrayOf(NSDeviceStatus(createdAt = null, date = 7_000L)), live = true)
        verify(nsClientV3Plugin).bumpDevicestatusHeartbeat(7_000L)
    }

    /** Neither a parseable created_at nor a date → can't determine freshness → don't bump (fail-closed). */
    @Test
    fun doesNotBumpWhenNoTimestamp() {
        sut.handleNewData(arrayOf(NSDeviceStatus(createdAt = null, date = null)), live = true)
        verify(nsClientV3Plugin, never()).bumpDevicestatusHeartbeat(any())
    }

    /** `toDb` throws for a result without an algorithm, and an error in the app scope ends the app. */
    @Test
    fun doesNotStoreApsResultWithoutAlgorithm() {
        givenResults(APSResult.Algorithm.UNKNOWN)

        sut.handleNewData(arrayOf(deviceStatusAt(TIME_1)))

        verifyBlocking(persistenceLayer, never()) { insertOrUpdateApsResult(any()) }
    }

    /** A new result is stored, and the calculation hears about it, to decide whether it came late. */
    @Test
    fun storesApsResultWithAlgorithm() {
        val results = givenResults(APSResult.Algorithm.SMB)

        sut.handleNewData(arrayOf(deviceStatusAt(TIME_1)))

        verifyBlocking(persistenceLayer) { insertOrUpdateApsResult(results.getValue(5_000L)) }
        verifyBlocking(iobCobCalculator) { apsResultsStored(5_000L) }
        assertThat(openAPSData.clockSuggested).isEqualTo(5_000L)
    }

    /**
     * The ISF for carbs is worked out from every result of the last day. A result older than the newest one
     * seen - the 48 hours loaded after a start, or what came while the phone was offline - is stored too.
     * It does not replace the newest one the overview shows.
     */
    @Test
    fun storesAnOlderResultToo() {
        val results = givenResults(APSResult.Algorithm.SMB)
        openAPSData.clockSuggested = 9_000L

        sut.handleNewData(arrayOf(deviceStatusAt(TIME_1)))

        verifyBlocking(persistenceLayer) { insertOrUpdateApsResult(results.getValue(5_000L)) }
        assertThat(openAPSData.clockSuggested).isEqualTo(9_000L)
        assertThat(openAPSData.suggested).isNull()
    }

    /** Every load after a start brings the last 48 hours again. What is stored already stays as it is. */
    @Test
    fun skipsAResultThatIsStoredAlready() {
        givenResults(APSResult.Algorithm.SMB)
        runBlocking {
            whenever(persistenceLayer.getLoopSensitivities(any(), any())).thenReturn(listOf(PersistenceLayer.LoopSensitivity(5_000L, 60.0)))
        }

        sut.handleNewData(arrayOf(deviceStatusAt(TIME_1)))

        verifyBlocking(persistenceLayer, never()) { insertOrUpdateApsResult(any()) }
        verifyBlocking(iobCobCalculator, never()) { apsResultsStored(any()) }
    }

    /** Only the new ones of a batch are stored, and the calculation hears the oldest of them. */
    @Test
    fun storesOnlyTheNewResultsOfABatch() {
        val results = givenResults(APSResult.Algorithm.SMB)
        runBlocking {
            whenever(persistenceLayer.getLoopSensitivities(any(), any())).thenReturn(listOf(PersistenceLayer.LoopSensitivity(5_000L, 60.0)))
        }

        // Nightscout sends the oldest first
        sut.handleNewData(arrayOf(deviceStatusAt(TIME_1), deviceStatusAt(TIME_2), deviceStatusAt(TIME_3)))

        verifyBlocking(persistenceLayer, never()) { insertOrUpdateApsResult(results.getValue(5_000L)) }
        verifyBlocking(persistenceLayer) { insertOrUpdateApsResult(results.getValue(6_000L)) }
        verifyBlocking(persistenceLayer) { insertOrUpdateApsResult(results.getValue(7_000L)) }
        verifyBlocking(iobCobCalculator) { apsResultsStored(6_000L) }
        assertThat(openAPSData.clockSuggested).isEqualTo(7_000L)
    }

    /**
     * The master stores a result with the time its algorithm ran, `deliverAt` in the device status. The
     * device status time is when the loop finished, a few seconds later. The client takes the master's
     * time, so a result falls into the same 5 minute slot and 24 hour window on both phones.
     */
    @Test
    fun storesTheResultWithTheTimeTheMasterStoredItWith() {
        val results = givenResults(APSResult.Algorithm.SMB)

        sut.handleNewData(arrayOf(deviceStatusAt(TIME_1, deliverAt = "2026-10-12T16:34:55.123Z")))

        verify(results.getValue(5_000L)).date = Instant.parse("2026-10-12T16:34:55.123Z").toEpochMilliseconds()
    }

    /**
     * A client before this version stored a result with the device status time. After the update it loads
     * the 48 hours again, and those results must not be stored a second time with the master's time.
     */
    @Test
    fun skipsAResultAnOlderClientStoredWithTheDeviceStatusTime() {
        givenResults(APSResult.Algorithm.SMB)
        runBlocking {
            whenever(persistenceLayer.getLoopSensitivities(any(), any())).thenReturn(listOf(PersistenceLayer.LoopSensitivity(5_000L, 60.0)))
        }

        sut.handleNewData(arrayOf(deviceStatusAt(TIME_1, deliverAt = "2026-10-12T16:34:55.123Z")))

        verifyBlocking(persistenceLayer, never()) { insertOrUpdateApsResult(any()) }
    }

    /**
     * A run that stopped early on bad CGM data calculated nothing, and the master did not store it (#5413).
     * The client does not store it either, but still shows it as the newest result.
     */
    @Test
    fun doesNotStoreAResultThatStoppedEarly() {
        givenResults(APSResult.Algorithm.SMB, stoppedEarly = true)

        sut.handleNewData(arrayOf(deviceStatusAt(TIME_1)))

        verifyBlocking(persistenceLayer, never()) { insertOrUpdateApsResult(any()) }
        verifyBlocking(iobCobCalculator, never()) { apsResultsStored(any()) }
        assertThat(openAPSData.clockSuggested).isEqualTo(5_000L)
        assertThat(openAPSData.suggested).isNotNull()
    }

    /** The same device status twice in one batch, which a WS push and a load can make, is stored once. */
    @Test
    fun storesTheSameResultOnceInABatch() {
        givenResults(APSResult.Algorithm.SMB)

        sut.handleNewData(arrayOf(deviceStatusAt(TIME_1), deviceStatusAt(TIME_1)))

        verifyBlocking(persistenceLayer, times(1)) { insertOrUpdateApsResult(any()) }
    }

    /**
     * #5414: one document with a time that cannot be read used to throw out of the whole batch. A load then
     * failed on the same page every time, for up to 48 hours. The others are handled now, their results
     * stored, and the batch is finished.
     */
    @Test
    fun aTimeThatCannotBeReadSkipsOnlyItsPart() {
        val results = givenResults(APSResult.Algorithm.SMB)
        whenever(dateUtil.fromISODateString("bad")).thenThrow(IllegalStateException("not an ISO date: bad"))

        sut.handleNewData(arrayOf(deviceStatusAt(TIME_1), deviceStatusAt("bad"), deviceStatusAt(TIME_3)))

        verifyBlocking(persistenceLayer) { insertOrUpdateApsResult(results.getValue(5_000L)) }
        verifyBlocking(persistenceLayer) { insertOrUpdateApsResult(results.getValue(7_000L)) }
        verifyBlocking(iobCobCalculator) { apsResultsStored(5_000L) }
        verify(rxBus).send(any<EventNsClientStatusUpdated>())
        assertThat(oneWarning()).contains("could not be read: 1. The first: suggested.timestamp \"bad\"")
    }

    /** Anything else that throws while a document is handled skips that document, not the batch. */
    @Test
    fun aDocumentThatThrowsDoesNotStopTheBatch() {
        val results = givenResults(APSResult.Algorithm.SMB)
        whenever(dateUtil.fromISODateString(TIME_UNKNOWN)).thenReturn(8_000L) // no result for it: making one throws

        sut.handleNewData(arrayOf(deviceStatusAt(TIME_1), deviceStatusAt(TIME_UNKNOWN), deviceStatusAt(TIME_3)))

        verifyBlocking(persistenceLayer) { insertOrUpdateApsResult(results.getValue(5_000L)) }
        verifyBlocking(persistenceLayer) { insertOrUpdateApsResult(results.getValue(7_000L)) }
        assertThat(oneWarning()).contains("openaps://test")
    }

    /** On the websocket thread a throw ends the app. A live document that cannot be read is skipped. */
    @Test
    fun aLiveDocumentThatCannotBeReadDoesNotThrow() {
        whenever(dateUtil.fromISODateString("bad")).thenThrow(IllegalStateException("not an ISO date: bad"))

        sut.handleNewData(arrayOf(deviceStatusAt("bad", date = 9_000L)), live = true)

        verify(nsClientV3Plugin).bumpDevicestatusHeartbeat(9_000L)
    }

    /**
     * The time of a device status is its `date`, a number. `created_at` is read only without it, so one
     * Nightscout sends as a number ("1698400000000" after the lenient decode) costs nothing.
     */
    @Test
    fun theTimeOfADeviceStatusIsItsDate() {
        val uploaders = HashMap<String, ProcessedDeviceStatusData.Uploader>()
        whenever(processedDeviceStatusData.uploaderMap).thenReturn(uploaders)

        sut.handleNewData(arrayOf(NSDeviceStatus(device = "openaps://test", createdAt = "1698400000000", date = 9_000L, uploaderBattery = 50)))

        assertThat(uploaders.getValue("openaps://test").clock).isEqualTo(9_000L)
        assertThat(uploaders.getValue("openaps://test").battery).isEqualTo(50)
        verify(dateUtil, never()).fromISODateString("1698400000000")
    }

    /**
     * A loop result this client cannot decode, from a master of another version or another uploader, used to
     * write a full stack trace for every document of the 48 hours. Now one line for the batch. The time still
     * moves: the master's loop runs, and the client must not show it as old.
     */
    @Test
    fun loopResultsThatCannotBeReadGiveOneLine() {
        givenResults(APSResult.Algorithm.SMB)

        sut.handleNewData(arrayOf(deviceStatusAt(TIME_1, readable = false), deviceStatusAt(TIME_2, readable = false), deviceStatusAt(TIME_3, readable = false)))

        assertThat(oneWarning()).contains("could not be read: 3. The first: loop result from openaps://test")
        verify(aapsLogger, never()).error(any<LTag>(), any<String>())
        verifyBlocking(persistenceLayer, never()) { insertOrUpdateApsResult(any()) }
        assertThat(openAPSData.clockSuggested).isEqualTo(7_000L)
    }

    /** The one warning of the batch. */
    private fun oneWarning(): String {
        val message = argumentCaptor<String>()
        verify(aapsLogger, times(1)).warn(eq(LTag.NSCLIENT), message.capture())
        return message.firstValue
    }

    /** One result for each suggested time, with that time, [algorithm] and [stoppedEarly]. */
    private fun givenResults(algorithm: APSResult.Algorithm, stoppedEarly: Boolean = false): Map<Long, APSResult> {
        val results = mapOf(TIME_1 to 5_000L, TIME_2 to 6_000L, TIME_3 to 7_000L).map { (iso, time) ->
            whenever(dateUtil.fromISODateString(iso)).thenReturn(time)
            time to mock<APSResult>().also {
                whenever(it.algorithm).thenReturn(algorithm)
                whenever(it.stoppedEarly).thenReturn(stoppedEarly)
                // A real date: the handler sets it to the master's time and reads it back
                var date = time
                whenever(it.date).thenAnswer { date }
                doAnswer { invocation -> date = invocation.getArgument(0); null }.whenever(it).date = any()
            }
        }.toMap()
        whenever(apsResultFactory.with(any())).thenAnswer { results.getValue((it.arguments[0] as RT).timestamp!!) }
        return results
    }

    /** @param readable false leaves out a field the loop result must have, so it cannot be decoded */
    private fun deviceStatusAt(time: String, deliverAt: String? = null, readable: Boolean = true, date: Long? = null) = NSDeviceStatus(
        device = "openaps://test",
        date = date,
        openaps = NSDeviceStatus.OpenAps(suggested = buildJsonObject {
            put("timestamp", time)
            deliverAt?.let { put("deliverAt", it) }
            if (readable) put("runningDynamicIsf", false)
        })
    )

    private companion object {

        const val TIME_1 = "2026-10-12T16:35:00Z"
        const val TIME_2 = "2026-10-12T16:40:00Z"
        const val TIME_3 = "2026-10-12T16:45:00Z"
        const val TIME_UNKNOWN = "2026-10-12T16:50:00Z"
    }
}

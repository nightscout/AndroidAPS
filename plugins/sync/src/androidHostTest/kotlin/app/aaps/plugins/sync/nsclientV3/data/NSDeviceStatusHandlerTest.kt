package app.aaps.plugins.sync.nsclientV3.data

import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventMasterCarbsIsfArrived
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.localmodel.devicestatus.NSDeviceStatus
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

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

    private lateinit var sut: NSDeviceStatusHandler

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(config.AAPSCLIENT).thenReturn(true)
        sut = NSDeviceStatusHandler(
            preferences, config, dateUtil, processedDeviceStatusData, aapsLogger,
            persistenceLayer, overviewData, calculationWorkflow, rxBus,
            CoroutineScope(Dispatchers.Unconfined), { nsClientV3Plugin }
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
        givenNewSuggested(APSResult.Algorithm.UNKNOWN)

        sut.handleNewData(arrayOf(deviceStatusWithSuggested()))

        verifyBlocking(persistenceLayer, never()) { insertOrUpdateApsResult(any()) }
    }

    @Test
    fun storesApsResultWithAlgorithm() {
        val apsResult = givenNewSuggested(APSResult.Algorithm.SMB)

        sut.handleNewData(arrayOf(deviceStatusWithSuggested()))

        verifyBlocking(persistenceLayer) { insertOrUpdateApsResult(apsResult) }
    }

    /**
     * The client's COB calculation reads the master's ISF for carbs from the device status. The first one to
     * carry it, normally the first after a start, is announced, so the calculation can start over with it.
     */
    @Test
    fun announcesTheMastersCarbsIsfWhenItFirstArrives() {
        val apsResult = givenNewSuggested(APSResult.Algorithm.SMB)
        whenever(apsResult.isfMgdlForCarbs).thenReturn(58.0)
        whenever(processedDeviceStatusData.getAPSResult()).thenReturn(null, apsResult)

        sut.handleNewData(arrayOf(deviceStatusWithSuggested()))

        verify(rxBus).send(any<EventMasterCarbsIsfArrived>())
    }

    /** Every later device status carries the value too; the calculation must not start over each time. */
    @Test
    fun doesNotAnnounceTheCarbsIsfWhileItIsAlreadyKnown() {
        val apsResult = givenNewSuggested(APSResult.Algorithm.SMB)
        whenever(apsResult.isfMgdlForCarbs).thenReturn(58.0)

        sut.handleNewData(arrayOf(deviceStatusWithSuggested()))

        verify(rxBus, never()).send(any<EventMasterCarbsIsfArrived>())
    }

    private fun givenNewSuggested(algorithm: APSResult.Algorithm): APSResult {
        val apsResult = mock<APSResult>()
        whenever(apsResult.algorithm).thenReturn(algorithm)
        whenever(processedDeviceStatusData.openAPSData).thenReturn(ProcessedDeviceStatusData.OpenAPSData())
        whenever(processedDeviceStatusData.getAPSResult()).thenReturn(apsResult)
        whenever(dateUtil.fromISODateString(SUGGESTED_TIME)).thenReturn(5_000L)
        return apsResult
    }

    private fun deviceStatusWithSuggested() = NSDeviceStatus(
        device = "openaps://test",
        openaps = NSDeviceStatus.OpenAps(suggested = buildJsonObject {
            put("timestamp", SUGGESTED_TIME)
            put("runningDynamicIsf", false)
        })
    )

    private companion object {

        const val SUGGESTED_TIME = "2026-10-12T16:35:00Z"
    }
}

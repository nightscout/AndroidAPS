package app.aaps.workflow

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.GV
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.Sensitivity
import app.aaps.core.interfaces.calibration.Calibration
import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.ActivityGraphData
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.overview.graph.TimeRange
import app.aaps.core.interfaces.profiling.Profiler
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventBucketedDataCreated
import app.aaps.core.interfaces.smoothing.Smoothing
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.interfaces.workflow.CalculationWorkflow.ProgressData
import app.aaps.core.objects.workflow.WorkOutcome
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertIs

class PrepareGraphDataRunnerTest : TestBaseWithProfile() {

    @Mock lateinit var workflowChainData: WorkflowChainData
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var profiler: Profiler
    @Mock lateinit var mockedRxBus: RxBus
    @Mock lateinit var sensitivity: Sensitivity
    @Mock lateinit var dataAds: AutosensDataStore
    @Mock lateinit var dataIobCob: IobCobCalculator
    @Mock lateinit var cache: OverviewDataCache
    @Mock lateinit var overviewData: OverviewData
    @Mock lateinit var signals: CalculationSignalsEmitter
    @Mock lateinit var activeCalibration: Calibration
    @Mock lateinit var activeSmoothing: Smoothing

    private val autosensDataProvider = { mock<AutosensData>() }

    private fun runner() =
        PrepareGraphDataRunner(
            aapsLogger, workflowChainData, dateUtil, mockedRxBus, persistenceLayer,
            activePlugin, profileFunction, profileUtil, preferences, profiler, decimalFormatter,
            TestScope(), autosensDataProvider
        )

    // The runner asks this between phases; nothing in these tests stops a run part way.
    private suspend fun run() = runner().run(job = "main", generation = 1L, isStopped = { false })

    private fun buildData(bgDataReload: Boolean, emitFinalProgress: Boolean) =
        PrepareGraphData(
            iobCobCalculator = dataIobCob,
            overviewData = overviewData,
            cache = cache,
            signals = signals,
            reason = "test",
            end = 0L,
            bgDataReload = bgDataReload,
            limitDataToOldestAvailable = false,
            triggeredByNewBG = false,
            emitFinalProgress = emitFinalProgress
        )

    @BeforeEach
    fun setup() {
        whenever(dataIobCob.ads).thenReturn(dataAds)
        whenever(dataAds.clone()).thenReturn(dataAds)
        whenever(dataAds.dataLock).thenReturn(AapsLock())
        whenever(dataAds.getBucketedDataTableCopy()).thenReturn(null)
        // Mockito returns empty (not null) for collection getters; force null so smooth/oref short-circuit.
        whenever(dataAds.bucketedData).thenReturn(null)
        whenever(cache.timeRangeFlow).thenReturn(MutableStateFlow(null))
        whenever(activePlugin.activeSensitivity).thenReturn(sensitivity)
        whenever(sensitivity.isOref1).thenReturn(false)
    }

    private suspend fun stubSuspendCalls() {
        // Profile invalid → the oref autosens loop short-circuits; that algorithm has its own tests.
        whenever(profileFunction.isProfileValid(any())).thenReturn(false)
        whenever(profileFunction.getProfile(any())).thenReturn(null)
        whenever(dataAds.getLastAutosensData(any(), any(), any())).thenReturn(null)
        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any())).thenReturn(emptyList())
        whenever(persistenceLayer.getVariableSensitivities(any(), any())).thenReturn(emptyList())
        whenever(persistenceLayer.getTemporaryTargetActiveAt(any())).thenReturn(null)
        whenever(dataIobCob.calculateIobArrayForSMB(any(), any(), any(), any())).thenReturn(emptyArray())
        whenever(dataIobCob.iobArrayToString(any())).thenReturn("")
    }

    private fun carbsAt(vararg timestamps: Long) = timestamps.map { CA(timestamp = it, amount = 10.0, duration = 0) }

    @Test
    fun `a bucket takes the carbs of its own five minutes`() {
        val bgTime = T.mins(60).msecs()
        val all = carbsAt(
            bgTime - T.mins(5).msecs(),          // the previous bucket's boundary - not this one
            bgTime - T.mins(5).msecs() + 1,      // first millisecond of this bucket
            bgTime - T.mins(2).msecs(),          // plainly inside
            bgTime,                              // this bucket's own boundary
            bgTime + 1                           // already the next bucket
        )

        val picked = runner().carbsForBucket(all, bgTime).map { it.timestamp }

        assertThat(picked).containsExactly(
            bgTime - T.mins(5).msecs() + 1,
            bgTime - T.mins(2).msecs(),
            bgTime
        ).inOrder()
    }

    @Test
    fun `neighbouring buckets never both count the same carb`() {
        // The #4596 property, and the reason for the +1. Walk a carb across a whole bucket, one second at
        // a time, and no position may be claimed by both buckets - nor, on the shared boundary, by the
        // wrong one.
        val bgTime = T.mins(60).msecs()
        val previous = bgTime - T.mins(5).msecs()
        for (offset in 0..300) {
            val carb = carbsAt(previous + offset * 1000L)
            val inPrevious = runner().carbsForBucket(carb, previous).size
            val inCurrent = runner().carbsForBucket(carb, bgTime).size
            assertThat(inPrevious + inCurrent).isEqualTo(1)
        }
    }

    @Test
    fun `an empty span yields nothing`() {
        assertThat(runner().carbsForBucket(emptyList(), T.mins(60).msecs())).isEmpty()
    }

    @Test
    fun `missing or stale chain data returns failure`() = runTest {
        whenever(workflowChainData.prepareFor(any(), any())).thenReturn(null)

        assertIs<WorkOutcome.Failure>(run())
    }

    @Test
    fun `full run traverses all phases and emits progress`() = runTest {
        stubSuspendCalls()
        whenever(workflowChainData.prepareFor(any(), any())).thenReturn(buildData(bgDataReload = false, emitFinalProgress = true))

        val result = run()

        assertIs<WorkOutcome.Success>(result)
        verify(signals).emitProgress(eq(ProgressData.DRAW_BG), any())
        verify(signals).emitProgress(eq(ProgressData.DRAW_IOB), any())
        verify(signals).emitProgress(eq(ProgressData.DRAW_FINAL), any())
    }

    /**
     * The prepare pass runs before the one that knows the horizon, so it has to leave the horizon
     * alone. When it wrote `endTime = toTime` instead, the future activity stopped at the next full
     * hour while the axis went on to the newest prediction.
     *
     * `updateTimeRange` writes through to the flow on purpose: the pass reads the range back when it
     * builds the IOB and activity series, so a collapsed end has to be able to reach that read, or
     * this test would pass with the bug in place.
     */
    @Test
    fun `the prepare pass keeps the horizon and the future activity reaches it`() = runTest {
        stubSuspendCalls()
        val toTime = now + T.mins(30).msecs()
        val horizon = toTime + T.hours(4).msecs()
        whenever(overviewData.toTime).thenReturn(toTime)
        // As the post phase of the previous run left it.
        val timeRange = MutableStateFlow<TimeRange?>(TimeRange(fromTime = toTime - T.hours(24).msecs(), toTime = toTime, endTime = horizon))
        whenever(cache.timeRangeFlow).thenReturn(timeRange)
        whenever(cache.updateTimeRange(any())).then { timeRange.value = it.arguments[0] as TimeRange? }
        whenever(profileFunction.getProfile(any<Long>())).thenReturn(effectiveProfile)
        whenever(dataIobCob.calculateFromTreatmentsAndTemps(any(), any())).thenAnswer { IobTotal(it.arguments[0] as Long, activity = 0.01) }
        whenever(dataIobCob.calculateAbsoluteIobFromBaseBasals(any())).thenAnswer { IobTotal(it.arguments[0] as Long) }
        whenever(workflowChainData.prepareFor(anyOrNull(), any())).thenReturn(buildData(bgDataReload = false, emitFinalProgress = false))

        run()

        assertThat(timeRange.value?.endTime).isEqualTo(horizon)
        val activity = argumentCaptor<ActivityGraphData>()
        verify(cache).updateActivityGraph(activity.capture())
        assertThat(activity.lastValue.activityPrediction.last().timestamp).isGreaterThan(toTime)
        assertThat(activity.lastValue.activityPrediction.last().timestamp).isAtMost(horizon)
    }

    @Test
    fun `bg data reload phase loads, smooths and signals bucketed data`() = runTest {
        stubSuspendCalls()
        val gv = mock<GV>()
        whenever(gv.timestamp).thenReturn(-60_000L)
        whenever(gv.utcOffset).thenReturn(19_800_000L)
        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any())).thenReturn(listOf(gv))
        whenever(workflowChainData.prepareFor(any(), any())).thenReturn(buildData(bgDataReload = true, emitFinalProgress = false))

        val result = run()

        assertIs<WorkOutcome.Success>(result)
        verify(mockedRxBus).send(any<EventBucketedDataCreated>())
        // The cached IOB is kept for a new BG, only old entries are dropped
        verify(dataIobCob).bgDataReloaded()
        verify(dataIobCob, never()).clearCache()
        val readings = argumentCaptor<List<BgDataPoint>>()
        verify(cache).updateBgReadings(readings.capture())
        assertThat(readings.lastValue.single().utcOffset).isEqualTo(gv.utcOffset)
        // Terminal-only progress not emitted when emitFinalProgress = false
        verify(signals, never()).emitProgress(eq(ProgressData.DRAW_FINAL), any())
    }

    // ----- never correct twice: glucose the sensor already corrected is not calibrated again here -----

    /** Makes the smoothing step run on one real reading, with [source] as the active BG source. */
    private suspend fun stubSmoothingCalls(source: BgSource) {
        val data = mutableListOf(InMemoryGlucoseValue(timestamp = 1000L, value = 100.0))
        whenever(dataAds.bucketedData).thenReturn(data)
        whenever(dataIobCob.calculateIobFromBolus()).thenReturn(IobTotal(0L))
        whenever(dataIobCob.calculateIobFromTempBasalsIncludingConvertedExtended()).thenReturn(IobTotal(0L))
        whenever(activePlugin.activeBgSource).thenReturn(source)
        whenever(activePlugin.activeCalibration).thenReturn(activeCalibration)
        whenever(activeCalibration.calibrate(any(), any())).thenAnswer { it.getArgument(0) }
        whenever(activePlugin.activeSmoothing).thenReturn(activeSmoothing)
        whenever(activeSmoothing.smooth(any())).thenAnswer { it.getArgument(0) }
    }

    private fun source(calibratesInSensor: Boolean): BgSource = mock<BgSource>().also {
        whenever(it.calibratesInSensor()).thenReturn(calibratesInSensor)
    }

    @Test
    fun `no calibration in the app when the source calibrates in the sensor`() = runTest {
        stubSuspendCalls()
        stubSmoothingCalls(source(calibratesInSensor = true))
        whenever(workflowChainData.prepareFor(anyOrNull(), any())).thenReturn(buildData(bgDataReload = true, emitFinalProgress = false))

        assertIs<WorkOutcome.Success>(run())

        verify(activeCalibration, never()).calibrate(any(), any())
        // Smoothing is a local filter, so it still runs.
        verify(activeSmoothing).smooth(any())
    }

    @Test
    fun `calibration runs in the app for a normal source`() = runTest {
        stubSuspendCalls()
        stubSmoothingCalls(source(calibratesInSensor = false))
        whenever(workflowChainData.prepareFor(anyOrNull(), any())).thenReturn(buildData(bgDataReload = true, emitFinalProgress = false))

        assertIs<WorkOutcome.Success>(run())

        verify(activeCalibration).calibrate(any(), any())
    }
}

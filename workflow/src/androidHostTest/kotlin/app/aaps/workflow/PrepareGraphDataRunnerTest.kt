package app.aaps.workflow

import androidx.collection.LongSparseArray
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.time.SystemTimeZone
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.aps.Sensitivity
import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.ActivityGraphData
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.overview.graph.TimeRange
import app.aaps.core.interfaces.overview.liveGraphWindowEnd
import app.aaps.core.interfaces.profiling.Profiler
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventBucketedDataCreated
import app.aaps.core.interfaces.scenes.SceneExpiryCheck
import app.aaps.core.interfaces.widget.WidgetUpdater
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.interfaces.workflow.CalculationWorkflow.Companion.MAIN_CALCULATION
import app.aaps.core.interfaces.workflow.CalculationWorkflow.ProgressData
import app.aaps.core.objects.workflow.WorkOutcome
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

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
    @Mock lateinit var loop: Loop
    @Mock lateinit var widgetUpdater: WidgetUpdater
    @Mock lateinit var processedDeviceStatusData: ProcessedDeviceStatusData
    @Mock lateinit var sceneExpiryCheck: SceneExpiryCheck

    private val autosensDataProvider = { mock<AutosensData>() }

    private fun runner() =
        PrepareGraphDataRunner(
            aapsLogger, workflowChainData, dateUtil, mockedRxBus, persistenceLayer,
            activePlugin, profileFunction, profileUtil, preferences, profiler, decimalFormatter,
            fabricPrivacy, TestScope(), autosensDataProvider
        )

    // The runner asks this between phases; nothing in these tests stops a run part way.
    private suspend fun run() = runner().run(job = "main", generation = 1L, isStopped = { false })

    private fun buildData(bgDataReload: Boolean, emitFinalProgress: Boolean, liveWindow: Boolean = false, window: OverviewData = overviewData) =
        PrepareGraphData(
            iobCobCalculator = dataIobCob,
            overviewData = window,
            cache = cache,
            signals = signals,
            reason = "test",
            end = 0L,
            bgDataReload = bgDataReload,
            limitDataToOldestAvailable = false,
            triggeredByNewBG = false,
            emitFinalProgress = emitFinalProgress,
            liveWindow = liveWindow
        )

    private class Window(override var toTime: Long, override var fromTime: Long, override var endTime: Long) : OverviewData

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

    /**
     * #5614: the first run after the clock passed the window of the run before drew with that old window, so the
     * newest reading was missing from the graph until the next one. The post pass, which moved the window on, runs
     * only after the readings are picked.
     */
    @Test
    fun `a live run moves the window on before it picks the readings`() = runTest {
        stubSuspendCalls()
        val reading = now - T.mins(1).msecs()
        // The window of a run more than an hour ago, as its post pass left it
        val oldToTime = now - T.mins(30).msecs()
        val window = Window(toTime = oldToTime, fromTime = oldToTime - T.hours(24).msecs(), endTime = oldToTime)
        val gv = GV(timestamp = reading, value = 150.0, raw = null, noise = null, trendArrow = TrendArrow.FLAT, sourceSensor = SourceSensor.UNKNOWN)
        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any())).thenReturn(listOf(gv))
        whenever(dataAds.getBucketedDataTableCopy()).thenReturn(mutableListOf(InMemoryGlucoseValue(timestamp = reading, value = 150.0)))
        whenever(workflowChainData.prepareFor(any(), any())).thenReturn(buildData(bgDataReload = false, emitFinalProgress = false, liveWindow = true, window = window))

        run()

        assertThat(window.toTime).isEqualTo(liveGraphWindowEnd(Instant.fromEpochMilliseconds(now), SystemTimeZone.current()))
        val readings = argumentCaptor<List<BgDataPoint>>()
        verify(cache).updateBgReadings(readings.capture())
        assertThat(readings.lastValue.map { it.timestamp }).containsExactly(reading)
        val bucketed = argumentCaptor<List<BgDataPoint>>()
        verify(cache).updateBucketedData(bucketed.capture())
        assertThat(bucketed.lastValue.map { it.timestamp }).containsExactly(reading)
    }

    /** The history browser sets its own window, a day in the past: a run must leave it there. */
    @Test
    fun `a history run keeps its own window`() = runTest {
        stubSuspendCalls()
        val dayBefore = now - T.days(1).msecs()
        val window = Window(toTime = dayBefore, fromTime = dayBefore - T.hours(24).msecs(), endTime = dayBefore)
        whenever(workflowChainData.prepareFor(any(), any())).thenReturn(buildData(bgDataReload = false, emitFinalProgress = true, liveWindow = false, window = window))

        run()

        assertThat(window.toTime).isEqualTo(dayBefore)
        assertThat(window.fromTime).isEqualTo(dayBefore - T.hours(24).msecs())
        assertThat(window.endTime).isEqualTo(dayBefore)
    }

    // ----- A step that only fills the chart must not fail the phase, and so must not skip the loop -----

    private fun onePointInTheWindow() {
        // overviewData is a mock, so the window ends at 0 and one minute before that is inside it.
        whenever(dataAds.getBucketedDataTableCopy()).thenReturn(mutableListOf(InMemoryGlucoseValue(timestamp = -60_000L, value = 150.0)))
    }

    @Test
    fun `a failing bucketed chart step does not fail the phase`() = runTest {
        stubSuspendCalls()
        onePointInTheWindow()
        doThrow(IllegalStateException("broken bucketed point")).whenever(cache).updateBucketedData(any())
        whenever(workflowChainData.prepareFor(any(), any())).thenReturn(buildData(bgDataReload = false, emitFinalProgress = true))

        val result = run()

        assertIs<WorkOutcome.Success>(result)
        // The steps after it still ran.
        verify(cache).updateBgReadings(any())
        verify(cache).updateIobGraph(any())
        verify(signals).emitProgress(eq(ProgressData.DRAW_FINAL), any())
    }

    @Test
    fun `a failing bg readings chart step does not fail the phase`() = runTest {
        stubSuspendCalls()
        // Without a reload only the chart step reads the readings from the database.
        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any())).thenThrow(IllegalStateException("broken reading"))
        whenever(workflowChainData.prepareFor(any(), any())).thenReturn(buildData(bgDataReload = false, emitFinalProgress = true))

        val result = run()

        assertIs<WorkOutcome.Success>(result)
        verify(cache, never()).updateBgReadings(any())
        verify(cache).updateIobGraph(any())
        verify(signals).emitProgress(eq(ProgressData.DRAW_BG), any())
    }

    @Test
    fun `a failing iob chart step does not fail the phase`() = runTest {
        stubSuspendCalls()
        whenever(persistenceLayer.getVariableSensitivities(any(), any())).thenThrow(IllegalStateException("broken chart point"))
        whenever(workflowChainData.prepareFor(any(), any())).thenReturn(buildData(bgDataReload = false, emitFinalProgress = true))

        val result = run()

        assertIs<WorkOutcome.Success>(result)
        verify(cache, never()).updateIobGraph(any())
        verify(signals).emitProgress(eq(ProgressData.DRAW_IOB), any())
        verify(signals).emitProgress(eq(ProgressData.DRAW_FINAL), any())
    }

    /** A cancelled run must end. Only real failures of a chart step are kept away from the phase. */
    @Test
    fun `a cancelled chart step still cancels the phase`() = runTest {
        stubSuspendCalls()
        doThrow(CancellationException("replaced")).whenever(cache).updateBgReadings(any())
        whenever(workflowChainData.prepareFor(any(), any())).thenReturn(buildData(bgDataReload = false, emitFinalProgress = true))

        assertFailsWith<CancellationException> { run() }
    }

    // The safety side: the loop doses from these two steps, so their failure must stay a failure.

    @Test
    fun `a failing bg load still fails the phase`() = runTest {
        stubSuspendCalls()
        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any())).thenThrow(IllegalStateException("broken database"))
        whenever(workflowChainData.prepareFor(any(), any())).thenReturn(buildData(bgDataReload = true, emitFinalProgress = true))

        assertFailsWith<IllegalStateException> { run() }

        verify(signals, never()).emitProgress(eq(ProgressData.DRAW_FINAL), any())
    }

    // Both IOB/COB passes, so wrapping only one of them by mistake fails a test.
    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `a failing iob cob pass still fails the phase`(isOref1: Boolean) = runTest {
        stubSuspendCalls()
        whenever(sensitivity.isOref1).thenReturn(isOref1)
        whenever(profileFunction.isProfileValid(any())).thenThrow(IllegalStateException("broken calculation"))
        whenever(workflowChainData.prepareFor(any(), any())).thenReturn(buildData(bgDataReload = false, emitFinalProgress = true))

        assertFailsWith<IllegalStateException> { run() }

        verify(cache, never()).updateIobGraph(any())
        verify(signals, never()).emitProgress(eq(ProgressData.DRAW_FINAL), any())
    }

    /** Lets the IOB/COB pass get into its bucket loop, where reading the profile of a bucket then throws. */
    private suspend fun stubIobCobPassFailingInItsBucketLoop() {
        whenever(profileFunction.isProfileValid(any())).thenReturn(true)
        whenever(dataIobCob.calculateDetectionStart(any(), any())).thenReturn(0L)
        whenever(dataAds.bucketedData).thenReturn(MutableList(5) { InMemoryGlucoseValue(timestamp = -it * T.mins(5).msecs(), value = 150.0) })
        whenever(dataAds.autosensDataTable).thenReturn(LongSparseArray())
        whenever(persistenceLayer.getTherapyEventDataFromTime(any(), any<TE.Type>(), any())).thenReturn(emptyList())
        whenever(persistenceLayer.getProfileSwitchesFromTime(any(), any())).thenReturn(emptyList())
        whenever(persistenceLayer.getCarbsFromTimeToTimeExpanded(any(), any(), any())).thenReturn(emptyList())
        whenever(profileFunction.getProfile(any<Long>())).thenThrow(IllegalStateException("broken bucket"))
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `an iob cob pass that fails inside its bucket loop still fails the phase`(isOref1: Boolean) = runTest {
        stubSuspendCalls()
        whenever(sensitivity.isOref1).thenReturn(isOref1)
        stubIobCobPassFailingInItsBucketLoop()
        whenever(workflowChainData.prepareFor(any(), any())).thenReturn(buildData(bgDataReload = false, emitFinalProgress = true))

        val failure = assertFailsWith<IllegalStateException> { run() }

        assertThat(failure.message).isEqualTo("broken bucket")
        // The loop was reached: the pass reports its progress at the top of each bucket.
        verify(signals).emitProgress(eq(ProgressData.IOB_COB_OREF), eq(80))
        verify(cache, never()).updateIobGraph(any())
        verify(signals, never()).emitProgress(eq(ProgressData.DRAW_FINAL), any())
    }

    /** Only an `Exception` of a chart step is kept away. An `Error` is not a bad data point, so it fails the phase. */
    @Test
    fun `an error in a chart step still fails the phase`() = runTest {
        stubSuspendCalls()
        doThrow(NotImplementedError("broken build")).whenever(cache).updateBgReadings(any())
        whenever(workflowChainData.prepareFor(any(), any())).thenReturn(buildData(bgDataReload = false, emitFinalProgress = true))

        assertFailsWith<NotImplementedError> { run() }

        verify(signals, never()).emitProgress(eq(ProgressData.DRAW_FINAL), any())
    }

    // ----- A chart step that keeps failing is reported, once per step -----

    @Test
    fun `a chart step that fails on two runs is reported once`() = runTest {
        stubSuspendCalls()
        whenever(persistenceLayer.getVariableSensitivities(any(), any())).thenThrow(IllegalStateException("broken chart point"))
        whenever(workflowChainData.prepareFor(any(), any())).thenReturn(buildData(bgDataReload = false, emitFinalProgress = true))
        val runner = runner()

        repeat(2) { assertIs<WorkOutcome.Success>(runner.run(job = "main", generation = 1L, isStopped = { false })) }

        val reported = argumentCaptor<Throwable>()
        verify(fabricPrivacy, times(1)).logException(reported.capture())
        assertThat(reported.lastValue.message).contains("IOB and autosens graph data")
        assertThat(reported.lastValue.cause).hasMessageThat().isEqualTo("broken chart point")
    }

    @Test
    fun `each failing chart step gets its own report`() = runTest {
        stubSuspendCalls()
        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any())).thenThrow(IllegalStateException("broken reading"))
        whenever(persistenceLayer.getVariableSensitivities(any(), any())).thenThrow(IllegalStateException("broken chart point"))
        whenever(workflowChainData.prepareFor(any(), any())).thenReturn(buildData(bgDataReload = false, emitFinalProgress = true))

        run()

        val reported = argumentCaptor<Throwable>()
        verify(fabricPrivacy, times(2)).logException(reported.capture())
        assertThat(reported.firstValue.message).contains("BG readings")
        assertThat(reported.secondValue.message).contains("IOB and autosens graph data")
    }

    @Test
    fun `a run without a failure reports nothing`() = runTest {
        stubSuspendCalls()
        whenever(workflowChainData.prepareFor(any(), any())).thenReturn(buildData(bgDataReload = false, emitFinalProgress = true))

        run()

        verify(fabricPrivacy, never()).logException(any())
    }

    // The same two cases through the real executor and the real post phase, up to the loop call.

    private fun postRunner() =
        PostCalculationRunner(
            aapsLogger, workflowChainData, iobCobCalculator, loop,
            widgetUpdater, config, processedDeviceStatusData, profileUtil, preferences, sceneExpiryCheck
        )

    /** A new BG the loop has not used yet, so the post phase calls the loop when it is reached. */
    private fun stubPostPhaseWithNewBg() {
        val ads = mock<AutosensDataStore>()
        val bg = mock<InMemoryGlucoseValue>()
        whenever(bg.timestamp).thenReturn(5000L)
        whenever(iobCobCalculator.ads).thenReturn(ads)
        whenever(ads.actualBg()).thenReturn(bg)
        whenever(loop.lastBgTriggeredRun).thenReturn(0L)
        whenever(config.APS).thenReturn(false)
        whenever(workflowChainData.postFor(any(), any())).thenReturn(
            PostCalculationData(overviewData = overviewData, cache = cache, signals = signals, runLoopAndWidgetPhase = true)
        )
    }

    private suspend fun TestScope.runMainChain() {
        val executor = CoroutineCalculationExecutor(this, aapsLogger, runner(), postRunner(), wakeLock = null)
        executor.start(MAIN_CALCULATION, generation = 1L, runPost = true)
        assertThat(executor.awaitIdle(MAIN_CALCULATION, 5.seconds)).isTrue()
    }

    @Test
    fun `the loop is still invoked when a chart step fails`() = runTest {
        stubSuspendCalls()
        stubPostPhaseWithNewBg()
        whenever(persistenceLayer.getVariableSensitivities(any(), any())).thenThrow(IllegalStateException("broken chart point"))
        whenever(workflowChainData.prepareFor(any(), any())).thenReturn(buildData(bgDataReload = false, emitFinalProgress = false))

        runMainChain()

        verify(loop).invoke(any(), any(), any())
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `the loop is not invoked when the iob cob pass fails`(isOref1: Boolean) = runTest {
        stubSuspendCalls()
        stubPostPhaseWithNewBg()
        whenever(sensitivity.isOref1).thenReturn(isOref1)
        whenever(profileFunction.isProfileValid(any())).thenThrow(IllegalStateException("broken calculation"))
        whenever(workflowChainData.prepareFor(any(), any())).thenReturn(buildData(bgDataReload = false, emitFinalProgress = false))

        runMainChain()

        verify(loop, never()).invoke(any(), any(), any())
    }

    @Test
    fun `the loop is not invoked when the bg load fails`() = runTest {
        stubSuspendCalls()
        stubPostPhaseWithNewBg()
        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any())).thenThrow(IllegalStateException("broken database"))
        whenever(workflowChainData.prepareFor(any(), any())).thenReturn(buildData(bgDataReload = true, emitFinalProgress = false))

        runMainChain()

        verify(loop, never()).invoke(any(), any(), any())
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
}

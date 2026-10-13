package app.aaps.workflow

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.widget.WidgetUpdater
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.objects.workflow.WorkOutcome
import app.aaps.shared.tests.TestBaseWithProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class PostCalculationRunnerTest : TestBaseWithProfile() {

    @Mock lateinit var workflowChainData: WorkflowChainData
    @Mock lateinit var loop: Loop
    @Mock lateinit var widgetUpdater: WidgetUpdater
    @Mock lateinit var processedDeviceStatusData: ProcessedDeviceStatusData

    private fun runner() =
        PostCalculationRunner(
            aapsLogger, workflowChainData, iobCobCalculator, loop,
            widgetUpdater, config, processedDeviceStatusData, profileUtil, preferences
        )

    // Nothing in these tests stops a run part way.
    private suspend fun run() = runner().run(job = "main", generation = 1L, isStopped = { false })

    private fun dataWith(runLoopAndWidgetPhase: Boolean): PostCalculationData {
        val cache = mock<OverviewDataCache>()
        whenever(cache.timeRangeFlow).thenReturn(MutableStateFlow(null))
        return PostCalculationData(
            overviewData = mock<OverviewData>(),
            cache = cache,
            signals = mock<CalculationSignalsEmitter>(),
            runLoopAndWidgetPhase = runLoopAndWidgetPhase
        )
    }

    @BeforeEach
    fun setup() {
        whenever(config.APS).thenReturn(false)
    }

    @Test
    fun `missing or stale data returns failure`() = runTest {
        whenever(workflowChainData.postFor(any(), any())).thenReturn(null)

        assertIs<WorkOutcome.Failure>(run())
    }

    @Test
    fun `predictions only phase returns success without loop or widget`() = runTest {
        val data = dataWith(runLoopAndWidgetPhase = false)
        whenever(workflowChainData.postFor(any(), any())).thenReturn(data)

        val result = run()

        Assertions.assertEquals(WorkOutcome.Success, result)
        verify(loop, never()).invoke(any(), any(), any())
        verify(widgetUpdater, never()).update(any())
    }

    @Test
    fun `full phase invokes loop on new bg and updates widget`() = runTest {
        val ads = mock<AutosensDataStore>()
        val bg = mock<InMemoryGlucoseValue>()
        whenever(bg.timestamp).thenReturn(5000L)
        whenever(iobCobCalculator.ads).thenReturn(ads)
        whenever(ads.actualBg()).thenReturn(bg)
        whenever(loop.lastBgTriggeredRun).thenReturn(0L)
        val data = dataWith(runLoopAndWidgetPhase = true)
        whenever(workflowChainData.postFor(any(), any())).thenReturn(data)

        val result = run()

        Assertions.assertEquals(WorkOutcome.Success, result)
        verify(loop).invoke(any(), any(), any())
        verify(widgetUpdater).update("WorkFlow")
    }

    // The chain that reaches this runner does not have to be the chain the new BG started - a BG chain
    // can be cancelled and replaced by one started by another database change (issue #5066). The only
    // gate left is lastBgTriggeredRun, so the two cases below are what keeps the loop from running twice.
    @Test
    fun `full phase invokes loop even when the chain was not started by a new bg`() = runTest {
        val ads = mock<AutosensDataStore>()
        val bg = mock<InMemoryGlucoseValue>()
        whenever(bg.timestamp).thenReturn(5000L)
        whenever(iobCobCalculator.ads).thenReturn(ads)
        whenever(ads.actualBg()).thenReturn(bg)
        // A loop run happened for an older BG, the newest one was never used
        whenever(loop.lastBgTriggeredRun).thenReturn(4000L)
        val data = dataWith(runLoopAndWidgetPhase = true)
        whenever(workflowChainData.postFor(any(), any())).thenReturn(data)

        val result = run()

        Assertions.assertEquals(WorkOutcome.Success, result)
        verify(loop).invoke(any(), any(), any())
    }

    @Test
    fun `full phase skips loop when newest bg was already used`() = runTest {
        val ads = mock<AutosensDataStore>()
        val bg = mock<InMemoryGlucoseValue>()
        whenever(bg.timestamp).thenReturn(5000L)
        whenever(iobCobCalculator.ads).thenReturn(ads)
        whenever(ads.actualBg()).thenReturn(bg)
        whenever(loop.lastBgTriggeredRun).thenReturn(5000L)
        val data = dataWith(runLoopAndWidgetPhase = true)
        whenever(workflowChainData.postFor(any(), any())).thenReturn(data)

        val result = run()

        Assertions.assertEquals(WorkOutcome.Success, result)
        verify(loop, never()).invoke(any(), any(), any())
        verify(widgetUpdater).update("WorkFlow")
    }

    /**
     * The chain was replaced while the loop ran, and the loop had not stored a decision yet. It did
     * nothing for this BG, so the BG must be given back, or the replacing chain skips it and this BG
     * gets no loop run at all.
     */
    @Test
    fun `a loop run cancelled before its decision gives the bg back`() = runTest {
        val ads = mock<AutosensDataStore>()
        val bg = mock<InMemoryGlucoseValue>()
        whenever(bg.timestamp).thenReturn(5000L)
        whenever(iobCobCalculator.ads).thenReturn(ads)
        whenever(ads.actualBg()).thenReturn(bg)
        whenever(loop.lastBgTriggeredRun).thenReturn(4000L)
        whenever(loop.lastRun).thenReturn(Loop.LastRun().apply { lastAPSRun = 1000L })
        whenever(loop.invoke(any(), any(), any())).doSuspendableAnswer { throw CancellationException("replaced") }
        val data = dataWith(runLoopAndWidgetPhase = true)
        whenever(workflowChainData.postFor(any(), any())).thenReturn(data)

        assertFailsWith<CancellationException> { run() }

        verify(loop).lastBgTriggeredRun = 5000L
        verify(loop).lastBgTriggeredRun = 4000L
    }

    /**
     * Once the loop stored a decision, the enactment after it cannot be cancelled and may have reached
     * the pump. The BG stays used, so no second automatic run follows for it.
     */
    @Test
    fun `a loop run cancelled after its decision keeps the bg used`() = runTest {
        val ads = mock<AutosensDataStore>()
        val bg = mock<InMemoryGlucoseValue>()
        whenever(bg.timestamp).thenReturn(5000L)
        whenever(iobCobCalculator.ads).thenReturn(ads)
        whenever(ads.actualBg()).thenReturn(bg)
        whenever(loop.lastBgTriggeredRun).thenReturn(4000L)
        val lastRun = Loop.LastRun().apply { lastAPSRun = 1000L }
        whenever(loop.lastRun).thenReturn(lastRun)
        whenever(loop.invoke(any(), any(), any())).doSuspendableAnswer {
            lastRun.lastAPSRun = 6000L // the decision is stored
            throw CancellationException("replaced")
        }
        val data = dataWith(runLoopAndWidgetPhase = true)
        whenever(workflowChainData.postFor(any(), any())).thenReturn(data)

        assertFailsWith<CancellationException> { run() }

        verify(loop).lastBgTriggeredRun = 5000L
        verify(loop, never()).lastBgTriggeredRun = 4000L
    }

    @Test
    fun `full phase skips loop when no actual bg is available`() = runTest {
        val ads = mock<AutosensDataStore>()
        whenever(iobCobCalculator.ads).thenReturn(ads)
        whenever(ads.actualBg()).thenReturn(null)
        val data = dataWith(runLoopAndWidgetPhase = true)
        whenever(workflowChainData.postFor(any(), any())).thenReturn(data)

        val result = run()

        Assertions.assertEquals(WorkOutcome.Success, result)
        verify(loop, never()).invoke(any(), any(), any())
        verify(widgetUpdater).update("WorkFlow")
    }
}

package app.aaps.ui.compose.history

import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
internal class HistoryViewModelTest {

    @Mock private lateinit var historyScope: HistoryScope
    @Mock private lateinit var calculationWorkflow: CalculationWorkflow
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var graphViewModelFactory: GraphViewModel.Factory
    @Mock private lateinit var signals: CalculationSignalsEmitter
    @Mock private lateinit var overviewData: OverviewData
    @Mock private lateinit var cache: OverviewDataCache

    private val dispatcher = StandardTestDispatcher()
    private lateinit var sut: HistoryViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(dispatcher)
        // The init block runs synchronously at construction: the `progress` field reads
        // historyScope.signals.progress, and buildState()/runCalculation() read overviewData —
        // both must be non-null or construction NPEs.
        whenever(historyScope.signals).thenReturn(signals)
        whenever(signals.progress).thenReturn(MutableStateFlow(0))
        whenever(historyScope.overviewData).thenReturn(overviewData)
        // runCalculation() clears the cache first, so the old window is not drawn under the new date.
        whenever(historyScope.cache).thenReturn(cache)
        sut = HistoryViewModel(historyScope, calculationWorkflow, dateUtil, graphViewModelFactory)
    }

    // Some tests below pin a zone with daylight saving time. MidnightTime reads the JVM default.
    private val originalZone: TimeZone = TimeZone.getDefault()

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        TimeZone.setDefault(originalZone)
    }

    /** A real window rather than the mock above: these tests read back what the view model wrote. */
    private class FakeOverviewData : OverviewData {

        override var toTime = 0L
        override var fromTime = 0L
        override var endTime = 0L
    }

    private fun viewModelAt(now: Long, window: OverviewData): HistoryViewModel {
        whenever(historyScope.overviewData).thenReturn(window)
        whenever(dateUtil.now()).thenReturn(now)
        whenever(dateUtil.dateString(any())).thenReturn("date")
        return HistoryViewModel(historyScope, calculationWorkflow, dateUtil, graphViewModelFactory)
    }

    /** Where a window for [date] must start: its local midnight plus the nudge. */
    private fun windowStart(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.of(PRAGUE)).toInstant().toEpochMilli() + HistoryViewModel.WINDOW_NUDGE_MS

    private fun noonIn(date: LocalDate): Long = date.atTime(12, 0).atZone(ZoneId.of(PRAGUE)).toInstant().toEpochMilli()

    /**
     * The window used to move 100 s later on every tap, because each step started from `fromTime`,
     * which already carries the nudge, and then added it again. After ten taps back it started at
     * 00:16:40.
     */
    @Test
    fun `stepping through days keeps every window just after midnight`() {
        TimeZone.setDefault(TimeZone.getTimeZone(PRAGUE))
        val today = LocalDate.of(2026, 10, 14)
        val window = FakeOverviewData()
        val vm = viewModelAt(noonIn(today), window)

        assertThat(window.fromTime).isEqualTo(windowStart(today))
        repeat(10) { vm.previousWindow() }
        assertThat(window.fromTime).isEqualTo(windowStart(today.minusDays(10)))
        repeat(10) { vm.nextWindow() }
        assertThat(window.fromTime).isEqualTo(windowStart(today))
        assertThat(window.toTime - window.fromTime).isEqualTo(24 * 3600 * 1000L)
    }

    /** 29 March 2026 has 23 hours. Going back 24 hours from the 30th landed on the 28th and skipped it. */
    @Test
    fun `stepping back over the spring clock change does not skip a day`() {
        TimeZone.setDefault(TimeZone.getTimeZone(PRAGUE))
        val window = FakeOverviewData()
        val vm = viewModelAt(noonIn(LocalDate.of(2026, 3, 30)), window)

        vm.previousWindow()
        assertThat(window.fromTime).isEqualTo(windowStart(LocalDate.of(2026, 3, 29)))
        vm.previousWindow()
        assertThat(window.fromTime).isEqualTo(windowStart(LocalDate.of(2026, 3, 28)))
    }

    /** 25 October 2026 has 25 hours. Going forward 24 hours from its midnight stayed on the 25th. */
    @Test
    fun `stepping forward over the autumn clock change does not stay on the same day`() {
        TimeZone.setDefault(TimeZone.getTimeZone(PRAGUE))
        val window = FakeOverviewData()
        val vm = viewModelAt(noonIn(LocalDate.of(2026, 10, 24)), window)

        vm.nextWindow()
        assertThat(window.fromTime).isEqualTo(windowStart(LocalDate.of(2026, 10, 25)))
        vm.nextWindow()
        assertThat(window.fromTime).isEqualTo(windowStart(LocalDate.of(2026, 10, 26)))
    }

    @Test
    fun `default uiState is empty date and at-now, progress starts idle`() {
        val state = sut.uiState.value
        // fromTime == 0L -> dateText stays blank; toTime(0) >= now(0) -> isAtNow true.
        assertThat(state.dateText).isEqualTo("")
        assertThat(state.isAtNow).isTrue()
        assertThat(sut.progress.value).isEqualTo(0)
    }

    @Test
    fun `onPause stops the history calculation`() = runTest(dispatcher) {
        sut.onPause()
        // onPause launches on viewModelScope now, so let that run before verifying.
        advanceUntilIdle()

        verifyBlocking(calculationWorkflow) { stopCalculation(CalculationWorkflow.HISTORY_CALCULATION, "onPause") }
    }

    @Test
    fun `selectedDateMillis returns the timestamp from dateUtil`() {
        whenever(dateUtil.getTimestampWithCurrentTimeOfDay(any())).thenReturn(999L)

        assertThat(sut.selectedDateMillis()).isEqualTo(999L)
    }

    @Test
    fun `changing the window clears the cache before recalculating`() = runTest(dispatcher) {
        // The worker refills the cache one series at a time and blood glucose comes last, so without
        // the clear the graph shows the previous day's readings under the new date for several
        // seconds. Order matters: clearing after starting the calculation could wipe fresh results.
        clearInvocations(cache, calculationWorkflow)

        sut.previousWindow()
        // previousWindow launches on viewModelScope now.
        advanceUntilIdle()

        val order = inOrder(cache, calculationWorkflow)
        order.verify(cache).reset()
        // runBlocking, because runCalculation is a suspend function and InOrder has no suspend form.
        runBlocking {
            order.verify(calculationWorkflow).runCalculation(
                job = any(), iobCobCalculator = anyOrNull(), overviewData = any(), cache = any(),
                signals = any(), reason = any(), end = any(), bgDataReload = any(), triggeredByNewBG = any()
            )
        }
    }

    @Test
    fun `jumping to now also clears the cache`() = runTest(dispatcher) {
        clearInvocations(cache)

        sut.jumpToNow()
        advanceUntilIdle()

        verify(cache).reset()
    }

    private companion object {

        /** Has daylight saving time, so the two clock-change days can be tested. */
        const val PRAGUE = "Europe/Prague"
    }
}

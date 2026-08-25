package app.aaps.ui.compose.history

import androidx.lifecycle.ViewModel
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import java.util.GregorianCalendar
import javax.inject.Inject

// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class HistoryViewModel @Inject constructor(
    private val historyScope: HistoryScope,
    private val calculationWorkflow: CalculationWorkflow,
    private val dateUtil: DateUtil,
    private val graphViewModelFactory: GraphViewModel.Factory
) : ViewModel() {

    val progress: StateFlow<Int> = historyScope.signals.progress

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        // Always start at "today" on (re-)entry. HistoryBrowserData is app-scoped @Singleton
        // so its time fields persist across VM lifetimes; reinitialising here avoids stale
        // windows flashing while the first calculation is in flight.
        setTime(dateUtil.now())
        runCalculation("init")
    }

    /** Creates the per-scope graph VM. Called by [HistoryScreen] via `viewModel(factory = …)`. */
    fun createGraphViewModel(): GraphViewModel =
        graphViewModelFactory.create(historyScope.cache, fullWindow = true)

    fun previousWindow() {
        adjustTimeRange(historyScope.overviewData.fromTime - WINDOW_MS)
        runCalculation("previousWindow")
    }

    fun nextWindow() {
        adjustTimeRange(historyScope.overviewData.fromTime + WINDOW_MS)
        runCalculation("nextWindow")
    }

    fun jumpToNow() {
        setTime(dateUtil.now())
        runCalculation("jumpToNow")
    }

    fun setDate(millis: Long) {
        setTime(dateUtil.mergeUtcDateToTimestamp(historyScope.overviewData.fromTime, millis))
        runCalculation("setDate")
    }

    fun onPause() {
        calculationWorkflow.stopCalculation(CalculationWorkflow.HISTORY_CALCULATION, "onPause")
    }

    fun selectedDateMillis(): Long =
        dateUtil.getTimestampWithCurrentTimeOfDay(historyScope.overviewData.fromTime)

    override fun onCleared() {
        calculationWorkflow.stopCalculation(CalculationWorkflow.HISTORY_CALCULATION, "onCleared")
        historyScope.onDestroy()
        super.onCleared()
    }

    // Round to start-of-day so windows snap to midnight.
    private fun setTime(start: Long) {
        GregorianCalendar().also { calendar ->
            calendar.timeInMillis = start
            calendar[Calendar.MILLISECOND] = 0
            calendar[Calendar.SECOND] = 0
            calendar[Calendar.MINUTE] = 0
            calendar[Calendar.HOUR_OF_DAY] = 0
            adjustTimeRange(calendar.timeInMillis)
        }
    }

    // The +100000 ms nudge here comes from the legacy GraphView era where exact boundary
    // timestamps produced off-by-one rounding at axis labels. Compose/Vico doesn't need it,
    // but workers still expect this window to match what they were given, so we keep it
    // until the calculation pipeline itself gets cleaned up.
    private fun adjustTimeRange(start: Long) {
        historyScope.overviewData.fromTime = start + 100000
        historyScope.overviewData.toTime = historyScope.overviewData.fromTime + WINDOW_MS
        historyScope.overviewData.endTime = historyScope.overviewData.toTime
        _uiState.value = buildState()
    }

    private fun runCalculation(from: String) {
        // Clear the old window before recalculating. The worker fills the cache one series at a time
        // and BG comes last, so without this the graph shows the new date over the previous day's
        // blood glucose for the several seconds the calculation takes - insulin and BG history that
        // never belonged to the day on the label. The progress bar already tells the user to wait.
        historyScope.cache.reset()
        calculationWorkflow.runCalculation(
            job = CalculationWorkflow.HISTORY_CALCULATION,
            iobCobCalculator = historyScope.iobCobCalculator,
            overviewData = historyScope.overviewData,
            cache = historyScope.cache,
            signals = historyScope.signals,
            reason = from,
            end = historyScope.overviewData.toTime,
            bgDataReload = true,
            triggeredByNewBG = false
        )
    }

    private fun buildState(): HistoryUiState {
        val fromTime = historyScope.overviewData.fromTime
        val toTime = historyScope.overviewData.toTime
        val text = if (fromTime == 0L) "" else dateUtil.dateString(fromTime)
        return HistoryUiState(
            dateText = text,
            isAtNow = toTime >= dateUtil.now()
        )
    }

    companion object {

        private val WINDOW_MS = T.hours(Constants.GRAPH_TIME_RANGE_HOURS.toLong()).msecs()
    }
}

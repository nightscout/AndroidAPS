package app.aaps.ui.compose.overview.graphs

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.configuration.Constants
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgInfoData
import app.aaps.core.interfaces.overview.graph.GraphConfig
import app.aaps.core.interfaces.overview.graph.GraphConfigRepository
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.overview.graph.SeriesType
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlin.concurrent.Volatile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * ViewModel for Overview graphs (Compose/Vico version).
 *
 * Architecture: Independent Series Updates
 * - Each series (BG readings, bucketed, IOB, COB, etc.) has its own StateFlow
 * - UI collects each flow separately
 * - Only the changed series triggers recomposition
 * - Time range is derived from all series (recalculates as data arrives)
 *
 * Workers emit to cache flows → ViewModel exposes flows → UI collects independently
 */

@Stable
@AssistedInject
class GraphViewModel(
    @Assisted cache: OverviewDataCache,
    @Assisted private val fullWindow: Boolean,
    private val graphConfigRepository: GraphConfigRepository,
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val dateUtil: DateUtil,
    private val rh: TextResolver
) : ViewModel() {

    @AssistedFactory
    interface Factory {

        /**
         * @param fullWindow span the x-axis over the whole calculated window rather than over the
         *   data in it. The overview wants the data hugged, so the axis does not reserve empty space
         *   around a gap in readings. The history browser wants the whole chosen day, so that a day
         *   whose readings start late still draws as a day, and today draws as a day with the
         *   remaining hours empty.
         */
        fun create(cache: OverviewDataCache, fullWindow: Boolean): GraphViewModel
    }

    // Chart config - updates when high/low mark preferences change
    private val _chartConfigFlow = MutableStateFlow(
        ChartConfig(
            highMark = preferences.get(UnitDoubleKey.OverviewHighMark),
            lowMark = preferences.get(UnitDoubleKey.OverviewLowMark)
        )
    )
    val chartConfigFlow: StateFlow<ChartConfig> = _chartConfigFlow.asStateFlow()

    init {
        // Update chart config when high/low mark preferences change
        // drop(1) skips the initial emission (already set in field initializer)
        preferences.observe(UnitDoubleKey.OverviewHighMark)
            .drop(1)
            .onEach { highMark -> _chartConfigFlow.update { it.copy(highMark = highMark) } }
            .launchIn(viewModelScope)
        preferences.observe(UnitDoubleKey.OverviewLowMark)
            .drop(1)
            .onEach { lowMark -> _chartConfigFlow.update { it.copy(lowMark = lowMark) } }
            .launchIn(viewModelScope)
    }

    // Graph configuration (which series on which graph)
    val graphConfigFlow: StateFlow<GraphConfig> = graphConfigRepository.graphConfigFlow

    fun updateGraphConfig(config: GraphConfig) = graphConfigRepository.update(config)

    // Individual series flows - each can trigger independent recomposition
    val bgReadingsFlow: StateFlow<List<BgDataPoint>> = cache.bgReadingsFlow
    val bucketedDataFlow: StateFlow<List<BgDataPoint>> = cache.bucketedDataFlow
    val predictionsFlow: StateFlow<List<BgDataPoint>> = cache.predictionsFlow

    // Secondary graph flows
    val iobGraphFlow = cache.iobGraphFlow
    val absIobGraphFlow = cache.absIobGraphFlow
    val cobGraphFlow = cache.cobGraphFlow
    val activityGraphFlow = cache.activityGraphFlow
    val bgiGraphFlow = cache.bgiGraphFlow
    val deviationsGraphFlow = cache.deviationsGraphFlow
    val ratioGraphFlow = cache.ratioGraphFlow
    val devSlopeGraphFlow = cache.devSlopeGraphFlow
    val varSensGraphFlow = cache.varSensGraphFlow
    val heartRateGraphFlow = cache.heartRateGraphFlow
    val stepsGraphFlow = cache.stepsGraphFlow
    val treatmentGraphFlow = cache.treatmentGraphFlow
    val epsGraphFlow = cache.epsGraphFlow
    val basalGraphFlow = cache.basalGraphFlow
    val targetLineFlow = cache.targetLineFlow
    val runningModeGraphFlow = cache.runningModeGraphFlow

    // NSClient status (pump/openAPS/uploader from Nightscout)
    val nsClientStatusFlow = cache.nsClientStatusFlow

    // =========================================================================
    // BG Info Section (Overview info display)
    // =========================================================================

    // Ticker flow for periodic updates (every 30 seconds) — used for timeAgo text and now line
    private val ticker30s = flow {
        while (true) {
            emit(dateUtil.now())
            delay(30_000L)
        }
    }

    /** Current time updated every 30s — use as key for now line position */
    val nowTimestamp: StateFlow<Long> = ticker30s.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = dateUtil.now()
    )

    // BG info UI state - combines bgInfo with periodic timeAgo updates
    val bgInfoState: StateFlow<BgInfoUiState> = combine(
        cache.bgInfoFlow,
        ticker30s
    ) { bgInfo, _ ->
        BgInfoUiState(
            bgInfo = bgInfo,
            timeAgoText = dateUtil.minAgo(rh, bgInfo?.timestamp)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BgInfoUiState(bgInfo = null, timeAgoText = "")
    )

    // Derived time range from actual data (recalculates as series arrive)
    // When PREDICTIONS overlay is enabled, extends into the future to fit prediction points;
    // otherwise clamps to toTime so the x-axis doesn't reserve empty future space.
    val derivedTimeRange: StateFlow<Pair<Long, Long>?> = combine(
        cache.bgReadingsFlow,
        cache.bucketedDataFlow,
        cache.predictionsFlow,
        cache.timeRangeFlow,
        graphConfigFlow
    ) { bgReadings, bucketedData, predictions, cacheTimeRange, graphConfig ->
        val showPredictions = SeriesType.PREDICTIONS in graphConfig.bgOverlays
        val effectivePredictions = if (showPredictions) predictions else emptyList()
        // Kept apart, because only one of the two may set the left edge. Predictions are future
        // points; a set holding nothing else has its minimum at roughly now.
        val historyTimestamps = (bgReadings + bucketedData).map { it.timestamp }
        val allTimestamps = historyTimestamps + effectivePredictions.map { it.timestamp }

        // fullWindow takes the same branch as "no data at all": the axis is the calculated window,
        // not the extent of what happens to be in it. Without this a day whose readings start in the
        // evening gets an axis only as wide as those readings, and zooming out to the whole day then
        // leaves the readings squashed into a corner instead of filling the day.
        val range = if (allTimestamps.isEmpty() || fullWindow) {
            cacheTimeRange?.let {
                val upper = if (showPredictions) it.endTime else it.toTime
                Pair(it.fromTime, upper)
            } ?: run {
                // Clean DB: no data and no cached range (worker never ran) — fall back to the
                // default window so the axis frame still renders instead of staying blank.
                val now = dateUtil.now()
                Pair(now - Constants.GRAPH_TIME_RANGE_HOURS * 3600_000L, now)
            }
        } else {
            // History only, never predictions - issue #5111. Predictions are future points, so when
            // the glucose and bucketed flows are empty and a loop run has published some, the
            // minimum of everything sits at roughly now: the axis became [now, now + horizon], the
            // current time was pinned to the left edge with no past behind it, the prediction was
            // the only thing drawn, and it could not be scrolled back because that was the whole
            // range. It came right on the next reading, which refilled the glucose flow and pulled
            // the edge back. Reported as "offline for a while, then it starts loading data".
            //
            // With no history at all, the calculated window is the honest left edge: it is the same
            // 24 hours the branch above uses, so an empty graph keeps the axis it had.
            val minTime = historyTimestamps.minOrNull()
                ?: cacheTimeRange?.fromTime
                ?: (dateUtil.now() - Constants.GRAPH_TIME_RANGE_HOURS * 3600_000L)
            val maxTime = allTimestamps.maxOrNull() ?: return@combine null
            val cacheUpper = cacheTimeRange?.let { if (showPredictions) it.endTime else it.toTime }
            val effectiveMax = if (cacheUpper != null) maxOf(maxTime, cacheUpper) else maxTime
            Pair(minTime, effectiveMax)
        }
        // The right edge every series is measured against. A series that stops before this is drawn
        // short of the axis, which is what a basal line ending before "now" looks like. `by` names
        // which input won, because the cure differs: `data` means a reading or a prediction reaches
        // past the cached range, `range` means the cached range is the wider of the two.
        aapsLogger.debug(LTag.UI) {
            val cacheUpper = cacheTimeRange?.let { if (showPredictions) it.endTime else it.toTime }
            val dataMax = allTimestamps.maxOrNull()
            "Graph axis: to=${dateUtil.dateAndTimeAndSecondsString(range.second)} " +
                "by=${if (!fullWindow && cacheUpper != null && dataMax != null && dataMax > cacheUpper) "data" else "range"} " +
                "data=${dataMax?.let { dateUtil.dateAndTimeAndSecondsString(it) } ?: "none"} " +
                "range=${cacheUpper?.let { dateUtil.dateAndTimeAndSecondsString(it) } ?: "none"} " +
                "now=${dateUtil.dateAndTimeAndSecondsString(dateUtil.now())} " +
                "predictions=${if (showPredictions) "on" else "off"}"
        }
        range
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    init {
        aapsLogger.debug(LTag.UI, "GraphViewModel initialized - exposing independent series flows")
    }

    @Volatile var lastInteractionMs: Long = 0L
        private set

    fun onGraphInteraction() {
        preferences.put(BooleanNonKey.ObjectivesScaleUsed, true)
        lastInteractionMs = dateUtil.now()
    }

    override fun onCleared() {
        super.onCleared()
        aapsLogger.debug(LTag.UI, "GraphViewModel cleared")
    }
}

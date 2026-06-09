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
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
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

/**
 * Static chart configuration (doesn't change during graph lifetime)
 */
data class ChartConfig(
    val highMark: Double,
    val lowMark: Double
)

/**
 * UI state for BG info section display
 */
@Immutable
data class BgInfoUiState(
    val bgInfo: BgInfoData?,
    val timeAgoText: String
)

@Stable
class GraphViewModel @AssistedInject constructor(
    @Assisted cache: OverviewDataCache,
    private val graphConfigRepository: GraphConfigRepository,
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val dateUtil: DateUtil,
    private val rh: ResourceHelper
) : ViewModel() {

    @AssistedFactory
    interface Factory {

        fun create(cache: OverviewDataCache): GraphViewModel
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
            emit(System.currentTimeMillis())
            delay(30_000L)
        }
    }

    /** Current time updated every 30s — use as key for now line position */
    val nowTimestamp: StateFlow<Long> = ticker30s.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = System.currentTimeMillis()
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
        val allTimestamps = (bgReadings + bucketedData + effectivePredictions).map { it.timestamp }

        if (allTimestamps.isEmpty()) {
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
            val minTime = allTimestamps.minOrNull() ?: return@combine null
            val maxTime = allTimestamps.maxOrNull() ?: return@combine null
            val cacheUpper = cacheTimeRange?.let { if (showPredictions) it.endTime else it.toTime }
            val effectiveMax = if (cacheUpper != null) maxOf(maxTime, cacheUpper) else maxTime
            Pair(minTime, effectiveMax)
        }
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
        lastInteractionMs = System.currentTimeMillis()
    }

    override fun onCleared() {
        super.onCleared()
        aapsLogger.debug(LTag.UI, "GraphViewModel cleared")
    }
}

package app.aaps.ui.compose.overview.graphs

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgInfoData
import app.aaps.core.interfaces.overview.graph.GraphConfig
import app.aaps.core.interfaces.overview.graph.GraphConfigRepository
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.displayText
import app.aaps.core.objects.extensions.round
import app.aaps.core.ui.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.Locale
import javax.inject.Inject

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

/**
 * UI state for IOB display
 */
@Immutable
data class IobUiState(
    val text: String = "",
    val iobTotal: Double = 0.0
)

/**
 * UI state for COB display
 */
@Immutable
data class CobUiState(
    val text: String = "",
    val carbsReq: Int = 0,
    val cobValue: Double = 0.0
)

/**
 * UI state for sensitivity / autosens display
 */
@Immutable
data class SensitivityUiState(
    val asText: String = "",
    val isfFrom: String = "",
    val isfTo: String = "",
    val dialogText: String = "",
    val ratio: Double = 1.0,
    val isEnabled: Boolean = true,
    val hasData: Boolean = false
)

@HiltViewModel
@Stable
class GraphViewModel @Inject constructor(
    cache: OverviewDataCache,
    private val graphConfigRepository: GraphConfigRepository,
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val dateUtil: DateUtil,
    private val rh: ResourceHelper,
    private val iobCobCalculator: IobCobCalculator,
    private val decimalFormatter: DecimalFormatter,
    private val loop: Loop,
    private val config: Config,
    private val persistenceLayer: PersistenceLayer,
    private val constraintChecker: ConstraintsChecker,
    private val profileFunction: ProfileFunction,
    private val processedDeviceStatusData: ProcessedDeviceStatusData,
    private val profileUtil: ProfileUtil,
    private val activePlugin: ActivePlugin
) : ViewModel() {

    // Chart config - updates when high/low mark preferences change
    val chartConfigFlow: StateFlow<ChartConfig>
        field = MutableStateFlow(
            ChartConfig(
                highMark = preferences.get(UnitDoubleKey.OverviewHighMark),
                lowMark = preferences.get(UnitDoubleKey.OverviewLowMark)
            )
        )

    init {
        // Update chart config when high/low mark preferences change
        // drop(1) skips the initial emission (already set in field initializer)
        preferences.observe(UnitDoubleKey.OverviewHighMark)
            .drop(1)
            .onEach { highMark -> chartConfigFlow.update { it.copy(highMark = highMark) } }
            .launchIn(viewModelScope)
        preferences.observe(UnitDoubleKey.OverviewLowMark)
            .drop(1)
            .onEach { lowMark -> chartConfigFlow.update { it.copy(lowMark = lowMark) } }
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
            timeAgoText = dateUtil.minOrSecAgo(rh, bgInfo?.timestamp)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BgInfoUiState(bgInfo = null, timeAgoText = "")
    )

    // =========================================================================
    // IOB / COB current values (updated every 2.5 minutes)
    // =========================================================================

    private val iobCobTicker = flow {
        while (true) {
            emit(Unit)
            delay(150_000L) // 2.5 minutes
        }
    }

    val iobUiState: StateFlow<IobUiState> = iobCobTicker.combine(cache.iobGraphFlow) { _, _ ->
        val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
        val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
        val total = bolusIob.iob + basalIob.basaliob
        IobUiState(
            text = rh.gs(R.string.format_insulin_units, total),
            iobTotal = total
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = IobUiState()
    )

    val cobUiState: StateFlow<CobUiState> = iobCobTicker.combine(cache.cobGraphFlow) { _, _ ->
        val cobInfo = iobCobCalculator.getCobInfo("GraphViewModel COB")
        var cobText = cobInfo.displayText(rh, decimalFormatter)
            ?: rh.gs(R.string.value_unavailable_short)
        var carbsReq = 0

        val constraintsProcessed = loop.lastRun?.constraintsProcessed
        val lastRun = loop.lastRun
        if (config.APS && constraintsProcessed != null && lastRun != null) {
            if (constraintsProcessed.carbsReq > 0) {
                val lastCarbsTime = persistenceLayer.getNewestCarbs()?.timestamp ?: 0L
                if (lastCarbsTime < lastRun.lastAPSRun) {
                    cobText += " ${constraintsProcessed.carbsReq}${rh.gs(R.string.required)}"
                }
                carbsReq = constraintsProcessed.carbsReq
            }
        }

        CobUiState(text = cobText, carbsReq = carbsReq, cobValue = cobInfo.displayCob ?: 0.0)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CobUiState()
    )

    // =========================================================================
    // Sensitivity / Autosens (updated every 2.5 minutes with IOB/COB)
    // =========================================================================

    val sensitivityUiState: StateFlow<SensitivityUiState> = iobCobTicker.combine(cache.iobGraphFlow) { _, _ ->
        buildSensitivityUiState()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SensitivityUiState()
    )

    private suspend fun buildSensitivityUiState(): SensitivityUiState {
        val lastAutosensData = iobCobCalculator.ads.getLastAutosensData("Overview", aapsLogger, dateUtil)
        val lastAutosensRatio = lastAutosensData?.autosensResult?.ratio
        val lastAutosensPercent = lastAutosensRatio?.let { it * 100 }

        val isEnabled = if (config.AAPSCLIENT) preferences.get(BooleanNonKey.AutosensUsedOnMainPhone)
        else constraintChecker.isAutosensModeEnabled().value()

        val profile = profileFunction.getProfile()
        val request = loop.lastRun?.request
        val isfMgdl = profile?.getProfileIsfMgdl()
        val isfForCarbs = profile?.getIsfMgdlForCarbs(dateUtil.now(), "Overview", config, processedDeviceStatusData)
        val variableSens =
            if (config.APS) request?.variableSens ?: 0.0
            else if (config.AAPSCLIENT) processedDeviceStatusData.getAPSResult()?.variableSens ?: 0.0
            else 0.0
        val ratioUsed = request?.autosensResult?.ratio ?: 1.0
        val units = profileFunction.getUnits()

        var asText = ""
        var isfFrom = ""
        var isfTo = ""
        val dialogText = ArrayList<String>()

        if (variableSens != isfMgdl && variableSens != 0.0 && isfMgdl != null) {
            // Variable ISF branch — hide "AS: 100%" from overview when ratio is exactly 100%
            lastAutosensPercent?.let {
                if (it != 100.0)
                    asText = rh.gs(R.string.autosens_short, it)
                dialogText.add(rh.gs(R.string.autosens_long, it))
            }
            isfFrom = String.format(Locale.getDefault(), "%1$.1f", profileUtil.fromMgdlToUnits(isfMgdl, units))
            isfTo = String.format(Locale.getDefault(), "%1$.1f", profileUtil.fromMgdlToUnits(variableSens, units))
            if (ratioUsed != 1.0 && ratioUsed != lastAutosensRatio)
                dialogText.add(rh.gs(R.string.algorithm_long, ratioUsed * 100))
            dialogText.add(rh.gs(R.string.isf_for_carbs, profileUtil.fromMgdlToUnits(isfForCarbs ?: 0.0, units)))
            if (config.APS) {
                activePlugin.activeAPS.getSensitivityOverviewString()?.let { dialogText.add(it) }
            }
        } else {
            // Standard autosens-only branch — skip when ratio is exactly 100%
            lastAutosensData?.let {
                val pct = it.autosensResult.ratio * 100
                if (pct != 100.0)
                    asText = rh.gs(R.string.autosens_short, pct)
            }
        }

        return SensitivityUiState(
            asText = asText,
            isfFrom = isfFrom,
            isfTo = isfTo,
            dialogText = dialogText.joinToString("\n"),
            ratio = lastAutosensRatio ?: 1.0,
            isEnabled = isEnabled,
            hasData = lastAutosensData != null
        )
    }

    // Derived time range from actual data (recalculates as series arrive)
    // Includes prediction timestamps so the x-axis extends into the future
    val derivedTimeRange: StateFlow<Pair<Long, Long>?> = combine(
        cache.bgReadingsFlow,
        cache.bucketedDataFlow,
        cache.predictionsFlow,
        cache.timeRangeFlow
    ) { bgReadings, bucketedData, predictions, cacheTimeRange ->
        // Combine all timestamps from all series including predictions
        val allTimestamps = (bgReadings + bucketedData + predictions).map { it.timestamp }

        if (allTimestamps.isEmpty()) {
            // Fall back to cache time range if no data yet (use endTime for predictions)
            cacheTimeRange?.let { Pair(it.fromTime, it.endTime) }
        } else {
            val minTime = allTimestamps.minOrNull() ?: return@combine null
            val maxTime = allTimestamps.maxOrNull() ?: return@combine null
            // Also consider endTime from cache (may extend beyond prediction points)
            val effectiveMax = if (cacheTimeRange != null) maxOf(maxTime, cacheTimeRange.endTime) else maxTime
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

    override fun onCleared() {
        super.onCleared()
        aapsLogger.debug(LTag.UI, "GraphViewModel cleared")
    }
}

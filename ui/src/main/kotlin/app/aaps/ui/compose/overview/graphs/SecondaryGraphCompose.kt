package app.aaps.ui.compose.overview.graphs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.configuration.Constants
import app.aaps.core.graph.vico.AdaptiveStep
import app.aaps.core.graph.vico.Square
import app.aaps.core.interfaces.overview.graph.BolusType
import app.aaps.core.interfaces.overview.graph.DeviationType
import app.aaps.core.interfaces.overview.graph.GraphDataPoint
import app.aaps.core.interfaces.overview.graph.SeriesType
import app.aaps.core.interfaces.overview.graph.TreatmentGraphData
import app.aaps.core.ui.compose.AapsTheme
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.VicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Position
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce

/**
 * CartesianChartModelProducer.update() skips notifying receivers (and thus skips recomputing axis
 * ranges) when a transaction's partials AND extraStore are both unchanged from the last one. Since
 * scrolling/zooming re-submits identical series data (only the visible window changed), stashing
 * the visible window here forces the extraStore to differ, so Vico actually reprocesses the
 * transaction instead of silently dropping it.
 */
private val VISIBLE_RANGE_KEY = ExtraStore.Key<Pair<Double?, Double?>>()

/**
 * Secondary graphs are much shorter than the BG graph, so Vico's ItemPlacer thins out a denser
 * tick request to avoid label overlap — and it does so by doubling the step rather than picking a
 * still-nice one, dropping the top boundary in the process (a max of 50 renders as 0/20/40 instead
 * of 0/25/50). Requesting fewer ticks up front avoids that thinning.
 *
 * IOB only occupies the bottom half of its graph (basal reserves the top half — see
 * `primaryYMax`), so it has roughly half the vertical pixels per tick that a full-height graph
 * does, and needs the more conservative count.
 */
private const val IOB_GRAPH_TICK_COUNT = 3

/** Tick count for series that use the graph's full height (COB, BGI/DEV/ACTIVITY/STEPS, SENS/VAR_SENS/HEART_RATE, DEV_SLOPE). */
private const val SECONDARY_GRAPH_TICK_COUNT = 5

/** Minimum half-width for SENSITIVITY's pivot scale — below this, snap to a fixed 95%/100%/105% scale instead of nice-ifying a near-zero deviation. */
private const val SENS_MIN_DEVIATION = 5.0

/**
 * Mostly-positive series: 0-floored by default (see [zeroFloorNiceRange]), allowing a
 * disparity-aware negative excursion without ever centering zero — the same rule used for IOB.
 */
private val ZERO_FLOOR_SERIES_TYPES = setOf(SeriesType.BGI, SeriesType.DEVIATIONS, SeriesType.ACTIVITY, SeriesType.STEPS, SeriesType.ABS_IOB)

/**
 * Wraps a step-based [VerticalAxis.ItemPlacer], filtering out labels/gridlines outside
 * [visibleMin]..[visibleMax]. Two independent uses:
 * - IOB/basal combo graph: the axis extends past the actual IOB data range to reserve headroom
 *   for the basal overlay (see the `primaryYMax` computation), so labels should stop at the real
 *   data boundary ([visibleMax]) instead of continuing into the reserved band above it.
 * - Dual-axis combos where primary is zero-floor-style: its axis is pushed below its own real
 *   floor purely to align its zero with the secondary/pivot series (see `dualAxisRanges`), so
 *   labels should stop at primary's own real floor ([visibleMin]) instead of showing a fake
 *   negative tick for a series that never actually goes negative.
 * Delegates everything else (margins, measurement, overlap-based thinning) to [delegate].
 */
private class ClampedVerticalAxisItemPlacer(
    private val delegate: VerticalAxis.ItemPlacer,
    private val visibleMin: () -> Double = { Double.NEGATIVE_INFINITY },
    private val visibleMax: () -> Double = { Double.POSITIVE_INFINITY }
) : VerticalAxis.ItemPlacer by delegate {

    override fun getLabelValues(
        context: CartesianDrawingContext,
        axisHeight: Float,
        maxLabelHeight: Float,
        position: Axis.Position.Vertical
    ): List<Double> = delegate.getLabelValues(context, axisHeight, maxLabelHeight, position).filter { it in visibleMin()..visibleMax() }

    override fun getLineValues(
        context: CartesianDrawingContext,
        axisHeight: Float,
        maxLabelHeight: Float,
        position: Axis.Position.Vertical
    ): List<Double>? = delegate.getLineValues(context, axisHeight, maxLabelHeight, position)?.filter { it in visibleMin()..visibleMax() }
}

/**
 * General-purpose secondary graph composable.
 *
 * Renders any combination of series types on a single chart.
 * Each series type has its own color and rendering style:
 * - IOB: Blue line with area fill + bolus markers (SMBs, normal boluses, extended boluses)
 * - COB: Orange adaptive-step line with area fill + carbs markers + failover dots
 * - Simple line series (AbsIOB, BGI, Sensitivity, VarSens, DevSlope, HR, Steps): Colored line with gradient fill
 * - Deviations: Per-type colored step lines with gradient fill (POSITIVE/NEGATIVE/EQUAL/UAM/CSF)
 */
@OptIn(FlowPreview::class)
@Composable
fun SecondaryGraphCompose(
    viewModel: GraphViewModel,
    seriesTypes: List<SeriesType>,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState,
    derivedTimeRange: Pair<Long, Long>?,
    nowTimestamp: Long,
    activityOverlay: Boolean = false,
    onVisibleRangeChanged: ((Pair<Double, Double>?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (seriesTypes.isEmpty()) return

    // Ensure DEV_SLOPE is always primary — it needs primary to render both dsMax and dsMin lines
    val orderedTypes = remember(seriesTypes) {
        val mustBePrimary = setOf(SeriesType.DEV_SLOPE, SeriesType.DEVIATIONS)
        if (seriesTypes.size >= 2 && seriesTypes[0] !in mustBePrimary && seriesTypes[1] in mustBePrimary)
            listOf(seriesTypes[1], seriesTypes[0])
        else
            seriesTypes
    }

    val hasRealTimeRange = derivedTimeRange != null
    val (minTimestamp, maxTimestamp) = derivedTimeRange ?: run {
        val now = System.currentTimeMillis()
        val dayAgo = now - Constants.GRAPH_TIME_RANGE_HOURS * 60 * 60 * 1000L
        dayAgo to now
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    val minX = 0.0
    val maxX = remember(minTimestamp, maxTimestamp) {
        timestampToX(maxTimestamp, minTimestamp)
    }
    val stableTimeRange = remember(minTimestamp / 60000, maxTimestamp / 60000) {
        minTimestamp to maxTimestamp
    }

    // =========================================================================
    // Collect data flows — only for selected series types
    // =========================================================================

    // Primary series = left axis (orderedTypes[0]), secondary series = right axis (orderedTypes[1])
    val primaryType = orderedTypes[0]
    val secondaryType = orderedTypes.getOrNull(1)

    // Specific case: BGI and DEVIATIONS share the same mg/dL unit and should share the vertical scale
    val shareAxis = (primaryType == SeriesType.BGI && secondaryType == SeriesType.DEVIATIONS) ||
        (primaryType == SeriesType.DEVIATIONS && secondaryType == SeriesType.BGI)

    val isDualAxis = secondaryType != null && !shareAxis
    val primaryTypes = if (shareAxis) orderedTypes else listOf(primaryType)

    val hasIob = primaryType == SeriesType.IOB
    val hasCob = primaryType == SeriesType.COB

    // Collect flows for primary series (includes share flow for DEV and BGI if together on the same graph with shareAxis true)
    val iobData = if (hasIob) viewModel.iobGraphFlow.collectAsStateWithLifecycle().value else null
    val cobData = if (hasCob) viewModel.cobGraphFlow.collectAsStateWithLifecycle().value else null
    val treatmentData = if (hasIob || hasCob) viewModel.treatmentGraphFlow.collectAsStateWithLifecycle().value else null
    val absIobData = if (primaryType == SeriesType.ABS_IOB) viewModel.absIobGraphFlow.collectAsStateWithLifecycle().value else null
    val bgiData = if (SeriesType.BGI in primaryTypes) viewModel.bgiGraphFlow.collectAsStateWithLifecycle().value else null
    val deviationsData = if (SeriesType.DEVIATIONS in primaryTypes) viewModel.deviationsGraphFlow.collectAsStateWithLifecycle().value else null
    val ratioData = if (primaryType == SeriesType.SENSITIVITY) viewModel.ratioGraphFlow.collectAsStateWithLifecycle().value else null
    val varSensData = if (primaryType == SeriesType.VAR_SENSITIVITY) viewModel.varSensGraphFlow.collectAsStateWithLifecycle().value else null
    val devSlopeData = if (primaryType == SeriesType.DEV_SLOPE) viewModel.devSlopeGraphFlow.collectAsStateWithLifecycle().value else null
    val hrData = if (primaryType == SeriesType.HEART_RATE) viewModel.heartRateGraphFlow.collectAsStateWithLifecycle().value else null
    val stepsData = if (primaryType == SeriesType.STEPS) viewModel.stepsGraphFlow.collectAsStateWithLifecycle().value else null
    // Activity data: either as primary series OR as overlay (on IOB graph)
    val needsActivity = primaryType == SeriesType.ACTIVITY || (activityOverlay && hasIob)
    val activityData = if (needsActivity) viewModel.activityGraphFlow.collectAsStateWithLifecycle().value else null
    // Basal overlay only when IOB is primary and single-axis (no room for basal with dual-axis)
    val basalData = if (hasIob && !isDualAxis) viewModel.basalGraphFlow.collectAsStateWithLifecycle().value else null

    // Collect flow for secondary (right axis) series - (emptyList() if DEV and BGI together)
    val secondaryLineData = if (!isDualAxis) emptyList() else when (secondaryType) {
        SeriesType.IOB             -> viewModel.iobGraphFlow.collectAsStateWithLifecycle().value.let {
            it.iob.map { p -> GraphDataPoint(p.timestamp, p.value) }
        }

        SeriesType.ABS_IOB         -> viewModel.absIobGraphFlow.collectAsStateWithLifecycle().value.absIob
        SeriesType.COB             -> viewModel.cobGraphFlow.collectAsStateWithLifecycle().value.cob
        SeriesType.BGI             -> viewModel.bgiGraphFlow.collectAsStateWithLifecycle().value.bgi
        SeriesType.DEVIATIONS      -> viewModel.deviationsGraphFlow.collectAsStateWithLifecycle().value.deviations.map {
            GraphDataPoint(it.timestamp, it.value)
        }

        SeriesType.SENSITIVITY     -> viewModel.ratioGraphFlow.collectAsStateWithLifecycle().value.ratio.map {
            // Stored as 100*(ratio-1), display shifted by +100 to show as percentage (90%, 110%) —
            // must match the same shift applied when SENSITIVITY is primary (see processedSimpleSeries).
            GraphDataPoint(it.timestamp, it.value + 100.0)
        }
        SeriesType.VAR_SENSITIVITY -> viewModel.varSensGraphFlow.collectAsStateWithLifecycle().value.varSens
        SeriesType.DEV_SLOPE       -> viewModel.devSlopeGraphFlow.collectAsStateWithLifecycle().value.dsMax
        SeriesType.HEART_RATE      -> viewModel.heartRateGraphFlow.collectAsStateWithLifecycle().value.heartRates
        SeriesType.STEPS           -> viewModel.stepsGraphFlow.collectAsStateWithLifecycle().value.steps
        SeriesType.ACTIVITY        -> viewModel.activityGraphFlow.collectAsStateWithLifecycle().value.activity
        SeriesType.PREDICTIONS     -> emptyList() // UI-only overlay flag, not a secondary series
    }

    // Cache last non-empty treatment data to survive reset() cycles
    val lastTreatmentData = remember { mutableStateOf(treatmentData ?: TreatmentGraphData(emptyList(), emptyList(), emptyList(), emptyList())) }
    if (treatmentData != null && (treatmentData.boluses.isNotEmpty() || treatmentData.carbs.isNotEmpty() || treatmentData.extendedBoluses.isNotEmpty())) {
        lastTreatmentData.value = treatmentData
    }

    // =========================================================================
    // Process all series into x/y coordinates
    // =========================================================================

    // Simple line series processing (excludes BASAL — it's a fixed flipped overlay on IOB)
    val processedSimpleSeries = remember(
        stableTimeRange, absIobData, bgiData, ratioData, varSensData, devSlopeData, hrData, stepsData, activityData
    ) {
        if (!hasRealTimeRange) return@remember emptyList()
        buildList {
            absIobData?.absIob?.takeIf { it.isNotEmpty() }?.let {
                add(SeriesType.ABS_IOB to processPoints(it, minTimestamp, minX, maxX))
            }
            bgiData?.let {
                if (it.bgi.isNotEmpty()) add(SeriesType.BGI to processPoints(it.bgi, minTimestamp, minX, maxX))
                // TODO: differentiate prediction line style (e.g., dashed) from historical
                if (it.bgiPrediction.isNotEmpty()) add(SeriesType.BGI to processPoints(it.bgiPrediction, minTimestamp, minX, maxX))
            }
            ratioData?.ratio?.takeIf { it.isNotEmpty() }?.let { pts ->
                // Stored as 100*(ratio-1), display shifted by +100 to show as percentage (90%, 110%)
                val shifted = pts.map { GraphDataPoint(it.timestamp, it.value + 100.0) }
                add(SeriesType.SENSITIVITY to processPoints(shifted, minTimestamp, minX, maxX))
            }
            varSensData?.varSens?.takeIf { it.isNotEmpty() }?.let {
                add(SeriesType.VAR_SENSITIVITY to processPoints(it, minTimestamp, minX, maxX))
            }
            devSlopeData?.let {
                if (it.dsMax.isNotEmpty()) add(SeriesType.DEV_SLOPE to processPoints(it.dsMax, minTimestamp, minX, maxX))
                // dsMin tagged separately via DEV_SLOPE_MIN marker (see below)
            }
            hrData?.heartRates?.takeIf { it.isNotEmpty() }?.let {
                add(SeriesType.HEART_RATE to processPoints(it, minTimestamp, minX, maxX))
            }
            stepsData?.steps?.takeIf { it.isNotEmpty() }?.let {
                add(SeriesType.STEPS to processPoints(it, minTimestamp, minX, maxX))
            }
            if (primaryType == SeriesType.ACTIVITY) {
                activityData?.let {
                    if (it.activity.isNotEmpty()) add(SeriesType.ACTIVITY to processPoints(it.activity, minTimestamp, minX, maxX))
                    if (it.activityPrediction.isNotEmpty()) add(SeriesType.ACTIVITY to processPoints(it.activityPrediction, minTimestamp, minX, maxX))
                }
            }
        }
    }

    // DevSlope min line — separate from simple series for distinct color
    val processedDevSlopeMin = remember(devSlopeData, stableTimeRange) {
        if (!hasRealTimeRange || devSlopeData == null || devSlopeData.dsMin.isEmpty()) return@remember emptyList()
        processPoints(devSlopeData.dsMin, minTimestamp, minX, maxX)
    }

    // Deviation line data — split by DeviationType for per-type colored lines with gradient fill.
    // Each type series has ALL x-coordinates; y=0 where the point's type doesn't match.
    // With Square connector this creates filled step-rectangles at each deviation point.
    val processedDeviationLines = remember(deviationsData, stableTimeRange) {
        if (!hasRealTimeRange || deviationsData == null || deviationsData.deviations.isEmpty())
            return@remember null
        val filtered = deviationsData.deviations
            .map { Triple(timestampToX(it.timestamp, minTimestamp), it.value, it.deviationType) }
            .filter { it.first in minX..maxX }
            .sortedBy { it.first }
        if (filtered.isEmpty()) return@remember null
        val allX = filtered.map { it.first }
        val typeValueMap = filtered.associate { it.first to (it.second to it.third) }
        // Build per-type y arrays: real value where type matches, 0.0 otherwise
        val perType = DeviationType.entries.associateWith { type ->
            allX.map { x ->
                val (value, pointType) = typeValueMap[x]!!
                if (pointType == type) value else 0.0
            }
        }
        // Only include types that have at least one non-zero value
        val activeTypes = perType.filter { (_, ys) -> ys.any { it != 0.0 } }
        ProcessedDeviationLines(allX, activeTypes)
    }

    // IOB line processing
    val processedIob = remember(iobData, stableTimeRange) {
        if (!hasRealTimeRange || iobData == null) return@remember emptyList()
        processPoints(iobData.iob, minTimestamp, minX, maxX)
    }

    // IOB treatment overlays processing
    val processedIobTreatments = remember(lastTreatmentData.value, stableTimeRange) {
        if (!hasRealTimeRange || !hasIob) return@remember ProcessedIobOverlays.EMPTY
        processIobOverlays(lastTreatmentData.value, minTimestamp, minX, maxX)
    }

    // COB line processing
    val processedCob = remember(cobData, stableTimeRange) {
        if (!hasRealTimeRange || cobData == null) return@remember Pair(emptyList(), emptyList())
        val cobFiltered = processPoints(cobData.cob, minTimestamp, minX, maxX)
        val failoverFiltered = cobData.failOverPoints.map { timestampToX(it.timestamp, minTimestamp) to it.cobValue }
            .let { filterToRange(it, minX, maxX) }
        cobFiltered to failoverFiltered
    }

    // COB carbs overlay processing
    val processedCarbs = remember(lastTreatmentData.value, stableTimeRange) {
        if (!hasRealTimeRange || !hasCob) return@remember emptyList()
        val pts = lastTreatmentData.value.carbs.map { timestampToX(it.timestamp, minTimestamp) to it.amount }
        filterToRange(pts, minX, maxX)
    }

    // Flipped basal overlay — fixed on IOB graphs, rendered as a second chart layer.
    // Uses negative Y values so area fill goes upward to y=0 (the top of the basal layer).
    val processedBasalProfile = remember(basalData, stableTimeRange) {
        if (!hasRealTimeRange || basalData == null) return@remember emptyList()
        val pts = processPoints(basalData.profileBasal, minTimestamp, minX, maxX)
        pts.map { (x, y) -> x to -y } // negate: y=0 at top, -maxBasal at bottom
    }
    val processedBasalActual = remember(basalData, stableTimeRange) {
        if (!hasRealTimeRange || basalData == null) return@remember emptyList()
        val pts = processPoints(basalData.actualBasal, minTimestamp, minX, maxX)
        pts.map { (x, y) -> x to -y }
    }

    val hasBasalLayer = hasIob && basalData != null && !isDualAxis

    // Activity overlay (scaled to fit within IOB's Y range, like BG graph)
    val processedActivityOverlay = remember(activityOverlay, hasIob, activityData, processedIob, stableTimeRange) {
        if (!hasRealTimeRange || !activityOverlay || !hasIob || activityData == null) return@remember Pair(emptyList(), emptyList())
        val maxAct = activityData.maxActivity
        if (maxAct <= 0.0) return@remember Pair(emptyList(), emptyList())
        val iobMaxY = processedIob.maxOfOrNull { it.second }?.coerceAtLeast(0.1) ?: 0.1
        val scale = iobMaxY * 0.8 / maxAct
        val hist = activityData.activity
            .map { timestampToX(it.timestamp, minTimestamp) to (it.value * scale) }
            .let { filterToRange(it, minX, maxX) }
        val pred = activityData.activityPrediction
            .map { timestampToX(it.timestamp, minTimestamp) to (it.value * scale) }
            .let { filterToRange(it, minX, maxX) }
        hist to pred
    }

    // Process secondary (right axis) series
    val processedSecondary = remember(secondaryLineData, stableTimeRange) {
        if (!hasRealTimeRange || secondaryLineData.isEmpty()) return@remember emptyList()
        processPoints(secondaryLineData, minTimestamp, minX, maxX)
    }

    // Visible-window bounds (same x-unit as processed* points — minutes from minTimestamp) for
    // windowing the Y-axis scale to only the currently scrolled/zoomed portion of this graph's
    // own chart, instead of the full loaded time range.
    // The reporter writes into a plain (non-Compose-state) holder on every draw pass — see
    // VisibleRangeHolder — so we poll it here rather than observing it directly, keeping Compose
    // state writes off the draw path.
    // Computed here (before the model-rebuild LaunchedEffect below) so visibleMinX/visibleMaxX can
    // be included in that effect's keys — Vico only reliably re-applies axis ranges via a real
    // modelProducer.runTransaction, not merely by swapping the CartesianLayerRangeProvider instance.
    val visibleRangeHolder = remember { VisibleRangeHolder() }
    val visibleRangeReporter = rememberVisibleRangeReporter(visibleRangeHolder)

    var rawVisibleRange by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var visibleRange by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    LaunchedEffect(visibleRangeHolder) {
        while (true) {
            delay(50)
            val current = visibleRangeHolder.value
            if (current != rawVisibleRange) rawVisibleRange = current
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { rawVisibleRange }
            .debounce(30)
            .collect { visibleRange = it }
    }

    // Expose this graph's own (already debounced) visible window upward — used by BgGraphCompose
    // to window its own axis from this graph's synced scroll/zoom instead of attaching its own
    // decoration (that was tried and found to break BG's own pinch-zoom gesture handling).
    val currentOnVisibleRangeChanged by rememberUpdatedState(onVisibleRangeChanged)
    LaunchedEffect(visibleRange) {
        currentOnVisibleRangeChanged?.invoke(visibleRange)
    }

    val visibleMinX = visibleRange?.first
    val visibleMaxX = visibleRange?.second

    // Single source of truth for the primary layer: build the (x, y, slot) specs synchronously,
    // in the exact order they are emitted to the model below. Deriving the line styles,
    // hasPrimaryData and the Y-range from this list (instead of from slot state set asynchronously
    // inside the LaunchedEffect) keeps the model, styles and axis range consistent within a single
    // frame. Otherwise the range provider races the model and the graph briefly renders against a
    // stale 0..1 axis until the next recomposition ("renders wrong, then fixes itself").
    val primarySeries = remember(
        processedDeviationLines, processedIob, processedIobTreatments, processedCob,
        processedCarbs, processedSimpleSeries, processedDevSlopeMin, processedActivityOverlay
    ) {
        buildList {
            // Deviation lines (per-type step lines) — first so other series draw on top
            processedDeviationLines?.series?.forEach { (type, yValues) ->
                add(PrimarySeriesSpec(processedDeviationLines.allX, yValues, SeriesSlot.DeviationLine(type)))
            }
            // IOB line
            if (processedIob.isNotEmpty())
                add(PrimarySeriesSpec(processedIob.map { it.first }, processedIob.map { it.second }, SeriesSlot.IobLine))
            // IOB overlays: SMBs (small, medium, large), normal boluses, extended boluses
            if (processedIobTreatments.smallSmbs.isNotEmpty())
                add(PrimarySeriesSpec(processedIobTreatments.smallSmbs.map { it.first }, processedIobTreatments.smallSmbs.map { it.second }, SeriesSlot.SmallSmb))
            if (processedIobTreatments.mediumSmbs.isNotEmpty())
                add(PrimarySeriesSpec(processedIobTreatments.mediumSmbs.map { it.first }, processedIobTreatments.mediumSmbs.map { it.second }, SeriesSlot.MediumSmb))
            if (processedIobTreatments.largeSmbs.isNotEmpty())
                add(PrimarySeriesSpec(processedIobTreatments.largeSmbs.map { it.first }, processedIobTreatments.largeSmbs.map { it.second }, SeriesSlot.LargeSmb))
            if (processedIobTreatments.normalBoluses.isNotEmpty())
                add(PrimarySeriesSpec(processedIobTreatments.normalBoluses.map { it.first }, processedIobTreatments.normalBoluses.map { it.second }, SeriesSlot.NormalBolus))
            processedIobTreatments.extBoluses.forEach { (start, end, amount) ->
                add(PrimarySeriesSpec(listOf(start, end), listOf(amount, amount), SeriesSlot.ExtBolus))
            }
            // COB line + failover dots + carbs markers
            val (cobFiltered, failoverFiltered) = processedCob
            if (cobFiltered.isNotEmpty())
                add(PrimarySeriesSpec(cobFiltered.map { it.first }, cobFiltered.map { it.second }, SeriesSlot.CobLine))
            if (failoverFiltered.isNotEmpty())
                add(PrimarySeriesSpec(failoverFiltered.map { it.first }, failoverFiltered.map { it.second }, SeriesSlot.FailoverDots))
            if (processedCarbs.isNotEmpty())
                add(PrimarySeriesSpec(processedCarbs.map { it.first }, processedCarbs.map { it.second }, SeriesSlot.CarbsMarker))
            // Simple line series
            processedSimpleSeries.forEach { (type, pts) ->
                if (pts.isNotEmpty())
                    add(PrimarySeriesSpec(pts.map { it.first }, pts.map { it.second }, SeriesSlot.SimpleLine(type)))
            }
            // DevSlope min (separate slot for magenta color)
            if (processedDevSlopeMin.isNotEmpty())
                add(PrimarySeriesSpec(processedDevSlopeMin.map { it.first }, processedDevSlopeMin.map { it.second }, SeriesSlot.DevSlopeMin))
            // Activity overlay (scaled) — only when activityOverlay=true on IOB graph
            val (actHist, actPred) = processedActivityOverlay
            if (actHist.isNotEmpty())
                add(PrimarySeriesSpec(actHist.map { it.first }, actHist.map { it.second }, SeriesSlot.ActivityOverlay))
            if (actPred.isNotEmpty())
                add(PrimarySeriesSpec(actPred.map { it.first }, actPred.map { it.second }, SeriesSlot.ActivityOverlay))
        }
    }
    val hasPrimaryData = primarySeries.isNotEmpty()

    LaunchedEffect(
        processedSimpleSeries,
        processedDevSlopeMin,
        processedDeviationLines,
        processedIob,
        processedIobTreatments,
        processedCob,
        processedCarbs,
        processedBasalProfile,
        processedBasalActual,
        processedSecondary,
        processedActivityOverlay,
        maxX,
        visibleMinX,
        visibleMaxX
    ) {
        // Always populate the model — even with no data / no real time range — so the chart frame
        // (axes, grid, now-line) renders the empty-state normalizer series instead of staying blank.
        // (Matches TreatmentBeltGraphCompose.) When data is absent the processed* lists are empty,
        // so only the normalizer is emitted; the primary range provider anchors an empty 0..1 axis.
        modelProducer.runTransaction {
            // Primary line layer — emit in the exact order of `primarySeries` so the line styles
            // (built from the same list) map 1:1 to the series.
            lineModel {
                primarySeries.forEach { spec -> series(x = spec.x, y = spec.y) }
                // Normalizer — ensures identical maxPointSize across all charts
                series(x = normalizerX(maxX), y = NORMALIZER_Y)
            }

            // Block 2 → Basal layer (end axis, flipped) OR secondary series layer (end axis)
            if (hasBasalLayer) {
                lineModel {
                    if (processedBasalActual.size >= 2) {
                        series(x = processedBasalActual.map { it.first }, y = processedBasalActual.map { it.second })
                    } else {
                        series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                    }
                    if (processedBasalProfile.size >= 2) {
                        series(x = processedBasalProfile.map { it.first }, y = processedBasalProfile.map { it.second })
                    } else {
                        series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                    }
                }
            } else if (isDualAxis) {
                lineModel {
                    if (processedSecondary.isNotEmpty()) {
                        series(x = processedSecondary.map { it.first }, y = processedSecondary.map { it.second })
                    } else {
                        series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                    }
                    // Normalizer for end axis layer
                    series(x = normalizerX(maxX), y = NORMALIZER_Y)
                }
            }

            // Forces Vico to reprocess this transaction even when the series data above is
            // identical to last time (see VISIBLE_RANGE_KEY doc) — otherwise scrolling/zooming
            // would re-submit the same partials and get silently skipped, never picking up the
            // updated primaryRangeProvider.
            extras { it[VISIBLE_RANGE_KEY] = visibleMinX to visibleMaxX }
        }
    }

    // =========================================================================
    // Line styles
    // =========================================================================

    val seriesColors = rememberSeriesColors()
    val iobLineStyle = rememberIobLineStyles()
    val cobLineStyle = rememberCobLineStyles()
    val normalizerLine = remember { createNormalizerLine() }

    val lines = remember(primarySeries, seriesColors, iobLineStyle, cobLineStyle, normalizerLine) {
        buildList {
            for (spec in primarySeries) {
                add(
                    when (val slot = spec.slot) {
                        is SeriesSlot.DeviationLine -> createDeviationLine(slot.type)
                        SeriesSlot.IobLine          -> iobLineStyle.iobLine
                        SeriesSlot.SmallSmb         -> iobLineStyle.smallSmbLine
                        SeriesSlot.MediumSmb        -> iobLineStyle.mediumSmbLine
                        SeriesSlot.LargeSmb         -> iobLineStyle.largeSmbLine
                        SeriesSlot.NormalBolus      -> iobLineStyle.bolusLine
                        SeriesSlot.ExtBolus         -> iobLineStyle.extBolusLine
                        SeriesSlot.CobLine          -> cobLineStyle.cobLine
                        SeriesSlot.FailoverDots     -> cobLineStyle.failoverDotsLine
                        SeriesSlot.CarbsMarker      -> cobLineStyle.carbsLine
                        SeriesSlot.DevSlopeMin      -> createDevSlopeMinLine()
                        SeriesSlot.ActivityOverlay  -> createSeriesLine(SeriesType.ACTIVITY, seriesColors)
                        is SeriesSlot.SimpleLine    -> createSeriesLine(slot.type, seriesColors)
                    }
                )
            }
            add(normalizerLine)
        }
    }

    // Basal layer line styles — same as BG graph (profile dashed, actual solid with fill)
    val basalColor = AapsTheme.elementColors.tempBasal
    val basalProfileLine = remember(basalColor) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(basalColor)),
            stroke = LineCartesianLayer.LineStroke.Dashed(
                thickness = 1.dp,
                cap = StrokeCap.Round,
                dashLength = 1.dp,
                gapLength = 2.dp
            ),
            areaFill = null,
            interpolator = Square
        )
    }
    val basalActualLine = remember(basalColor) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(basalColor)),
            stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 1.dp),
            areaFill = LineCartesianLayer.AreaFill.single(Fill(basalColor.copy(alpha = 0.3f))),
            interpolator = Square
        )
    }
    val basalLines = remember(basalActualLine, basalProfileLine) {
        listOf(basalActualLine, basalProfileLine)
    }

    // =========================================================================
    // Chart rendering
    // =========================================================================

    val timeFormatter = rememberTimeFormatter(minTimestamp)
    val bottomAxisItemPlacer = rememberBottomAxisItemPlacer(minTimestamp)
    val nowLineColor = MaterialTheme.colorScheme.onSurface
    val nowLine = rememberNowLine(minTimestamp, nowTimestamp, nowLineColor)
    val decorations = remember(nowLine, visibleRangeReporter) { listOf(nowLine, visibleRangeReporter) }

    // IOB (with basal overlay active): zero-floor nice range — 0 if the visible window has no
    // negative IOB, else a disparity-aware negative sliver (never centering zero) — then reserve
    // the top BASAL_HEIGHT_FRACTION of the height for basal, ABOVE the actual data range (not
    // above y=0 — those only coincide when nice.min is 0; when IOB has a negative excursion,
    // reserving "above 0" would eat into the negative portion's share and let positive IOB data
    // creep into the fraction of the axis meant for basal). Solving for axisMax such that
    // (nice.max - nice.min) / (axisMax - nice.min) == (1 - BASAL_HEIGHT_FRACTION) gives:
    // axisMax = nice.min + (nice.max - nice.min) / (1 - BASAL_HEIGHT_FRACTION)
    // — this reduces to the simpler nice.max / (1 - frac) exactly when nice.min == 0. The tick
    // step comes from the un-inflated nice range, so gridlines in the data region stay clean.
    // Result pairs the final (inflated) axis range with the un-inflated data max — the latter is
    // where tick labels must stop (see ClampedVerticalAxisItemPlacer) so they don't extend into
    // the reserved basal band above the actual IOB data.
    val primaryYMaxResult = remember(hasBasalLayer, processedIob, processedSimpleSeries, processedCob, processedDevSlopeMin, processedDeviationLines, visibleMinX, visibleMaxX) {
        if (!hasBasalLayer) return@remember null // auto-range when no basal
        val allY = windowedPrimaryY(visibleMinX, visibleMaxX, processedIob, processedCob.first, processedSimpleSeries, processedDevSlopeMin, processedDeviationLines)
        if (allY.isEmpty()) null
        else {
            val nice = zeroFloorNiceRange(allY.min(), allY.max().coerceAtLeast(0.1), IOB_GRAPH_TICK_COUNT)
            val axisMax = nice.min + (nice.max - nice.min) / (1 - BASAL_HEIGHT_FRACTION)
            NiceScale(nice.min, axisMax, nice.step) to nice.max
        }
    }
    val primaryYMax = primaryYMaxResult?.first
    val primaryIobDataMax = primaryYMaxResult?.second
    // Dual-axis zero alignment.
    // Why: Vico computes each vertical axis range independently, so y=0 on the
    // left axis lands at a different pixel row than y=0 on the right axis. When
    // both selected series can cross zero (e.g. IOB vs BGI), misaligned zeros
    // are visually misleading — a point "above zero" on one axis can appear
    // below a point that is "below zero" on the other. We compute a shared zero
    // fraction from both data extents and extend each side's range to match it.
    // Only applied to true dual-axis case (no basal overlay, secondary present).
    val dualAxisRanges = remember(
        isDualAxis, hasBasalLayer, processedIob, processedCob, processedSimpleSeries,
        processedDevSlopeMin, processedDeviationLines, processedSecondary, visibleMinX, visibleMaxX
    ) {
        if (!isDualAxis || hasBasalLayer) return@remember null
        val primaryY = windowedPrimaryY(visibleMinX, visibleMaxX, processedIob, processedCob.first, processedSimpleSeries, processedDevSlopeMin, processedDeviationLines)
        val secondaryY = windowedY(processedSecondary, visibleMinX, visibleMaxX)
        if (primaryY.isEmpty() || secondaryY.isEmpty()) return@remember null

        val primaryIsPivot = primaryType == SeriesType.SENSITIVITY || primaryType == SeriesType.DEV_SLOPE
        val secondaryIsPivot = secondaryType == SeriesType.SENSITIVITY || secondaryType == SeriesType.DEV_SLOPE
        if (primaryIsPivot != secondaryIsPivot) {
            // Exactly one side is pivot-centered (SENS/DEV_SLOPE), the other zero-floor-style
            // (e.g. COB, BGI): the pivot side gets a full nice-scaled symmetric range around its
            // own pivot — unconditionally, no branching on where its bounds happen to sit relative
            // to the pivot, so it never flips between "centered" and "pinned" between recomputes.
            // The zero-floor side gets a symmetric (-half,+half) container sized from its own
            // zero-floor nice range, so its zero lands exactly at the pivot's center (same pixel
            // row) and its curve — normally never negative — fills only the upper half, scaled
            // nicely up to its own real max (any rare negative sliver still fits, just not
            // symmetric-looking, since half also covers it).
            val isSensPivot = primaryType == SeriesType.SENSITIVITY || secondaryType == SeriesType.SENSITIVITY
            val pivot = if (isSensPivot) 100.0 else 0.0
            val pivotMinDeviation = if (isSensPivot) SENS_MIN_DEVIATION else 0.0
            return@remember if (primaryIsPivot) {
                val pivotNice = niceScaleAroundPivot(primaryY.min(), primaryY.max(), pivot, SECONDARY_GRAPH_TICK_COUNT, pivotMinDeviation)
                val zeroFloorNice = zeroFloorNiceRange(secondaryY.min(), secondaryY.max().coerceAtLeast(0.1), SECONDARY_GRAPH_TICK_COUNT)
                val zeroFloorHalf = maxOf(-zeroFloorNice.min, zeroFloorNice.max)
                // Primary is the pivot itself here — its own axis is fully meaningful in both
                // directions, no label clamping needed.
                AlignedRanges(pivotNice.min, pivotNice.max, -zeroFloorHalf, zeroFloorHalf)
            } else {
                val pivotNice = niceScaleAroundPivot(secondaryY.min(), secondaryY.max(), pivot, SECONDARY_GRAPH_TICK_COUNT, pivotMinDeviation)
                val zeroFloorNice = zeroFloorNiceRange(primaryY.min(), primaryY.max().coerceAtLeast(0.1), SECONDARY_GRAPH_TICK_COUNT)
                val zeroFloorHalf = maxOf(-zeroFloorNice.min, zeroFloorNice.max)
                // Primary is the zero-floor side, pushed into a symmetric container purely to
                // align its zero with the pivot's center — its own real floor (e.g. 0 for COB)
                // must still be the lowest label shown, or its axis reads as if COB/BGI/etc. can
                // go negative when it can't.
                AlignedRanges(-zeroFloorHalf, zeroFloorHalf, pivotNice.min, pivotNice.max, primaryLabelFloor = zeroFloorNice.min)
            }
        }
        if (primaryIsPivot || secondaryIsPivot) {
            // Both sides pivot-centered (SENS + DEV_SLOPE together — unusual but possible): keep
            // the existing pivot-aware zero alignment, both are symmetric around their own pivot
            // anyway so the old mechanism is adequate here.
            val primaryPivot = if (primaryType == SeriesType.SENSITIVITY) 100.0 else 0.0
            val secondaryPivot = if (secondaryType == SeriesType.SENSITIVITY) 100.0 else 0.0
            return@remember alignZeros(primaryY.min(), primaryY.max(), secondaryY.min(), secondaryY.max(), primaryPivot, secondaryPivot)
        }

        // Neither series is pivot-centered (e.g. COB + VAR_SENSITIVITY, or BGI + STEPS): primary
        // stays nice-scaled and zero-floored, secondary stays raw. Each series' own "negative
        // fraction" is computed independently — primary from its nice bounds, secondary from its
        // raw bounds — and the shared target is their average, so neither curve's height dominates
        // the compromise. Each axis is then widened just enough to hit that shared fraction,
        // anchored at whichever of its own real/nice bounds avoids clipping (see
        // fractionAlignedRange / fractionAlignedNiceRange) so nothing is ever clipped.
        val primaryNice = zeroFloorNiceRange(primaryY.min(), primaryY.max().coerceAtLeast(0.1), SECONDARY_GRAPH_TICK_COUNT)
        val primaryFraction = if (primaryNice.max > primaryNice.min) -primaryNice.min / (primaryNice.max - primaryNice.min) else 0.0

        val secondaryMax = secondaryY.max().coerceAtLeast(0.1)
        val secondaryFraction = if (secondaryY.min() < 0.0) -secondaryY.min() / (secondaryMax - secondaryY.min()) else 0.0

        val sharedFraction = (primaryFraction + secondaryFraction) / 2.0

        val (aMin, aMax) = fractionAlignedNiceRange(primaryNice.min, primaryNice.max, sharedFraction)
        val (bMin, bMax) = fractionAlignedRange(secondaryY.min(), secondaryMax, sharedFraction)
        // primaryNice.min is primary's own real floor (0 unless it has a genuine negative sliver);
        // aMin can be pushed below that purely to align zero with secondary — clamp labels back to
        // primary's own floor so its axis never shows a fake negative tick.
        AlignedRanges(aMin, aMax, bMin, bMax, primaryLabelFloor = primaryNice.min)
    }

    // Empty graph (only the normalizer series at y=0) would auto-range to a degenerate [0,0]
    // Y-axis and render an invisible frame. Anchor a default 0..1 range so the axis/grid/now-line
    // still draw, matching the BG graph's empty frame (BG is anchored by its in-range belt).
    // A (near-)constant primary series (e.g. a flat COB) is degenerate the same way: Vico collapses
    // its auto-range to 0..1 and clips the data, so derive an explicit range for that case too.
    val primaryConstantRange = remember(primarySeries) {
        val ys = primarySeries.flatMap { it.y }
        if (ys.isEmpty()) return@remember null
        val dataMin = ys.min()
        val dataMax = ys.max()
        if (dataMax - dataMin >= 1e-6) return@remember null // non-degenerate → let Vico auto-range
        val low = minOf(0.0, dataMax)
        low to maxOf(dataMax, low + 1.0)
    }
    // Single-axis, non-basal, non-degenerate case (e.g. standalone BGI/DevSlope/VarSens graphs):
    // Vico's own auto-range would compute from the full loaded model, not the visible window —
    // same flattening/clipping problem the basal and dual-axis cases solve above — so window it
    // here too, using the same union-of-primary-series helper.
    val primaryAutoRange = remember(
        hasBasalLayer, dualAxisRanges, processedIob, processedCob, processedSimpleSeries,
        processedDevSlopeMin, processedDeviationLines, visibleMinX, visibleMaxX
    ) {
        if (hasBasalLayer || dualAxisRanges != null) return@remember null
        val allY = windowedPrimaryY(visibleMinX, visibleMaxX, processedIob, processedCob.first, processedSimpleSeries, processedDevSlopeMin, processedDeviationLines)
        if (allY.isEmpty()) return@remember null
        val dataMin = allY.min()
        val dataMax = allY.max()
        if (dataMax - dataMin >= 1e-6) dataMin to dataMax
        else { // degenerate (near-flat) windowed subset — pad so Vico doesn't collapse the axis
            val low = minOf(0.0, dataMax)
            low to maxOf(dataMax, low + 1.0)
        }
    }
    // COB alone (single-axis, no basal/dual overlay): always starts at 0 regardless of the
    // visible minimum, with a "nice" max/tick-step instead of the raw windowed value. Takes
    // precedence over primaryAutoRange/primaryConstantRange for this specific case.
    val primaryCobScale = remember(hasCob, dualAxisRanges, processedCob, visibleMinX, visibleMaxX) {
        if (!hasCob || dualAxisRanges != null) return@remember null
        val allY = windowedY(processedCob.first, visibleMinX, visibleMaxX)
        if (allY.isEmpty()) return@remember null
        niceScale(0.0, allY.max().coerceAtLeast(0.0), SECONDARY_GRAPH_TICK_COUNT)
    }
    // BGI / DEVIATIONS / ACTIVITY / STEPS alone (single-axis, no basal/dual overlay): zero-floor
    // nice range — these are mostly-positive series, so 0 by default, but allows a disparity-aware
    // negative excursion without ever centering zero (same rule as IOB). Takes precedence over
    // primaryAutoRange/primaryConstantRange for this specific case.
    val primaryZeroFloorScale = remember(
        primaryType, dualAxisRanges, hasBasalLayer, processedIob, processedCob, processedSimpleSeries,
        processedDevSlopeMin, processedDeviationLines, visibleMinX, visibleMaxX
    ) {
        if (dualAxisRanges != null || hasBasalLayer) return@remember null
        if (primaryType !in ZERO_FLOOR_SERIES_TYPES) return@remember null
        val allY = windowedPrimaryY(visibleMinX, visibleMaxX, processedIob, processedCob.first, processedSimpleSeries, processedDevSlopeMin, processedDeviationLines)
        if (allY.isEmpty()) return@remember null
        zeroFloorNiceRange(allY.min(), allY.max(), SECONDARY_GRAPH_TICK_COUNT)
    }
    // VAR_SENSITIVITY / HEART_RATE alone: free-range nice scale — no zero anchor, since a
    // sensitivity ratio or heart rate has no meaningful "zero" reference point.
    val primaryFreeRangeScale = remember(
        primaryType, dualAxisRanges, hasBasalLayer, processedIob, processedCob, processedSimpleSeries,
        processedDevSlopeMin, processedDeviationLines, visibleMinX, visibleMaxX
    ) {
        if (dualAxisRanges != null || hasBasalLayer) return@remember null
        if (primaryType != SeriesType.VAR_SENSITIVITY && primaryType != SeriesType.HEART_RATE) return@remember null
        val allY = windowedPrimaryY(visibleMinX, visibleMaxX, processedIob, processedCob.first, processedSimpleSeries, processedDevSlopeMin, processedDeviationLines)
        if (allY.isEmpty()) return@remember null
        niceScale(allY.min(), allY.max(), SECONDARY_GRAPH_TICK_COUNT)
    }
    // SENSITIVITY (centered on 100%) / DEV_SLOPE (centered on 0) alone: pivot-centered nice
    // scale, so the pivot always lands exactly in the middle of the axis.
    val primaryPivotScale = remember(
        primaryType, dualAxisRanges, hasBasalLayer, processedIob, processedCob, processedSimpleSeries,
        processedDevSlopeMin, processedDeviationLines, visibleMinX, visibleMaxX
    ) {
        if (dualAxisRanges != null || hasBasalLayer) return@remember null
        val pivot = when (primaryType) {
            SeriesType.SENSITIVITY -> 100.0
            SeriesType.DEV_SLOPE   -> 0.0
            else                   -> return@remember null
        }
        val allY = windowedPrimaryY(visibleMinX, visibleMaxX, processedIob, processedCob.first, processedSimpleSeries, processedDevSlopeMin, processedDeviationLines)
        if (allY.isEmpty()) return@remember null
        val minDeviation = if (primaryType == SeriesType.SENSITIVITY) SENS_MIN_DEVIATION else 0.0
        niceScaleAroundPivot(allY.min(), allY.max(), pivot, SECONDARY_GRAPH_TICK_COUNT, minDeviation)
    }
    // Dynamic tick step for the start axis' ItemPlacer — null falls back to Vico's own automatic
    // spacing for series that don't have a "nice" rule yet.
    val primaryYStep = primaryYMax?.step ?: primaryCobScale?.step ?: primaryZeroFloorScale?.step
        ?: primaryFreeRangeScale?.step ?: primaryPivotScale?.step
    val primaryRangeProvider = remember(
        maxX, primaryYMax, dualAxisRanges, hasPrimaryData, primaryCobScale, primaryZeroFloorScale,
        primaryFreeRangeScale, primaryPivotScale, primaryConstantRange, primaryAutoRange
    ) {
        when {
            // Basal overlay case takes precedence (reserves the top BASAL_HEIGHT_FRACTION of axis for basal)
            primaryYMax != null          -> CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX, minY = primaryYMax.min, maxY = primaryYMax.max)
            // Dual-axis: use zero-aligned primary range so zeros line up with secondary axis
            dualAxisRanges != null       -> CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX, minY = dualAxisRanges.aMin, maxY = dualAxisRanges.aMax)
            // No data: anchor a default range so the empty frame is visible
            !hasPrimaryData              -> CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX, minY = 0.0, maxY = 1.0)
            // COB alone: nice 0-to-max scale
            primaryCobScale != null      -> CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX, minY = primaryCobScale.min, maxY = primaryCobScale.max)
            // BGI/DEVIATIONS/ACTIVITY/STEPS alone: zero-floor nice range (disparity-aware negative excursion)
            primaryZeroFloorScale != null -> CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX, minY = primaryZeroFloorScale.min, maxY = primaryZeroFloorScale.max)
            // VAR_SENSITIVITY/HEART_RATE alone: free-range nice scale, no zero anchor
            primaryFreeRangeScale != null -> CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX, minY = primaryFreeRangeScale.min, maxY = primaryFreeRangeScale.max)
            // SENSITIVITY/DEV_SLOPE alone: pivot-centered nice scale
            primaryPivotScale != null    -> CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX, minY = primaryPivotScale.min, maxY = primaryPivotScale.max)
            // Constant series (whole loaded range is flat): explicit range so it isn't collapsed to 0..1 and clipped
            primaryConstantRange != null -> CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX, minY = primaryConstantRange.first, maxY = primaryConstantRange.second)
            // Single-axis: window the range to the visible portion instead of Vico's full-model auto-range
            primaryAutoRange != null     -> CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX, minY = primaryAutoRange.first, maxY = primaryAutoRange.second)
            else                         -> CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX)
        }
    }
    // Basal range: 0 at top, -basalMaxY at bottom → basal occupies the top BASAL_HEIGHT_FRACTION of the height.
    // Windowed to the visible scroll/zoom range, like the primary IOB scale above, so basal doesn't
    // stay flattened/clipped when scrolled away from the loaded range's peak.
    val basalMaxY = remember(basalData, processedBasalProfile, processedBasalActual, visibleMinX, visibleMaxX) {
        if (basalData == null || basalData.maxBasal <= 0.0) return@remember 1.0
        // Profile/actual are stored sparsely (only at rate changes — see rebuildBasalGraph), so a
        // zoom window entirely inside one constant segment (e.g. an extended zero-temp) can contain
        // zero literal points for either series. Deliberately NOT using windowedY here: its own
        // ifEmpty fallback dumps the *whole loaded day's* values the moment the window has no
        // literal point, which is exactly what over-inflated this axis in the first place. Instead,
        // filter to the window with no fallback, and separately fold in the profile rate actually
        // in effect at the window start (stepValueAt), so a windowless stretch reflects just this
        // window's real profile level instead of the whole day's range.
        fun inWindow(x: Double) = visibleMinX == null || visibleMaxX == null || x in visibleMinX..visibleMaxX
        val literalWindowed = processedBasalProfile.filter { inWindow(it.first) }.map { it.second } +
            processedBasalActual.filter { inWindow(it.first) }.map { it.second }
        val hiddenProfileValue = visibleMinX?.let { stepValueAt(processedBasalProfile, it) }
        val windowedAbsMax = (literalWindowed + listOfNotNull(hiddenProfileValue)).map { -it }.maxOrNull()
        (windowedAbsMax?.takeIf { it > 0.0 } ?: hiddenProfileValue?.let { -it } ?: basalData.maxBasal) / BASAL_HEIGHT_FRACTION
    }
    val basalRangeProvider = remember(maxX, basalMaxY) {
        CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX, minY = -basalMaxY, maxY = 0.0)
    }

    // Secondary axis line style (colored by series type)
    val secondaryAxisColor = secondaryType?.let { seriesColors.colorFor(it) } ?: Color.Gray
    val secondaryAxisLine = remember(secondaryAxisColor) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(secondaryAxisColor)),
            areaFill = LineCartesianLayer.AreaFill.single(
                fill = Fill(Color.Transparent)
            )
        )
    }
    val secondaryAxisLines = remember(secondaryAxisLine, normalizerLine) { listOf(secondaryAxisLine, normalizerLine) }
    // Secondary range: zero-aligned counterpart to primary when available, otherwise auto-range.
    val secondaryRangeProvider = remember(maxX, dualAxisRanges) {
        if (dualAxisRanges != null)
            CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX, minY = dualAxisRanges.bMin, maxY = dualAxisRanges.bMax)
        else
            CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX)
    }

    // Build chart layers
    val primaryLayer = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(lines),
        rangeProvider = primaryRangeProvider,
        verticalAxisPosition = Axis.Position.Vertical.Start
    )

    // Common axis components
    val startAxisItemPlacer = remember(primaryYStep, primaryIobDataMax, dualAxisRanges?.primaryLabelFloor) {
        val stepPlacer = VerticalAxis.ItemPlacer.step({ primaryYStep })
        val dataMax = primaryIobDataMax
        val labelFloor = dualAxisRanges?.primaryLabelFloor
        when {
            dataMax != null   -> ClampedVerticalAxisItemPlacer(stepPlacer, visibleMax = { dataMax })
            labelFloor != null -> ClampedVerticalAxisItemPlacer(stepPlacer, visibleMin = { labelFloor })
            else               -> stepPlacer
        }
    }
    val startAxis = VerticalAxis.rememberStart(
        itemPlacer = startAxisItemPlacer,
        label = rememberTextComponent(
            style = TextStyle(color = MaterialTheme.colorScheme.onSurface),
            minWidth = TextComponent.MinWidth.fixed(30.dp)
        ),
        guideline = LineComponent(fill = Fill(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
    )
    val bottomAxis = HorizontalAxis.rememberBottom(
        valueFormatter = timeFormatter,
        itemPlacer = bottomAxisItemPlacer,
        label = rememberTextComponent(
            style = TextStyle(color = MaterialTheme.colorScheme.onSurface)
        ),
        guideline = LineComponent(fill = Fill(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
    )

    if (hasBasalLayer) {
        val basalLayer = rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(basalLines),
            rangeProvider = basalRangeProvider,
            verticalAxisPosition = Axis.Position.Vertical.End
        )
        CartesianChartHost(
            chart = rememberCartesianChart(
                primaryLayer, basalLayer,
                startAxis = startAxis,
                bottomAxis = bottomAxis, decorations = decorations, getXStep = { _, _, _ -> 1.0 }
            ),
            modelProducer = modelProducer,
            modifier = modifier.fillMaxWidth(),
            scrollState = scrollState, zoomState = zoomState
        )
    } else if (isDualAxis) {
        val secondaryLayer = rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(secondaryAxisLines),
            rangeProvider = secondaryRangeProvider,
            verticalAxisPosition = Axis.Position.Vertical.End
        )
        CartesianChartHost(
            chart = rememberCartesianChart(
                primaryLayer, secondaryLayer,
                startAxis = startAxis,
                bottomAxis = bottomAxis, decorations = decorations, getXStep = { _, _, _ -> 1.0 }
            ),
            modelProducer = modelProducer,
            modifier = modifier.fillMaxWidth(),
            scrollState = scrollState, zoomState = zoomState
        )
    } else {
        CartesianChartHost(
            chart = rememberCartesianChart(
                primaryLayer,
                startAxis = startAxis,
                bottomAxis = bottomAxis, decorations = decorations, getXStep = { _, _, _ -> 1.0 }
            ),
            modelProducer = modelProducer,
            modifier = modifier.fillMaxWidth(),
            scrollState = scrollState, zoomState = zoomState
        )
    }
}

// =========================================================================
// Series slot identifiers (for matching line styles to data series)
// =========================================================================

/** One primary-layer series: its x/y data plus the slot that selects its line style. */
private class PrimarySeriesSpec(val x: List<Double>, val y: List<Double>, val slot: SeriesSlot)

private sealed class SeriesSlot {
    data class DeviationLine(val type: DeviationType) : SeriesSlot()
    data object IobLine : SeriesSlot()
    data object SmallSmb : SeriesSlot()
    data object MediumSmb : SeriesSlot()
    data object LargeSmb : SeriesSlot()
    data object NormalBolus : SeriesSlot()
    data object ExtBolus : SeriesSlot()
    data object CobLine : SeriesSlot()
    data object FailoverDots : SeriesSlot()
    data object CarbsMarker : SeriesSlot()
    data class SimpleLine(val type: SeriesType) : SeriesSlot()
    data object DevSlopeMin : SeriesSlot()
    data object ActivityOverlay : SeriesSlot()
}

// =========================================================================
// Data processing helpers
// =========================================================================

/**
 * Y-values of [points] restricted to [visibleMinX]..[visibleMaxX], falling back to the full
 * (unwindowed) values when the visible window currently has no points (e.g. scrolled into a
 * future gap with no data).
 */
private fun windowedY(points: List<Pair<Double, Double>>, visibleMinX: Double?, visibleMaxX: Double?): List<Double> {
    fun inWindow(x: Double) = visibleMinX == null || visibleMaxX == null || x in visibleMinX..visibleMaxX
    return points.filter { inWindow(it.first) }.map { it.second }.ifEmpty { points.map { it.second } }
}

/**
 * Value in effect at [x] for a sparse change-point-only step series — [points] only stores a new
 * entry when the value changes (see `rebuildBasalGraph`), so a narrow zoom window can contain zero
 * literal points even though the series has a well-defined value throughout it. The value actually
 * in effect at any [x] is whatever the most recent point at-or-before [x] holds (falls back to the
 * first point if [x] precedes all of them).
 */
private fun stepValueAt(points: List<Pair<Double, Double>>, x: Double): Double? =
    points.lastOrNull { it.first <= x }?.second ?: points.firstOrNull()?.second

/**
 * Union of y-values across all primary-layer series (IOB, COB, simple series, DevSlope-min,
 * deviation lines), restricted to [visibleMinX]..[visibleMaxX]. Falls back to the full
 * (unwindowed) union when the visible window currently has no points across ANY of these series
 * (e.g. scrolled into a future gap with no data) — the union is computed first, then the fallback
 * applies to the whole set, so windowed points from one series are never mixed with unwindowed
 * points from another.
 *
 * [processedDeviationLines] stores y-values per type without paired x, so pairs are reconstituted
 * by zipping each type's y-array against the shared allX before filtering.
 */
private fun windowedPrimaryY(
    visibleMinX: Double?,
    visibleMaxX: Double?,
    processedIob: List<Pair<Double, Double>>,
    processedCobY: List<Pair<Double, Double>>,
    processedSimpleSeries: List<Pair<SeriesType, List<Pair<Double, Double>>>>,
    processedDevSlopeMin: List<Pair<Double, Double>>,
    processedDeviationLines: ProcessedDeviationLines?
): List<Double> {
    fun inWindow(x: Double) = visibleMinX == null || visibleMaxX == null || x in visibleMinX..visibleMaxX
    fun deviationY(filterToWindow: Boolean): List<Double> =
        processedDeviationLines?.let { lines ->
            lines.series.values.flatMap { ys ->
                lines.allX.zip(ys)
                    .filter { (x, _) -> !filterToWindow || inWindow(x) }
                    .map { it.second }
            }
        } ?: emptyList()

    val windowed = buildList {
        addAll(processedIob.filter { inWindow(it.first) }.map { it.second })
        addAll(processedCobY.filter { inWindow(it.first) }.map { it.second })
        for ((_, pts) in processedSimpleSeries) addAll(pts.filter { inWindow(it.first) }.map { it.second })
        addAll(processedDevSlopeMin.filter { inWindow(it.first) }.map { it.second })
        addAll(deviationY(filterToWindow = true))
    }
    return windowed.ifEmpty {
        buildList {
            addAll(processedIob.map { it.second })
            addAll(processedCobY.map { it.second })
            for ((_, pts) in processedSimpleSeries) addAll(pts.map { it.second })
            addAll(processedDevSlopeMin.map { it.second })
            addAll(deviationY(filterToWindow = false))
        }
    }
}

@Suppress("SameParameterValue")
private fun processPoints(points: List<GraphDataPoint>, minTimestamp: Long, minX: Double, maxX: Double): List<Pair<Double, Double>> {
    val pts = points.map { timestampToX(it.timestamp, minTimestamp) to it.value }
    return filterToRange(pts, minX, maxX)
}

/** Process IOB treatment overlays: split SMBs by size, extract boluses and extended boluses */
@Suppress("SameParameterValue")
private fun processIobOverlays(data: TreatmentGraphData, minTimestamp: Long, minX: Double, maxX: Double): ProcessedIobOverlays {
    // Filter SMBs to visible range before computing size thresholds (prevents off-screen outliers from distorting buckets)
    val smbs = data.boluses.filter { it.bolusType == BolusType.SMB && timestampToX(it.timestamp, minTimestamp) in minX..maxX }

    var smallSmbs: List<Pair<Double, Double>> = emptyList()
    var mediumSmbs: List<Pair<Double, Double>> = emptyList()
    var largeSmbs: List<Pair<Double, Double>> = emptyList()

    if (smbs.isNotEmpty()) {
        if (smbs.size <= 1 || smbs.minOf { it.amount } == smbs.maxOf { it.amount }) {
            mediumSmbs = smbs.map { timestampToX(it.timestamp, minTimestamp) to 0.0 }
        } else {
            val minAmount = smbs.minOf { it.amount }
            val range = smbs.maxOf { it.amount } - minAmount
            val smallThreshold = minAmount + range / 3.0
            val largeThreshold = minAmount + 2.0 * range / 3.0

            val small = mutableListOf<Pair<Double, Double>>()
            val medium = mutableListOf<Pair<Double, Double>>()
            val large = mutableListOf<Pair<Double, Double>>()

            for (smb in smbs) {
                val point = timestampToX(smb.timestamp, minTimestamp) to 0.0
                when {
                    smb.amount < smallThreshold -> small.add(point)
                    smb.amount < largeThreshold -> medium.add(point)
                    else                        -> large.add(point)
                }
            }
            smallSmbs = small
            mediumSmbs = medium
            largeSmbs = large
        }
    }

    val normalBoluses = data.boluses.filter { it.bolusType == BolusType.NORMAL }
    val bolusFiltered = if (normalBoluses.isNotEmpty()) {
        filterToRange(normalBoluses.map { timestampToX(it.timestamp, minTimestamp) to it.amount }, minX, maxX)
    } else emptyList()

    val extBoluses = data.extendedBoluses.mapNotNull { eb ->
        val startX = timestampToX(eb.timestamp, minTimestamp)
        val endX = timestampToX(eb.timestamp + eb.duration, minTimestamp)
        if (endX < minX || startX > maxX) return@mapNotNull null
        val clampedStart = startX.coerceIn(minX, maxX)
        val clampedEnd = endX.coerceIn(minX, maxX)
        if (clampedStart == clampedEnd) return@mapNotNull null
        Triple(clampedStart, clampedEnd, eb.amount)
    }

    return ProcessedIobOverlays(
        filterToRange(smallSmbs, minX, maxX),
        filterToRange(mediumSmbs, minX, maxX),
        filterToRange(largeSmbs, minX, maxX),
        bolusFiltered, extBoluses
    )
}

private data class ProcessedIobOverlays(
    val smallSmbs: List<Pair<Double, Double>>,
    val mediumSmbs: List<Pair<Double, Double>>,
    val largeSmbs: List<Pair<Double, Double>>,
    val normalBoluses: List<Pair<Double, Double>>,
    val extBoluses: List<Triple<Double, Double, Double>>
) {

    companion object {

        val EMPTY = ProcessedIobOverlays(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}

// =========================================================================
// Line styles
// =========================================================================

/** Color map for simple line series */
data class SeriesColors(
    val iob: Color,
    val absIob: Color,
    val cob: Color,
    val bgi: Color,
    val deviations: Color,
    val sensitivity: Color,
    val varSensitivity: Color,
    val devSlope: Color,
    val heartRate: Color,
    val steps: Color,
    val activity: Color
) {

    fun colorFor(type: SeriesType): Color = when (type) {
        SeriesType.IOB             -> iob
        SeriesType.ABS_IOB         -> absIob
        SeriesType.COB             -> cob
        SeriesType.BGI             -> bgi
        SeriesType.DEVIATIONS      -> deviations
        SeriesType.SENSITIVITY     -> sensitivity
        SeriesType.VAR_SENSITIVITY -> varSensitivity
        SeriesType.DEV_SLOPE       -> devSlope
        SeriesType.HEART_RATE      -> heartRate
        SeriesType.STEPS           -> steps
        SeriesType.ACTIVITY        -> activity
        SeriesType.PREDICTIONS     -> activity // unused — PREDICTIONS is a BG overlay flag, not a secondary series
    }
}

@Composable
fun rememberSeriesColors(): SeriesColors {
    val iobColor = AapsTheme.generalColors.iobPrediction
    val cobColor = AapsTheme.generalColors.cobPrediction
    val onSurface = MaterialTheme.colorScheme.onSurface
    return remember(iobColor, cobColor, onSurface) {
        SeriesColors(
            iob = iobColor,                         // #1e88e5 blue (matches @color/iob)
            absIob = iobColor,                      // same blue as IOB
            cob = cobColor,                         // #FB8C00 orange (matches @color/cob)
            bgi = Color(0xFF00EEEE),                // cyan (matches @color/bgi)
            deviations = Color(0xFF00EEEE),         // cyan (matches @color/bgi — legacy uses per-type colors)
            sensitivity = onSurface,                // #000000 light / #FFFFFF dark (matches @color/ratio)
            varSensitivity = onSurface,             // same as sensitivity
            devSlope = Color(0xFFFFFF00),            // yellow (matches @color/devSlopePos)
            heartRate = Color(0xFFFFFF66),           // pale yellow (matches @color/heartRate #FFFFFF66)
            steps = Color(0xFF66FFB8),              // mint green (matches @color/steps)
            activity = Color(0xFFD3F166)            // lime green (matches @color/activity)
        )
    }
}

/** DevSlope min line — magenta, no fill (matches @color/devSlopeNeg #FF00FF) */
private fun createDevSlopeMinLine(): LineCartesianLayer.Line {
    return LineCartesianLayer.Line(
        fill = LineCartesianLayer.LineFill.single(Fill(Color(0xFFFF00FF))),
        areaFill = null
    )
}

/** Create a line style for a given series type, matching legacy rendering */
fun createSeriesLine(type: SeriesType, colors: SeriesColors): LineCartesianLayer.Line {
    val color = colors.colorFor(type)
    return when (type) {
        // Step function with area fill (like IOB)
        SeriesType.ABS_IOB                                                       -> LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(color)),
            areaFill = LineCartesianLayer.AreaFill.single(
                Fill(Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), Color.Transparent)))
            ),
            interpolator = Square
        )
        // Line only, no fill
        SeriesType.DEV_SLOPE, SeriesType.SENSITIVITY, SeriesType.VAR_SENSITIVITY -> LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(color)),
            areaFill = null
        )
        // Points/dots only — no connecting line
        SeriesType.HEART_RATE, SeriesType.STEPS                                  -> LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
            areaFill = null,
            pointProvider = LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    component = ShapeComponent(fill = Fill(color), shape = CircleShape),
                    size = 4.dp
                )
            )
        )
        // Default: smooth line with gradient area fill
        else                                                                     -> LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(color)),
            areaFill = LineCartesianLayer.AreaFill.single(
                Fill(Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), Color.Transparent)))
            )
        )
    }
}

// =========================================================================
// IOB line styles (bolus markers)
// =========================================================================

data class IobLineStyles(
    val iobLine: LineCartesianLayer.Line,
    val smallSmbLine: LineCartesianLayer.Line,
    val mediumSmbLine: LineCartesianLayer.Line,
    val largeSmbLine: LineCartesianLayer.Line,
    val bolusLine: LineCartesianLayer.Line,
    val extBolusLine: LineCartesianLayer.Line
)

@Composable
fun rememberIobLineStyles(): IobLineStyles {
    val iobColor = AapsTheme.generalColors.iobPrediction
    val smbColor = AapsTheme.elementColors.insulin
    val extBolusColor = AapsTheme.elementColors.extendedBolus

    val bolusLabelComponent = remember(smbColor) {
        TextComponent(textStyle = TextStyle(color = smbColor, fontSize = 16.sp))
    }
    val bolusValueFormatter = remember {
        CartesianValueFormatter { _, value, _ -> formatBolusLabel(value) }
    }
    val extBolusLabelComponent = remember(extBolusColor) {
        TextComponent(textStyle = TextStyle(color = extBolusColor, fontSize = 16.sp))
    }
    val extBolusValueFormatter = remember {
        CartesianValueFormatter { _, value, _ -> formatBolusLabel(value) }
    }

    return remember(iobColor, smbColor, extBolusColor, bolusLabelComponent, bolusValueFormatter, extBolusLabelComponent, extBolusValueFormatter) {
        IobLineStyles(
            iobLine = LineCartesianLayer.Line(
                fill = LineCartesianLayer.LineFill.single(Fill(iobColor)),
                areaFill = LineCartesianLayer.AreaFill.single(
                    Fill(Brush.verticalGradient(listOf(iobColor.copy(alpha = 1f), Color.Transparent)))
                ),
                interpolator = Square
            ),
            smallSmbLine = LineCartesianLayer.Line(
                fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
                areaFill = null,
                pointProvider = LineCartesianLayer.PointProvider.single(
                    LineCartesianLayer.Point(component = ShapeComponent(fill = Fill(smbColor), shape = TriangleShape), size = 10.dp)
                )
            ),
            mediumSmbLine = LineCartesianLayer.Line(
                fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
                areaFill = null,
                pointProvider = LineCartesianLayer.PointProvider.single(
                    LineCartesianLayer.Point(component = ShapeComponent(fill = Fill(smbColor), shape = TriangleShape), size = 16.dp)
                )
            ),
            largeSmbLine = LineCartesianLayer.Line(
                fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
                areaFill = null,
                pointProvider = LineCartesianLayer.PointProvider.single(
                    LineCartesianLayer.Point(component = ShapeComponent(fill = Fill(smbColor), shape = TriangleShape), size = 22.dp)
                )
            ),
            bolusLine = LineCartesianLayer.Line(
                fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
                areaFill = null,
                pointProvider = LineCartesianLayer.PointProvider.single(
                    LineCartesianLayer.Point(component = ShapeComponent(fill = Fill(smbColor), shape = InvertedTriangleShape), size = 22.dp)
                ),
                dataLabel = bolusLabelComponent,
                dataLabelPosition = Position.Vertical.Top,
                dataLabelValueFormatter = bolusValueFormatter
            ),
            extBolusLine = LineCartesianLayer.Line(
                fill = LineCartesianLayer.LineFill.single(Fill(extBolusColor)),
                areaFill = null,
                dataLabel = extBolusLabelComponent,
                dataLabelPosition = Position.Vertical.Top,
                dataLabelValueFormatter = extBolusValueFormatter
            )
        )
    }
}

// =========================================================================
// COB line styles (carbs markers)
// =========================================================================

data class CobLineStyles(
    val cobLine: LineCartesianLayer.Line,
    val failoverDotsLine: LineCartesianLayer.Line,
    val carbsLine: LineCartesianLayer.Line
)

@Composable
fun rememberCobLineStyles(): CobLineStyles {
    val cobColor = AapsTheme.generalColors.cobPrediction
    val carbsColor = AapsTheme.elementColors.carbs

    val carbsLabelComponent = remember(carbsColor) {
        TextComponent(textStyle = TextStyle(color = carbsColor, fontSize = 14.sp))
    }
    val carbsValueFormatter = remember {
        CartesianValueFormatter { _, value, _ -> formatCarbsLabel(value) }
    }

    return remember(cobColor, carbsColor, carbsLabelComponent, carbsValueFormatter) {
        CobLineStyles(
            cobLine = LineCartesianLayer.Line(
                fill = LineCartesianLayer.LineFill.single(Fill(cobColor)),
                areaFill = LineCartesianLayer.AreaFill.single(
                    Fill(Brush.verticalGradient(listOf(cobColor.copy(alpha = 1f), Color.Transparent)))
                ),
                interpolator = AdaptiveStep
            ),
            failoverDotsLine = LineCartesianLayer.Line(
                fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
                areaFill = null,
                pointProvider = LineCartesianLayer.PointProvider.single(
                    LineCartesianLayer.Point(component = ShapeComponent(fill = Fill(cobColor), shape = CircleShape), size = 6.dp)
                )
            ),
            carbsLine = LineCartesianLayer.Line(
                fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
                areaFill = null,
                pointProvider = LineCartesianLayer.PointProvider.single(
                    LineCartesianLayer.Point(component = ShapeComponent(fill = Fill(carbsColor), shape = InvertedTriangleShape), size = 22.dp)
                ),
                dataLabel = carbsLabelComponent,
                dataLabelPosition = Position.Vertical.Top,
                dataLabelValueFormatter = carbsValueFormatter
            )
        )
    }
}

// =========================================================================
// Label formatters (shared with IOB/COB graph composables)
// =========================================================================

/** Formats bolus amount: 1.0->"1", 1.2->"1.2", 0.8->".8" (drop leading zero) */
private fun formatBolusLabel(value: Double): String {
    if (value == 0.0) return ""
    val formatted = "%.2f".format(value).trimEnd('0').trimEnd('.').trimEnd(',')
    return if (formatted.startsWith("0.")) formatted.substring(1) else formatted
}

/** Formats carbs amount: 45.0->"45", 7.5->"7.5", 0.0->"" */
private fun formatCarbsLabel(value: Double): String {
    if (value == 0.0) return ""
    return "%.1f".format(value).trimEnd('0').trimEnd('.').trimEnd(',')
}

// =========================================================================
// Deviation line styles (per-type step lines with gradient fill)
// =========================================================================

private val DEVIATION_COLOR_POSITIVE = Color(0xC000FF00) // green (matches @color/deviationGreen)
private val DEVIATION_COLOR_NEGATIVE = Color(0xC0FF0000) // red (matches @color/deviationRed)
private val DEVIATION_COLOR_EQUAL = Color(0x72000000)    // black (matches @color/deviationBlack)
private val DEVIATION_COLOR_UAM = Color(0xFFC9BD60)      // yellow (matches @color/uam)
private val DEVIATION_COLOR_CSF = Color(0xC8666666)      // grey (matches @color/deviationGrey)

private fun deviationColor(type: DeviationType): Color = when (type) {
    DeviationType.POSITIVE -> DEVIATION_COLOR_POSITIVE
    DeviationType.NEGATIVE -> DEVIATION_COLOR_NEGATIVE
    DeviationType.EQUAL    -> DEVIATION_COLOR_EQUAL
    DeviationType.UAM      -> DEVIATION_COLOR_UAM
    DeviationType.CSF      -> DEVIATION_COLOR_CSF
}

/** Step line with solid area fill and thin stroke, per-deviation-type color */
private fun createDeviationLine(type: DeviationType): LineCartesianLayer.Line {
    val color = deviationColor(type)
    return LineCartesianLayer.Line(
        fill = LineCartesianLayer.LineFill.single(Fill(color)),
        stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 0.dp),
        areaFill = LineCartesianLayer.AreaFill.single(Fill(color.copy(alpha = 0.5f))),
        interpolator = Square
    )
}

/** Processed deviation data split by type for multi-series line rendering */
private data class ProcessedDeviationLines(
    val allX: List<Double>,
    val series: Map<DeviationType, List<Double>>
)

/**
 * Aligned y-ranges for primary (a*) and secondary (b*) axes — zeros share the same fractional
 * position. [primaryLabelFloor], when set, is primary's own real floor (below which its axis was
 * artificially extended purely for zero-alignment) — the start axis' tick placer clamps labels to
 * it so primary never shows a fake tick below a value it can actually reach.
 */
private data class AlignedRanges(val aMin: Double, val aMax: Double, val bMin: Double, val bMax: Double, val primaryLabelFloor: Double? = null)

/**
 * Adjusts two y-ranges so [aPivot]/[bPivot] land at the same fractional height on both axes.
 * Pivots default to 0.0 (true zero) for every series except SENSITIVITY, whose "no change" value
 * is 100% — treating 100 as SENSITIVITY's pivot lets it align with another series' zero exactly
 * like two zero-crossing series would (e.g. SENS at 100% and IOB at 0 both land in the middle).
 *
 * Implemented by shifting both ranges into pivot-relative space, reusing the plain zero-based
 * alignment logic, then shifting the result back.
 */
private fun alignZeros(aMin: Double, aMax: Double, bMin: Double, bMax: Double, aPivot: Double = 0.0, bPivot: Double = 0.0): AlignedRanges? {
    val shifted = alignZerosAtOrigin(aMin - aPivot, aMax - aPivot, bMin - bPivot, bMax - bPivot) ?: return null
    return AlignedRanges(shifted.aMin + aPivot, shifted.aMax + aPivot, shifted.bMin + bPivot, shifted.bMax + bPivot)
}

/**
 * Adjusts two y-ranges so y=0 lands at the same fractional height on both axes.
 *
 * Strategy: whenever either series crosses (or touches) zero, symmetrize each
 * axis around zero independently — both zeros then sit at the mid of their
 * own axis, so they share the same pixel row regardless of the individual
 * scales. When both series are strictly positive or strictly negative, zero
 * is pinned to the edge and no stretching is needed.
 *
 * Why this over a "shared zero fraction p" approach: an extension-only
 * alignment breaks down when one series is all-negative and the other is
 * bipolar (target p → 1 requires clipping positive data). Symmetrize-on-cross
 * is simpler, always works without clipping, and matches how the old
 * OverviewFragment aligned bipolar series (see GraphData.kt lines 150/151).
 */
private fun alignZerosAtOrigin(aMin: Double, aMax: Double, bMin: Double, bMax: Double): AlignedRanges? {
    // Treat touching zero (min=0 or max=0) as crossing — the zero line is in-range either way.
    val aCrosses = aMin <= 0 && aMax >= 0
    val bCrosses = bMin <= 0 && bMax >= 0
    return when {
        // At least one series straddles zero → symmetrize both around zero so zeros align at mid.
        // Degenerate case: if one side is all zeros (half=0) we still symmetrize it using the
        // other side's half (or 1.0 if both are all zero) so Vico gets a non-empty range and the
        // zero line still lands at mid.
        aCrosses || bCrosses -> {
            val aHalfRaw = maxOf(-aMin, aMax)
            val bHalfRaw = maxOf(-bMin, bMax)
            if (aHalfRaw <= 0 && bHalfRaw <= 0) return null // both completely flat at zero
            val fallback = maxOf(aHalfRaw, bHalfRaw, 1.0)
            val aHalf = if (aHalfRaw > 0) aHalfRaw else fallback
            val bHalf = if (bHalfRaw > 0) bHalfRaw else fallback
            AlignedRanges(-aHalf, aHalf, -bHalf, bHalf)
        }
        // Both strictly positive → pin zeros to bottom (use 0 as shared floor).
        aMin > 0 && bMin > 0 -> AlignedRanges(0.0, aMax, 0.0, bMax)
        // Both strictly negative → pin zeros to top (use 0 as shared ceiling).
        aMax < 0 && bMax < 0 -> AlignedRanges(aMin, 0.0, bMin, 0.0)
        // One all-positive, one all-negative (neither touches zero) → no useful shared alignment.
        else                 -> null
    }
}

/**
 * Builds an axis range that keeps [dataMin, dataMax] fully in view while placing zero at
 * [targetFraction] of the axis height. Anchors at dataMin first, stretching only the max to hit
 * the fraction; if that would still clip dataMax, anchors at dataMax instead and stretches the
 * min further negative. Either branch guarantees no clipping, for any fraction in range.
 */
private fun fractionAlignedRange(dataMin: Double, dataMax: Double, targetFraction: Double): Pair<Double, Double> {
    val fraction = targetFraction.coerceIn(0.0, 0.9)
    if (fraction <= 0.0) return dataMin.coerceAtMost(0.0) to dataMax
    val candidateMax = dataMin * (fraction - 1.0) / fraction
    return if (candidateMax >= dataMax) dataMin to candidateMax
    else (-fraction / (1.0 - fraction) * dataMax) to dataMax
}

/**
 * Same construction as [fractionAlignedRange], but whichever bound ends up stretched to hit
 * [targetFraction] is rounded to a nice value too — used for the primary axis, which must stay
 * nice-scaled even when its range is widened to align with a secondary axis' zero.
 */
private fun fractionAlignedNiceRange(niceMin: Double, niceMax: Double, targetFraction: Double): Pair<Double, Double> {
    val fraction = targetFraction.coerceIn(0.0, 0.9)
    if (fraction <= 0.0) return niceMin to niceMax
    val candidateMax = niceMin * (fraction - 1.0) / fraction
    return if (candidateMax >= niceMax) niceMin to niceUp(candidateMax)
    else niceNegativeSliver(-fraction / (1.0 - fraction) * niceMax) to niceMax
}


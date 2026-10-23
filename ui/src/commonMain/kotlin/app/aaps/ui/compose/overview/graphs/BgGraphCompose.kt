package app.aaps.ui.compose.overview.graphs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.ui.MarkerTier
import app.aaps.core.graph.vico.Square
import app.aaps.core.interfaces.overview.graph.ActivityGraphData
import app.aaps.core.interfaces.overview.graph.BasalGraphData
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgType
import app.aaps.core.interfaces.overview.graph.EpsGraphPoint
import app.aaps.core.interfaces.overview.graph.SeriesType
import app.aaps.core.interfaces.overview.graph.TargetLineData
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.icons.IcBgCheck
import app.aaps.core.ui.compose.icons.IcProfile
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.VicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import kotlin.concurrent.Volatile

/** Series identifiers */
/** Basal on BG graph — deprecated, now shown as flipped overlay on IOB graph. Set to true to restore. */
@Deprecated("Basal moved to IOB graph as flipped overlay")
private const val showBasalOnBgGraph = false

private const val SERIES_REGULAR = "regular"
private const val SERIES_BUCKETED = "bucketed"
private const val SERIES_PRED_IOB = "pred_iob"
private const val SERIES_PRED_COB = "pred_cob"
private const val SERIES_PRED_ACOB = "pred_acob"
private const val SERIES_PRED_UAM = "pred_uam"
private const val SERIES_PRED_ZT = "pred_zt"

/** All prediction series identifiers */
private val PREDICTION_SERIES = listOf(SERIES_PRED_IOB, SERIES_PRED_COB, SERIES_PRED_ACOB, SERIES_PRED_UAM, SERIES_PRED_ZT)

/**
 * From the centre of a reading to the tip of the marker that points at it: the 6 dp dot's radius
 * and 1 dp of air, the same rule as the watch. Both halves of a meal keep this distance, so the
 * reading stays visible as the seam between them. The gap is drawn into the marker shapes, see
 * [createMarkerLine], not added to the marker's y: a dp is steady, a share of the axis is not.
 */
private val MARKER_TIP_GAP = 4.dp

/** Height of a marker that has no reading near it, as a share of the Y axis above its floor. */
private const val MARKER_FALLBACK_FRACTION = 0.04

/** Size of the icon drawn at a measured glucose value (calibration, BG check). */
private val MARKER_SIZE_CALIBRATION = 14.dp

/**
 * Y data for an empty marker slot. Far below any axis floor, so Vico clips it away — the treatment
 * block always emits every series, so the line list never has to be matched to the data.
 */
private val MARKER_EMPTY_Y = listOf(-10_000.0, -10_000.0)

/**
 * CartesianChartModelProducer.update() skips notifying receivers (and thus skips recomputing axis
 * ranges) when a transaction's partials AND extraStore are both unchanged from the last one. Since
 * scrolling/zooming re-submits identical series data (only the visible window changed), stashing
 * the visible window here forces the extraStore to differ, so Vico actually reprocesses the
 * transaction instead of silently dropping it. Same mechanism as SecondaryGraphCompose.kt.
 */
private val BG_VISIBLE_RANGE_KEY = ExtraStore.Key<Pair<Long?, Long?>>()

/**
 * A [CartesianLayerRangeProvider] backed by plain mutable fields instead of an immutable value
 * object. [CartesianLayerRangeProvider.fixed] returns a NEW instance whenever bounds change,
 * which forces Vico to rebuild the [com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer]
 * and mint a new CartesianChart id — that in turn triggers Vico's internal chart re-registration,
 * which was found to destabilize BG's live pinch-zoom/scroll gesture handling (BG is the only
 * graph with an interactive chart). This object's IDENTITY never changes across scroll/zoom; only
 * its field values do, read fresh whenever Vico next processes a modelProducer transaction — so
 * the chart itself is never rebuilt mid-gesture. Private to this file: BG is the only graph that
 * needs this (secondary graphs are non-interactive, so `.fixed(...)` churn never affected them).
 */
private class MutableYRangeProvider(
    @Volatile var maxX: Double,
    @Volatile var minY: Double,
    @Volatile var maxY: Double
) : CartesianLayerRangeProvider {
    override fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore) = 0.0
    override fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore) = this.maxX
    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) = this.minY
    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore) = this.maxY
}

/**
 * BG Graph using Vico — dual-layer chart.
 *
 * Layer 0 (start axis): BG readings — regular (outlined circles) + bucketed (filled, range-colored)
 * Layer 1 (end axis, hidden): Basal — profile (dashed step) + actual delivered (solid step + area fill)
 * Layer 5 (start axis): treatment markers — a manual bolus above the reading pointing down at it,
 * carbs below it pointing up, and a measured value (calibration, BG check) sits on its own value. See
 * [treatmentMarkers] for the placement rules.
 *
 * Basal Y-axis is scaled so maxBasal occupies [BASAL_HEIGHT_FRACTION] of the chart height (maxY = maxBasal / BASAL_HEIGHT_FRACTION).
 *
 * The insulin activity overlay has no axis of its own: it is mapped into the BG axis' units with
 * zero at the low mark (see [activityOverlayScale]), and never changes the BG scale.
 *
 * Scroll/Zoom:
 * - Accepts external scroll/zoom states for synchronization with secondary graphs
 * - This is the primary interactive graph - user controls scroll/zoom here
 */
@Composable
fun BgGraphCompose(
    viewModel: GraphViewModel,
    bgOverlays: List<SeriesType>,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState,
    derivedTimeRange: Pair<Long, Long>?,
    nowTimestamp: Long,
    visibleTimeRange: Pair<Long, Long>? = null,
    /** Press and hold to see the values at a reading. Null turns it off. */
    inspect: GraphInspectState? = null,
    modifier: Modifier = Modifier
) {
    val dateUtil = LocalDateUtil.current
    // Collect flows independently - each triggers recomposition only when it changes
    val bgReadings by viewModel.bgReadingsFlow.collectAsStateWithLifecycle()
    val bucketedData by viewModel.bucketedDataFlow.collectAsStateWithLifecycle()
    val showPredictions = SeriesType.PREDICTIONS in bgOverlays
    val rawPredictions by viewModel.predictionsFlow.collectAsStateWithLifecycle()
    val predictions = if (showPredictions) rawPredictions else emptyList()
    val rawBasalData by viewModel.basalGraphFlow.collectAsStateWithLifecycle()
    val targetData by viewModel.targetLineFlow.collectAsStateWithLifecycle()
    val treatmentData by viewModel.treatmentGraphFlow.collectAsStateWithLifecycle()

    // Basal on BG graph is deprecated — now shown as flipped overlay on IOB graph instead.
    // Keep the layer structure intact (dummy data) to avoid chart restructuring.
    @Suppress("DEPRECATION")
    val basalData = if (showBasalOnBgGraph) rawBasalData else BasalGraphData(emptyList(), emptyList(), 0.0)
    val epsPoints by viewModel.epsGraphFlow.collectAsStateWithLifecycle()
    val showActivity = SeriesType.ACTIVITY in bgOverlays
    val activityData by viewModel.activityGraphFlow.collectAsStateWithLifecycle()
    val chartConfig by viewModel.chartConfigFlow.collectAsStateWithLifecycle()

    // Use derived time range or fall back to default (last GRAPH_TIME_RANGE_HOURS hours)
    val (minTimestamp, maxTimestamp) = derivedTimeRange ?: run {
        val now = dateUtil.now()
        val dayAgo = now - Constants.GRAPH_TIME_RANGE_HOURS * 60 * 60 * 1000L
        dayAgo to now
    }

    // Single model producer shared by all layers
    val modelProducer = remember { CartesianChartModelProducer() }

    // Series registry - tracks current data for each series
    val seriesRegistry = remember { mutableStateMapOf<String, List<BgDataPoint>>() }

    // Colors from theme (stable - won't change)
    val regularColor = AapsTheme.generalColors.originalBgValue
    val lowColor = AapsTheme.generalColors.bgLow
    val inRangeColor = AapsTheme.generalColors.bgInRange
    val highColor = AapsTheme.generalColors.bgHigh
    val basalColor = AapsTheme.elementColors.tempBasal
    val targetLineColor = AapsTheme.elementColors.tempTarget
    val activityColor = AapsTheme.elementColors.activity
    val insulinColor = AapsTheme.elementColors.insulin
    val carbsColor = AapsTheme.elementColors.carbs
    val bgCheckColor = AapsTheme.elementColors.bgCheck

    // Prediction colors
    val iobPredColor = AapsTheme.generalColors.iobPrediction
    val cobPredColor = AapsTheme.generalColors.cobPrediction
    val aCobPredColor = AapsTheme.generalColors.aCobPrediction
    val uamPredColor = AapsTheme.generalColors.uamPrediction
    val ztPredColor = AapsTheme.generalColors.ztPrediction

    // Calculate x-axis range (must match COB graph for alignment)
    val maxX = remember(minTimestamp, maxTimestamp) {
        timestampToX(maxTimestamp, minTimestamp)
    }

    // Stable range-provider instance for the start (BG) axis — created once, mutated in place by
    // the LaunchedEffect below rather than recreated (see MutableYRangeProvider).
    val startAxisRangeProvider = remember {
        val initialScale = niceScale(chartConfig.lowMark, chartConfig.highMark)
        MutableYRangeProvider(maxX = maxX, minY = initialScale.min, maxY = initialScale.max)
    }

    // Track which series are currently included (for matching LineProvider)
    val activeSeriesState = remember { mutableStateOf(listOf<String>()) }

    // Stable time range - only changes when timestamps change by more than 1 minute
    val stableTimeRange = remember(minTimestamp / 60000, maxTimestamp / 60000) {
        minTimestamp to maxTimestamp
    }

    // Function to rebuild chart from registry
    suspend fun rebuildChart(
        currentBasalData: BasalGraphData,
        currentTargetData: TargetLineData,
        currentEpsPoints: List<EpsGraphPoint>,
        currentActivityData: ActivityGraphData,
        currentActivityScale: ActivityOverlayScale,
        currentMinBgY: Double,
        currentMaxBgY: Double,
        currentMarkers: TreatmentMarkers,
        currentVisibleTimeRange: Pair<Long, Long>?
    ) {
        val regularPoints = seriesRegistry[SERIES_REGULAR] ?: emptyList()
        val bucketedPoints = seriesRegistry[SERIES_BUCKETED] ?: emptyList()

        // Note: do NOT early-return when there are no BG points. With a clean DB the chart must
        // still build its frame (axes, now-line, in-range belt) via the normalizer + dummy layers,
        // matching the COB graph. The per-layer logic below already handles empty series.

        modelProducer.runTransaction {
            // Block 1 → BG layer (layer 0, start axis)
            lineModel {
                val activeSeries = mutableListOf<String>()

                if (regularPoints.isNotEmpty()) {
                    val dataPoints = regularPoints
                        .map { timestampToX(it.timestamp, minTimestamp) to it.value }
                        .sortedBy { it.first }
                    series(x = dataPoints.map { it.first }, y = dataPoints.map { it.second })
                    activeSeries.add(SERIES_REGULAR)
                }

                if (bucketedPoints.isNotEmpty()) {
                    val dataPoints = bucketedPoints
                        .map { timestampToX(it.timestamp, minTimestamp) to it.value }
                        .sortedBy { it.first }
                    series(x = dataPoints.map { it.first }, y = dataPoints.map { it.second })
                    activeSeries.add(SERIES_BUCKETED)
                }

                // Prediction series - each type as a separate line
                for (predSeries in PREDICTION_SERIES) {
                    val predPoints = seriesRegistry[predSeries]
                    if (!predPoints.isNullOrEmpty()) {
                        val dataPoints = predPoints
                            .map { timestampToX(it.timestamp, minTimestamp) to it.value }
                            .sortedBy { it.first }
                        series(x = dataPoints.map { it.first }, y = dataPoints.map { it.second })
                        activeSeries.add(predSeries)
                    }
                }

                // Normalizer series
                series(x = normalizerX(maxX), y = NORMALIZER_Y)

                activeSeriesState.value = activeSeries.toList()
            }

            // Block 2 → Basal layer (layer 1, end axis)
            lineModel {
                if (currentBasalData.profileBasal.size >= 2) {
                    val pts = currentBasalData.profileBasal
                        .map { timestampToX(it.timestamp, minTimestamp) to it.value }
                        .sortedBy { it.first }
                    series(x = pts.map { it.first }, y = pts.map { it.second })
                } else {
                    // Dummy series - invisible at y=0
                    series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                }

                if (currentBasalData.actualBasal.size >= 2) {
                    val pts = currentBasalData.actualBasal
                        .map { timestampToX(it.timestamp, minTimestamp) to it.value }
                        .sortedBy { it.first }
                    series(x = pts.map { it.first }, y = pts.map { it.second })
                } else {
                    // Dummy series - invisible at y=0
                    series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                }
            }

            // Block 3 → Target line layer (layer 2, start axis)
            lineModel {
                if (currentTargetData.targets.size >= 2) {
                    val pts = currentTargetData.targets
                        .map { timestampToX(it.timestamp, minTimestamp) to it.value }
                        .sortedBy { it.first }
                    series(x = pts.map { it.first }, y = pts.map { it.second })
                } else {
                    // Dummy series - invisible at y=0
                    series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                }
            }

            // Block 4 → EPS layer (layer 3, start axis — Y based on profile %, scaled into BG coordinate space)
            // Same principle as legacy (originalPercentage/100 * baseline); baseline = 75% of the BG axis
            // height. Anchored at currentMinBgY (not 0) — since the axis floor is no longer fixed at 0,
            // a 0%-profile point must sit at the axis' actual bottom, not fall below it and disappear.
            lineModel {
                if (currentEpsPoints.isNotEmpty()) {
                    val epsBaseline = (currentMaxBgY - currentMinBgY) * 0.75
                    val pts = currentEpsPoints
                        .map { eps -> timestampToX(eps.timestamp, minTimestamp) to (currentMinBgY + eps.originalPercentage / 100.0 * epsBaseline) }
                        .sortedBy { it.first }
                    series(x = pts.map { it.first }, y = pts.map { it.second })
                } else {
                    // Dummy series - invisible at y=0
                    series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                }
            }

            // Block 5 → Activity layer (layer 4, start axis — Y-values mapped into BG coordinate space)
            // Zero activity is drawn at the low mark, the bottom edge of the green in-range belt:
            // a natural horizontal reference, with the room between it and the BG axis floor left
            // for the rare negative excursion. Scale comes from the visible window only — see
            // activityOverlayScale and the LaunchedEffect below.
            lineModel {
                if (!showActivity || currentActivityScale.scale <= 0.0 || currentActivityData.activity.size < 2) {
                    // Activity disabled or no data — emit dummy series (history + prediction)
                    series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                    series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                    return@lineModel
                }

                val pts = currentActivityData.activity
                    .map { timestampToX(it.timestamp, minTimestamp) to currentActivityScale.map(it.value) }
                    .sortedBy { it.first }
                series(x = pts.map { it.first }, y = pts.map { it.second })

                if (currentActivityData.activityPrediction.size >= 2) {
                    val predPts = currentActivityData.activityPrediction
                        .map { timestampToX(it.timestamp, minTimestamp) to currentActivityScale.map(it.value) }
                        .sortedBy { it.first }
                    series(x = predPts.map { it.first }, y = predPts.map { it.second })
                } else {
                    series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                }
            }

            // Block 6 → Treatment markers (layer 5, start axis — drawn at the glucose value of the
            // moment they happened, see TreatmentMarkers.kt). Insulin points down at the reading from
            // above and carbs point up at it from below, so a meal entered with its bolus is one marker
            // with both tips on the curve; a measured value sits on its own value.
            // Drawn last on purpose: a carbs marker reaches into the curve, and under the BG layer it
            // was covered by the reading dots and the profile icon. It does cover the one reading
            // it sits on.
            // Fixed series order, always all 7: insulin small/medium/large, carbs small/medium/large,
            // measured glucose. An empty slot gets a clipped dummy, so the line list below is a
            // plain fixed list instead of something that has to be matched to the data.
            lineModel {
                fun markerSeries(markers: List<TreatmentMarker>) {
                    if (markers.isEmpty()) series(x = listOf(0.0, 1.0), y = MARKER_EMPTY_Y)
                    else series(x = markers.map { it.x }, y = markers.map { it.y })
                }
                for (tier in MarkerTier.entries) markerSeries(currentMarkers.insulin.filter { it.tier == tier })
                for (tier in MarkerTier.entries) markerSeries(currentMarkers.carbs.filter { it.tier == tier })
                val measured = currentMarkers.calibrations
                if (measured.isEmpty()) series(x = listOf(0.0, 1.0), y = MARKER_EMPTY_Y)
                else series(x = measured.map { it.first }, y = measured.map { it.second })
            }

            // Forces Vico to reprocess this transaction even when the series data above is
            // identical to last time (see BG_VISIBLE_RANGE_KEY doc) — otherwise scrolling/zooming
            // would re-submit the same partials and get silently skipped, never picking up the
            // updated startAxisRangeProvider.
            extras { it[BG_VISIBLE_RANGE_KEY] = currentVisibleTimeRange?.first to currentVisibleTimeRange?.second }
        }
    }

    // Split predictions by type into registry
    val predictionsByType = remember(predictions) {
        mapOf(
            SERIES_PRED_IOB to predictions.filter { it.type == BgType.IOB_PREDICTION },
            SERIES_PRED_COB to predictions.filter { it.type == BgType.COB_PREDICTION },
            SERIES_PRED_ACOB to predictions.filter { it.type == BgType.A_COB_PREDICTION },
            SERIES_PRED_UAM to predictions.filter { it.type == BgType.UAM_PREDICTION },
            SERIES_PRED_ZT to predictions.filter { it.type == BgType.ZT_PREDICTION }
        )
    }

    // Single LaunchedEffect for all data - ensures atomic updates
    LaunchedEffect(
        bgReadings, bucketedData, predictionsByType, basalData, targetData, epsPoints, activityData, showActivity, chartConfig, stableTimeRange,
        visibleTimeRange, treatmentData
    ) {
        seriesRegistry[SERIES_REGULAR] = bgReadings
        seriesRegistry[SERIES_BUCKETED] = bucketedData
        for ((key, points) in predictionsByType) {
            seriesRegistry[key] = points
        }
        // maxBgY/minBgY clamped against highMark/lowMark (same as legacy GraphData.maxY logic) —
        // used only for the EPS baseline, NOT the axis range itself (see below for that — windowed,
        // unlike these full-range values).
        val allBgValues = (bgReadings + bucketedData).map { it.value }
        val maxBgY = if (allBgValues.isNotEmpty()) maxOf(allBgValues.max(), chartConfig.highMark) else chartConfig.highMark
        val minBgY = if (allBgValues.isNotEmpty()) minOf(allBgValues.min(), chartConfig.lowMark) else chartConfig.lowMark

        // Windowed axis min/max: BG values within the visible scroll/zoom window (not the full
        // loaded range), floored/ceiled at chartConfig.lowMark/highMark (the "Low mark"/"High mark"
        // target-range preferences) so the axis never shrinks past the configured target range —
        // but also never stays locked at a fixed 0 floor when real data sits well above it, which
        // used to leave a large empty band under the curve. niceScale(...) rounds both bounds out
        // a little, to a grid of about a tenth of the range, instead of using the raw data values;
        // the labels are placed apart from that (NiceVerticalAxisItemPlacer).
        // Mutate the stable provider in place (see MutableYRangeProvider) — Vico picks
        // up the new values when it processes the transaction submitted below, without ever
        // recreating BG's chart object.
        // Includes predictions (when shown) — otherwise scrolling into a region with only future
        // prediction data (no real BG readings) makes the windowed set empty, falling back to the
        // full unwindowed history's max instead of the actually-visible prediction values.
        fun inWindow(timestamp: Long) = visibleTimeRange == null || timestamp in visibleTimeRange.first..visibleTimeRange.second
        // A measured value (calibration, BG check) is drawn at its own glucose value and can sit
        // well off the curve, so it widens the axis like a reading — otherwise it would be clamped
        // to the edge and lie about what the meter said.
        val measuredPoints = measuredGlucosePoints(treatmentData.calibrations, treatmentData.therapyEvents)
        val allBgAndPredictionValues = (bgReadings + bucketedData + predictions).map { it.value } + measuredPoints.map { it.second }
        val windowedValues = (bgReadings + bucketedData + predictions).filter { inWindow(it.timestamp) }.map { it.value } +
            measuredPoints.filter { inWindow(it.first) }.map { it.second }
        val windowedOrFull = windowedValues.ifEmpty { allBgAndPredictionValues }
        val dataMax = maxOf(windowedOrFull.maxOrNull() ?: chartConfig.highMark, chartConfig.highMark)
        val dataMin = minOf(windowedOrFull.minOrNull() ?: chartConfig.lowMark, chartConfig.lowMark)
        val niceBgScale = niceScale(dataMin, dataMax)
        startAxisRangeProvider.maxX = maxX
        startAxisRangeProvider.minY = niceBgScale.min
        startAxisRangeProvider.maxY = niceBgScale.max

        // Activity overlay: zero anchored on the low mark (bottom edge of the green in-range belt),
        // scaled from the visible window like every other curve. The BG axis above is never changed
        // to fit it — the activity is fitted into the room the BG axis already leaves on each side
        // of the low mark. Prediction points count too, same reason as for the BG values above.
        val windowedActivity = (activityData.activity + activityData.activityPrediction)
            .filter { inWindow(it.timestamp) }
            .map { it.value }
            .ifEmpty { (activityData.activity + activityData.activityPrediction).map { it.value } }
        val activityScale = activityOverlayScale(
            activityMin = windowedActivity.minOrNull() ?: 0.0,
            activityMax = windowedActivity.maxOrNull() ?: 0.0,
            zeroLevel = chartConfig.lowMark,
            roomAbove = niceBgScale.max - chartConfig.lowMark,
            roomBelow = chartConfig.lowMark - niceBgScale.min
        )

        // Treatment markers: heights come from the curve itself, so they are rebuilt with the axis
        val axisSpan = niceBgScale.max - niceBgScale.min
        val markers = treatmentMarkers(
            boluses = treatmentData.boluses,
            carbs = treatmentData.carbs,
            calibrations = treatmentData.calibrations,
            therapyEvents = treatmentData.therapyEvents,
            readings = bucketedData.ifEmpty { bgReadings },
            minTimestamp = minTimestamp,
            minX = 0.0,
            maxX = maxX,
            minY = niceBgScale.min,
            maxY = niceBgScale.max,
            // The markers sit on the reading itself; the space to it is in the shapes, see MARKER_TIP_GAP
            gap = 0.0,
            fallbackY = niceBgScale.min + axisSpan * MARKER_FALLBACK_FRACTION
        )

        rebuildChart(basalData, targetData, epsPoints, activityData, activityScale, minBgY, maxBgY, markers, visibleTimeRange)
    }

    // Build lookup map for BUCKETED points: x-value -> BgDataPoint (for PointProvider)
    val bucketedLookup = remember(bucketedData, minTimestamp) {
        bucketedData.associateBy { timestampToX(it.timestamp, minTimestamp) }
    }

    val bucketedPointProvider = remember(bucketedLookup, lowColor, inRangeColor, highColor) {
        BucketedPointProvider(bucketedLookup, lowColor, inRangeColor, highColor)
    }

    // Time formatter and axis configuration
    val timeFormatter = rememberTimeFormatter(minTimestamp)
    val bottomAxisItemPlacer = rememberBottomAxisItemPlacer(minTimestamp)

    // =========================================================================
    // BG layer lines (layer 0)
    // =========================================================================

    val regularLine = remember(regularColor) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
            areaFill = null,
            pointProvider = LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    component = ShapeComponent(
                        fill = Fill(Color.Transparent),
                        shape = CircleShape,
                        strokeFill = Fill(regularColor.copy(alpha = 0.3f)),
                        strokeThickness = 1.dp
                    ),
                    size = 6.dp
                )
            )
        )
    }

    val bucketedLine = remember(bucketedPointProvider) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
            areaFill = null,
            pointProvider = bucketedPointProvider
        )
    }

    val normalizerLine = remember { createNormalizerLine() }

    // Prediction lines - transparent connecting line with small filled circle points
    val iobPredLine = remember(iobPredColor) { createPredictionLine(iobPredColor) }
    val cobPredLine = remember(cobPredColor) { createPredictionLine(cobPredColor) }
    val aCobPredLine = remember(aCobPredColor) { createPredictionLine(aCobPredColor) }
    val uamPredLine = remember(uamPredColor) { createPredictionLine(uamPredColor) }
    val ztPredLine = remember(ztPredColor) { createPredictionLine(ztPredColor) }

    val activeSeries by activeSeriesState
    val bgLines = remember(activeSeries, regularLine, bucketedLine, iobPredLine, cobPredLine, aCobPredLine, uamPredLine, ztPredLine, normalizerLine) {
        buildList {
            if (SERIES_REGULAR in activeSeries) add(regularLine)
            if (SERIES_BUCKETED in activeSeries) add(bucketedLine)
            if (SERIES_PRED_IOB in activeSeries) add(iobPredLine)
            if (SERIES_PRED_COB in activeSeries) add(cobPredLine)
            if (SERIES_PRED_ACOB in activeSeries) add(aCobPredLine)
            if (SERIES_PRED_UAM in activeSeries) add(uamPredLine)
            if (SERIES_PRED_ZT in activeSeries) add(ztPredLine)
            add(normalizerLine)
        }
    }

    // =========================================================================
    // Basal layer lines (layer 1) — always 2 lines: [profileLine, actualLine]
    // =========================================================================

    // Profile basal: dashed line, no fill, step connector
    val profileBasalLine = remember(basalColor) {
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

    // Actual delivered basal: solid line with semi-transparent area fill, step connector
    val actualBasalLine = remember(basalColor) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(basalColor)),
            stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 1.dp),
            areaFill = LineCartesianLayer.AreaFill.single(Fill(basalColor.copy(alpha = 0.3f))),
            interpolator = Square
        )
    }

    val basalLines = remember(profileBasalLine, actualBasalLine) {
        listOf(profileBasalLine, actualBasalLine)
    }

    // =========================================================================
    // Target line (layer 2) — single line on start (BG) axis
    // =========================================================================

    val targetLine = remember(targetLineColor) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(targetLineColor)),
            stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 1.dp),
            areaFill = null,
            interpolator = Square
        )
    }

    val targetLines = remember(targetLine) { listOf(targetLine) }

    // =========================================================================
    // EPS layer lines (layer 3) — profile icon points
    // =========================================================================

    val profileSwitchColor = AapsTheme.elementColors.profileSwitch
    val profilePainter = rememberVectorPainter(IcProfile)

    val epsLine = remember(profileSwitchColor, profilePainter) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
            areaFill = null,
            pointProvider = LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    component = PainterComponent(profilePainter, tint = profileSwitchColor),
                    size = 16.dp
                )
            )
        )
    }

    val epsLines = remember(epsLine) { listOf(epsLine) }

    // =========================================================================
    // Activity layer lines (layer 4) — solid historical + dashed prediction
    // =========================================================================

    val activityHistLine = remember(activityColor) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(activityColor)),
            stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 1.5.dp),
            areaFill = null
        )
    }

    val activityPredLine = remember(activityColor) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(activityColor)),
            stroke = LineCartesianLayer.LineStroke.Dashed(
                thickness = 1.5.dp,
                cap = StrokeCap.Round,
                dashLength = 4.dp,
                gapLength = 4.dp
            ),
            areaFill = null
        )
    }

    val activityLines = remember(activityHistLine, activityPredLine) {
        listOf(activityHistLine, activityPredLine)
    }

    // =========================================================================
    // Treatment marker lines (layer 5) — order must match block 6 in rebuildChart
    // =========================================================================

    val bgCheckPainter = rememberVectorPainter(IcBgCheck)

    val treatmentLines = remember(insulinColor, carbsColor, bgCheckColor, bgCheckPainter) {
        buildList {
            for (tier in MarkerTier.entries) {
                add(createMarkerLine(insulinColor, ::invertedTriangleShape, tier.markerSize))
            }
            for (tier in MarkerTier.entries) {
                add(createMarkerLine(carbsColor, ::triangleBelowShape, tier.markerSize))
            }
            add(
                LineCartesianLayer.Line(
                    fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
                    areaFill = null,
                    pointProvider = LineCartesianLayer.PointProvider.single(
                        LineCartesianLayer.Point(
                            component = PainterComponent(bgCheckPainter, tint = bgCheckColor),
                            size = MARKER_SIZE_CALIBRATION
                        )
                    )
                )
            )
        }
    }

    // Basal Y-axis range: maxBasal / BASAL_HEIGHT_FRACTION so basal occupies that fraction of chart height
    val basalMaxY = remember(basalData.maxBasal) {
        if (basalData.maxBasal > 0.0) basalData.maxBasal / BASAL_HEIGHT_FRACTION else 1.0
    }

    // =========================================================================
    // Decorations
    // =========================================================================

    val nowLineColor = MaterialTheme.colorScheme.onSurface
    val nowLine = rememberNowLine(minTimestamp, nowTimestamp, nowLineColor)

    // In-range belt — translucent band between lowMark and highMark on the BG axis
    val lowMark = chartConfig.lowMark
    val highMark = chartConfig.highMark
    val inRangeBox = remember(lowMark, highMark, inRangeColor) {
        InRangeBand(low = lowMark, high = highMark, color = inRangeColor.copy(alpha = 0.2f))
    }

    val inspectMarker = remember(inspect) { inspect?.let { SilentInspectMarker } }
    // Always called, so the composable calls do not depend on inspect
    val defaultMarkerController = CartesianMarkerController.rememberShowOnPress()
    val inspectLine = rememberInspectLine(inspect, dotOnReading = true)

    val decorations = remember(inRangeBox, nowLine, inspectLine) { listOfNotNull(inRangeBox, nowLine, inspectLine) }

    // =========================================================================
    // Range providers — hoisted out of rememberCartesianChart so keys are re-evaluated on recomposition
    // =========================================================================

    // startAxisRangeProvider (BG axis) is created once, further up, and mutated in place — see
    // MutableYRangeProvider and the LaunchedEffect above.
    val endAxisRangeProvider = remember(maxX, basalMaxY) {
        CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX, minY = 0.0, maxY = basalMaxY)
    }

    // =========================================================================
    // Chart — multi layer
    // =========================================================================

    CartesianChartHost(
        chart = rememberCartesianChart(
            // Layer 0: BG (start axis, visible)
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(bgLines),
                rangeProvider = startAxisRangeProvider,
                verticalAxisPosition = Axis.Position.Vertical.Start
            ),
            // Layer 1: Basal (end axis, hidden — no endAxis parameter)
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(basalLines),
                rangeProvider = endAxisRangeProvider,
                verticalAxisPosition = Axis.Position.Vertical.End
            ),
            // Layer 2: Target line (start axis — shares BG Y-axis range)
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(targetLines),
                rangeProvider = startAxisRangeProvider,
                verticalAxisPosition = Axis.Position.Vertical.Start
            ),
            // Layer 3: EPS (start axis — Y based on profile %, same range as BG layer)
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(epsLines),
                rangeProvider = startAxisRangeProvider,
                verticalAxisPosition = Axis.Position.Vertical.Start
            ),
            // Layer 4: Activity (start axis — shares BG Y-axis range, values normalized in rebuildChart)
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(activityLines),
                rangeProvider = startAxisRangeProvider,
                verticalAxisPosition = Axis.Position.Vertical.Start
            ),
            // Layer 5: Treatment markers (start axis — drawn last, see block 6)
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(treatmentLines),
                rangeProvider = startAxisRangeProvider,
                verticalAxisPosition = Axis.Position.Vertical.Start
            ),
            startAxis = VerticalAxis.rememberStart(
                itemPlacer = remember { NiceVerticalAxisItemPlacer() },
                label = rememberTextComponent(
                    style = TextStyle(color = MaterialTheme.colorScheme.onSurface),
                    minWidth = TextComponent.MinWidth.fixed(30.dp)
                ),
                guideline = LineComponent(fill = Fill(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = timeFormatter,
                itemPlacer = bottomAxisItemPlacer,
                label = rememberTextComponent(
                    style = TextStyle(color = MaterialTheme.colorScheme.onSurface)
                ),
                guideline = LineComponent(fill = Fill(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
            ),
            decorations = decorations,
            getXStep = { _, _, _ -> 1.0 },
            marker = inspectMarker,
            markerVisibilityListener = inspect?.visibilityListener,
            markerController = inspect?.controller ?: defaultMarkerController
        ),
        modelProducer = modelProducer,
        modifier = modifier.fillMaxWidth(),
        scrollState = scrollState,
        zoomState = zoomState
    )
}

/**
 * A line that draws no line at all, only a marker in [color] at every point. Used for the
 * treatment markers on the BG graph, one line per size.
 *
 * The point box is [size] grown by [MARKER_TIP_GAP] on both sides, and [shapeOf] gets the gap as
 * a share of that box, so the triangle keeps the proportions of [size] and its tip stops
 * [MARKER_TIP_GAP] away from the reading at the box centre.
 */
private fun createMarkerLine(color: Color, shapeOf: (tipInset: Float) -> Shape, size: Dp): LineCartesianLayer.Line {
    val boxSize = size + MARKER_TIP_GAP * 2
    val shape = shapeOf(MARKER_TIP_GAP / boxSize)
    return LineCartesianLayer.Line(
        fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
        areaFill = null,
        pointProvider = LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.Point(component = ShapeComponent(fill = Fill(color), shape = shape), size = boxSize)
        )
    )
}

/**
 * The in-range belt between [low] and [high] on the BG axis, drawn under the layers. Vico's
 * `HorizontalBox` draws over them, and its translucent green then tinted every reading and marker
 * inside the belt, so a bolus looked one colour in range and another out of it.
 */
private data class InRangeBand(private val low: Double, private val high: Double, private val color: Color) : Decoration {

    override fun drawUnderLayers(context: CartesianDrawingContext) {
        with(context) {
            val yRange = ranges.getYRange(Axis.Position.Vertical.Start)
            if (yRange.length == 0.0) return
            fun canvasY(value: Double): Float =
                (layerBounds.bottom - ((value - yRange.minY) / yRange.length).toFloat() * layerBounds.height)
                    .coerceIn(layerBounds.top, layerBounds.bottom)
            val top = canvasY(high)
            val bottom = canvasY(low)
            if (bottom <= top) return
            with(mutableDrawScope) {
                drawRect(
                    color = this@InRangeBand.color,
                    topLeft = Offset(layerBounds.left, top),
                    size = Size(layerBounds.width, bottom - top)
                )
            }
        }
    }
}

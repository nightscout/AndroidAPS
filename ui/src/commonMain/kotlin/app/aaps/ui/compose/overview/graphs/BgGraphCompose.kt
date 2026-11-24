package app.aaps.ui.compose.overview.graphs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
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
import app.aaps.core.interfaces.overview.graph.TreatmentGraphData
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalDecimalFormatter
import app.aaps.core.ui.compose.LocalProfileUtil
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
import com.patrykandpatrick.vico.compose.common.Position
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import kotlin.concurrent.Volatile

/**
 * Top of the basal on the BG graph, as a share of the low mark. Basal starts at BG 0, and the highest
 * basal in view reaches this share of the low mark, so it stays under the LOW band. The same
 * as in AAPS 3, where the axis also started at 0.
 */
private const val BASAL_TOP_OF_LOW_MARK = 0.6

/** The line from a meter value to the sensor curve at half the strength of its colour. */
private const val METER_LINE_ALPHA = 0.5f

/**
 * Width of the soft bands at the target, the LOW and the HIGH value. A value that means something is a
 * band, wide and faint like a highlighter stroke, and the grid is the only thin line. As thin lines they
 * all looked like the grid, and the graph read as one table of lines. Dashed, next to the dots of the
 * readings and the predictions, the LOW and HIGH lines read as more data.
 */
private val BAND_WIDTH = 4.dp

/** The target band: grey, with no core line, which would look like the grid again. */
private const val TARGET_BAND_ALPHA = 0.2f

/** The LOW and HIGH bands, in the colour of the readings beyond them. Their colour comes from the core, see [RANGE_CORE_ALPHA]. */
private const val RANGE_BAND_ALPHA = 0.2f

/**
 * The thin core line in the middle of the LOW and HIGH bands, like a pen line in a highlighter stroke. Faint
 * yellow turns olive and faint red turns brown on the dark background, so the clean colour and the exact
 * value come from this line, and the band only gives the form.
 */
private const val RANGE_CORE_ALPHA = 0.6f

/** Width of the core line of the LOW and HIGH bands, see [RANGE_CORE_ALPHA]. */
private val RANGE_CORE_WIDTH = 0.5.dp

/** Series identifiers */
private const val SERIES_REGULAR = "regular"
private const val SERIES_BUCKETED = "bucketed"
private const val SERIES_PRED_IOB = "pred_iob"
private const val SERIES_PRED_COB = "pred_cob"
private const val SERIES_PRED_ACOB = "pred_acob"
private const val SERIES_PRED_UAM = "pred_uam"
private const val SERIES_PRED_ZT = "pred_zt"

/** All prediction series identifiers, each with the type of prediction it draws */
private val PREDICTION_SERIES = listOf(
    SERIES_PRED_IOB to BgType.IOB_PREDICTION,
    SERIES_PRED_COB to BgType.COB_PREDICTION,
    SERIES_PRED_ACOB to BgType.A_COB_PREDICTION,
    SERIES_PRED_UAM to BgType.UAM_PREDICTION,
    SERIES_PRED_ZT to BgType.ZT_PREDICTION
)

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
 * The treatment markers of a model, for [TreatmentLabels]. Carried in the model, so the decoration
 * always draws what belongs to the model that is on screen, never a newer one that Vico has not
 * applied yet.
 */
private val TREATMENT_MARKERS_KEY = ExtraStore.Key<TreatmentMarkers>()

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
 * Layer 0 (start axis): Basal — actual delivered (solid step + area fill) + profile (dashed step).
 *   First, so a low reading is drawn over the basal and not under its fill.
 * Layer 1 (start axis): the soft bands at the target, LOW and HIGH. A layer, so the grid passes behind
 *   them, and before BG, so the readings stay on top.
 * Layer 2 (start axis): BG readings — regular (outlined circles) + bucketed (filled, range-colored)
 * Layer 5 (start axis): treatment markers — a manual bolus above the reading pointing down at it,
 * carbs below it pointing up, and a measured value (calibration, BG check) sits on its own value. See
 * [treatmentMarkers] for the placement rules.
 *
 * The BG axis starts at 0. Basal is drawn in BG units from 0, and the highest basal of the visible
 * window ([basalWindowMax]) reaches [BASAL_TOP_OF_LOW_MARK] of the low mark.
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
    val profileUtil = LocalProfileUtil.current
    val predictionHeadroom = remember(profileUtil.units) { profileUtil.fromMgdlToUnits(PREDICTION_AXIS_HEADROOM_MGDL) }
    val basalData by viewModel.basalGraphFlow.collectAsStateWithLifecycle()
    val targetData by viewModel.targetLineFlow.collectAsStateWithLifecycle()
    val treatmentData by viewModel.treatmentGraphFlow.collectAsStateWithLifecycle()

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

    // Colors from theme (stable - won't change)
    val regularColor = AapsTheme.generalColors.originalBgValue
    val lowColor = AapsTheme.generalColors.graphBgLow
    val inRangeColor = AapsTheme.generalColors.graphBgInRange
    val highColor = AapsTheme.generalColors.graphBgHigh
    // Neutral, so it does not read as a second range next to the LOW and HIGH bands
    val targetLineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val activityColor = AapsTheme.elementColors.activity
    val insulinColor = AapsTheme.elementColors.insulin
    // Basal and SMBs are insulin too: the same colour as a bolus. Both are weaker: the basal line runs the
    // whole width of the graph, and the SMBs come every few minutes, so at full colour they would compete
    // with the curve and the bolus. Together they read as one quiet strip of what the loop did.
    val basalColor = insulinColor.copy(alpha = SECOND_TIER_ALPHA)
    val smbColor = insulinColor.copy(alpha = SECOND_TIER_ALPHA)
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
    // submitBgModel rather than recreated (see MutableYRangeProvider).
    val startAxisRangeProvider = remember {
        val initialScale = niceScale(0.0, chartConfig.highMark)
        MutableYRangeProvider(maxX = maxX, minY = initialScale.min, maxY = initialScale.max)
    }

    // Track which series are currently included (for matching LineProvider)
    val activeSeriesState = remember { mutableStateOf(listOf<String>()) }

    // One transaction after the other, and none is cancelled half-way. When the input changes during a
    // transaction, the newest input waits for it, and the inputs in between are skipped. An effect keyed
    // on the input cancelled the running transaction instead, and every scroll or zoom of BG changes the
    // input. When that came just after Vico had stopped the animation of the transaction before, the
    // lines stayed where the animation stopped, part-way between the old and the new scale, while the
    // labels showed the new scale. If the next input was equal to the last finished one, Vico skipped
    // it as a repeat, and the lines stayed like that. Same as in SecondaryGraphCompose.
    val modelInput = BgModelInput(
        bgReadings, bucketedData, predictions, basalData, targetData, epsPoints, activityData, showActivity, chartConfig,
        minTimestamp, maxX, visibleTimeRange, treatmentData, predictionHeadroom
    )
    val latestModelInput by rememberUpdatedState(modelInput)
    LaunchedEffect(modelProducer) {
        snapshotFlow { latestModelInput }.collect { input ->
            submitBgModel(modelProducer, startAxisRangeProvider, activeSeriesState, input)
        }
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
    // BG layer lines (layer 2)
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
                        // Small and faint: detail for a closer look, not noise around the curve
                        strokeFill = Fill(regularColor.copy(alpha = 0.2f)),
                        strokeThickness = 1.dp
                    ),
                    size = 3.dp
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
    // Basal layer lines (layer 0) — always 2 lines: [actualLine, profileLine]
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
            areaFill = LineCartesianLayer.AreaFill.single(Fill(basalColor.copy(alpha = 0.15f))),
            interpolator = Square
        )
    }

    // Profile last, so its dashed line is drawn over the fill of the actual basal
    val basalLines = remember(actualBasalLine, profileBasalLine) {
        listOf(actualBasalLine, profileBasalLine)
    }

    // =========================================================================
    // Reference bands (layer 1) — the target, LOW and HIGH on the start (BG) axis
    // =========================================================================

    // A soft band at each value that means something: the target in grey, LOW and HIGH in the colour of the
    // readings beyond them, as Trio draws its lines, so a band also says which side is bad. LOW and HIGH get a
    // thin core line in that colour. Only the edges of the range: a filled area between LOW and HIGH tinted
    // every reading and marker inside it. A layer and not a decoration: the layers are drawn after the grid,
    // and this one before BG, so the grid passes behind the bands and the readings stay on top of them.
    // The order of the lines is the order of the series in submitBgModel.
    val bandLines = remember(targetLineColor, lowColor, highColor) {
        fun band(color: Color, alpha: Float, width: Dp) = LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(color.copy(alpha = alpha))),
            stroke = LineCartesianLayer.LineStroke.Continuous(thickness = width),
            areaFill = null,
            interpolator = Square
        )
        listOf(
            band(targetLineColor, TARGET_BAND_ALPHA, BAND_WIDTH),
            band(lowColor, RANGE_BAND_ALPHA, BAND_WIDTH),
            band(highColor, RANGE_BAND_ALPHA, BAND_WIDTH),
            band(lowColor, RANGE_CORE_ALPHA, RANGE_CORE_WIDTH),
            band(highColor, RANGE_CORE_ALPHA, RANGE_CORE_WIDTH)
        )
    }

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
                    component = PainterComponent(profilePainter, tint = profileSwitchColor.copy(alpha = SECOND_TIER_ALPHA)),
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
    // Treatment marker lines (layer 5) — order must match block 6 in submitBgModel
    // =========================================================================

    val bgCheckPainter = rememberVectorPainter(IcBgCheck)

    val treatmentLines = remember(insulinColor, smbColor, carbsColor, bgCheckColor, bgCheckPainter) {
        buildList {
            // SMBs stand on the bottom edge, as they stood on the zero line of the IOB graph, in the
            // basal tone so they read as one strip with it
            for (tier in MarkerTier.entries) {
                add(
                    LineCartesianLayer.Line(
                        fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
                        areaFill = null,
                        pointProvider = LineCartesianLayer.PointProvider.single(
                            LineCartesianLayer.Point(component = ShapeComponent(fill = Fill(smbColor), shape = TriangleShape), size = tier.smbSize)
                        )
                    )
                )
            }
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

    // =========================================================================
    // Decorations
    // =========================================================================

    val nowLineColor = MaterialTheme.colorScheme.onSurface
    val nowLine = rememberNowLine(minTimestamp, nowTimestamp, nowLineColor)

    val inspectMarker = remember(inspect) { inspect?.let { SilentInspectMarker } }
    // Always called, so the composable calls do not depend on inspect
    val defaultMarkerController = CartesianMarkerController.rememberShowOnPress()
    val inspectLine = rememberInspectLine(inspect, dotOnReading = true)

    val clockChanges = rememberClockChangeDecoration(minTimestamp, maxTimestamp, bgReadings, showIcon = true)
    // The amount at each treatment marker: insulin above it, carbs below it. A measured glucose value
    // gets its value beside it and a line to the sensor curve
    val decimalFormatter = LocalDecimalFormatter.current
    val treatmentLabels = remember(insulinColor, carbsColor, bgCheckColor, decimalFormatter, profileUtil) {
        TreatmentLabels(insulinColor, carbsColor, bgCheckColor, decimalFormatter) { value ->
            profileUtil.fromMgdlToStringInUnits(profileUtil.convertToMgdl(value, profileUtil.units))
        }
    }
    val decorations = remember(nowLine, clockChanges, treatmentLabels, inspectLine) {
        listOfNotNull(nowLine, clockChanges, treatmentLabels, inspectLine)
    }

    // =========================================================================
    // Range providers — hoisted out of rememberCartesianChart so keys are re-evaluated on recomposition
    // =========================================================================

    // startAxisRangeProvider (BG axis) is created once, further up, and mutated in place — see
    // MutableYRangeProvider and submitBgModel.

    // =========================================================================
    // Chart — multi layer
    // =========================================================================

    CartesianChartHost(
        chart = rememberCartesianChart(
            // Layer 0: Basal (start axis — values mapped into BG units in submitBgModel; first, so under BG)
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(basalLines),
                rangeProvider = startAxisRangeProvider,
                verticalAxisPosition = Axis.Position.Vertical.Start
            ),
            // Layer 1: Target, LOW and HIGH bands (start axis — shares BG Y-axis range; before BG, so under it)
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(bandLines),
                rangeProvider = startAxisRangeProvider,
                verticalAxisPosition = Axis.Position.Vertical.Start
            ),
            // Layer 2: BG (start axis, visible)
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(bgLines),
                rangeProvider = startAxisRangeProvider,
                verticalAxisPosition = Axis.Position.Vertical.Start
            ),
            // Layer 3: EPS (start axis — Y based on profile %, same range as BG layer)
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(epsLines),
                rangeProvider = startAxisRangeProvider,
                verticalAxisPosition = Axis.Position.Vertical.Start
            ),
            // Layer 4: Activity (start axis — shares BG Y-axis range, values normalized in submitBgModel)
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
                // A dotted grid at every label, as on the graphs below, so the labels line up with something
                // (see rememberGridLine). The target, LOW and HIGH are soft bands (BAND_WIDTH), so the grid
                // passes behind them and is not mistaken for them. No axis line and no ticks: the default
                // near-white line was the strongest line on the graph that is not data. The same on every
                // graph, so the hour lines stay aligned (Vico counts the tick and the line in the axis width
                // only when they exist).
                line = null,
                tick = null,
                guideline = rememberGridLine()
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = timeFormatter,
                itemPlacer = bottomAxisItemPlacer,
                label = rememberTextComponent(
                    style = TextStyle(color = MaterialTheme.colorScheme.onSurface)
                ),
                // A quiet solid baseline in the grid colour, not the default near-white line; dotted hour lines
                line = LineComponent(fill = Fill(AapsTheme.generalColors.graphGrid)),
                tick = LineComponent(fill = Fill(AapsTheme.generalColors.graphGrid)),
                guideline = rememberGridLine()
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

/** Everything the chart model of the BG graph is built from. Equal values give the same model. */
private data class BgModelInput(
    val bgReadings: List<BgDataPoint>,
    val bucketedData: List<BgDataPoint>,
    /** Empty when the predictions are not shown */
    val predictions: List<BgDataPoint>,
    val basalData: BasalGraphData,
    val targetData: TargetLineData,
    val epsPoints: List<EpsGraphPoint>,
    val activityData: ActivityGraphData,
    val showActivity: Boolean,
    val chartConfig: ChartConfig,
    val minTimestamp: Long,
    val maxX: Double,
    val visibleTimeRange: Pair<Long, Long>?,
    val treatmentData: TreatmentGraphData,
    /** How far a prediction may widen the top of the axis above the real data, in the units of the graph */
    val predictionHeadroom: Double
)

/**
 * Sets the BG axis for [input] on [startAxisRangeProvider] and sends the chart model built from
 * [input] to [modelProducer]. [activeSeries] gets the series of the BG layer that have data, so the
 * lines of that layer can match them.
 *
 * Outside the composable on purpose: it runs in a collector that lives as long as the graph, so a
 * value read from a composition would be the one of the first composition. Everything comes in
 * through the parameters.
 */
private suspend fun submitBgModel(
    modelProducer: CartesianChartModelProducer,
    startAxisRangeProvider: MutableYRangeProvider,
    activeSeries: MutableState<List<String>>,
    input: BgModelInput
) {
    val minTimestamp = input.minTimestamp
    val chartConfig = input.chartConfig
    val visibleTimeRange = input.visibleTimeRange
    val activityData = input.activityData
    val treatmentData = input.treatmentData

    // The axis starts at 0, because basal is drawn from BG 0 up to under the low mark (see
    // BASAL_TOP_OF_LOW_MARK), as in AAPS 3. The top is windowed: BG values within the visible
    // scroll/zoom window (not the full loaded range), never below chartConfig.highMark (the
    // "High mark" preference). niceScale(...) rounds the top out a little, to a grid of about a
    // tenth of the range, instead of using the raw data value; the labels are placed apart from
    // that (NiceVerticalAxisItemPlacer).
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
    // Real values and predicted ones apart: a prediction may pull the top of the axis only so
    // far above the real data, see bgAxisTop. The window fallback is decided for both together.
    val realPoints = (input.bgReadings + input.bucketedData).map { it.timestamp to it.value } + measuredPoints
    val predictionPoints = input.predictions.map { it.timestamp to it.value }
    val windowHasData = (realPoints + predictionPoints).any { inWindow(it.first) }
    fun List<Pair<Long, Double>>.axisValues() = if (windowHasData) filter { inWindow(it.first) }.map { it.second } else map { it.second }
    val realValues = realPoints.axisValues()
    val predictionValues = predictionPoints.axisValues()
    val dataMax = bgAxisTop(realValues.maxOrNull(), predictionValues.maxOrNull(), chartConfig.highMark, input.predictionHeadroom)
    val niceBgScale = niceScale(0.0, dataMax)
    startAxisRangeProvider.maxX = input.maxX
    startAxisRangeProvider.minY = niceBgScale.min
    startAxisRangeProvider.maxY = niceBgScale.max

    // Basal in BG units: 0 U/h at BG 0, the highest basal in view at BASAL_TOP_OF_LOW_MARK of the low
    // mark. The step series reach the right edge, and the scale follows the visible window.
    val basalProfile = processStepPoints(input.basalData.profileBasal, minTimestamp, 0.0, input.maxX)
    val basalActual = processStepPoints(input.basalData.actualBasal, minTimestamp, 0.0, input.maxX)
    val basalMax = basalWindowMax(
        profile = basalProfile,
        actual = basalActual,
        visibleMinX = visibleTimeRange?.let { timestampToX(it.first, minTimestamp) },
        visibleMaxX = visibleTimeRange?.let { timestampToX(it.second, minTimestamp) },
        maxBasal = input.basalData.maxBasal
    )
    val basalToBg = if (basalMax > 0.0) chartConfig.lowMark * BASAL_TOP_OF_LOW_MARK / basalMax else 0.0

    // Activity overlay: zero anchored on the low mark (the LOW band),
    // scaled from the visible window like every other curve. The BG axis above is never changed
    // to fit it — the activity is fitted into the room the BG axis already leaves on each side
    // of the low mark. Below the low mark only down to the top of the basal, which has the rest.
    // Prediction points count too, same reason as for the BG values above.
    val windowedActivity = (activityData.activity + activityData.activityPrediction)
        .filter { inWindow(it.timestamp) }
        .map { it.value }
        .ifEmpty { (activityData.activity + activityData.activityPrediction).map { it.value } }
    val activityScale = activityOverlayScale(
        activityMin = windowedActivity.minOrNull() ?: 0.0,
        activityMax = windowedActivity.maxOrNull() ?: 0.0,
        zeroLevel = chartConfig.lowMark,
        roomAbove = niceBgScale.max - chartConfig.lowMark,
        roomBelow = chartConfig.lowMark * (1.0 - BASAL_TOP_OF_LOW_MARK)
    )

    // Treatment markers: heights come from the curve itself, so they are rebuilt with the axis
    val axisSpan = niceBgScale.max - niceBgScale.min
    val markers = treatmentMarkers(
        boluses = treatmentData.boluses,
        carbs = treatmentData.carbs,
        calibrations = treatmentData.calibrations,
        therapyEvents = treatmentData.therapyEvents,
        readings = input.bucketedData.ifEmpty { input.bgReadings },
        minTimestamp = minTimestamp,
        minX = 0.0,
        maxX = input.maxX,
        minY = niceBgScale.min,
        maxY = niceBgScale.max,
        // The markers sit on the reading itself; the space to it is in the shapes, see MARKER_TIP_GAP
        gap = 0.0,
        fallbackY = niceBgScale.min + axisSpan * MARKER_FALLBACK_FRACTION
    )

    // Note: do NOT early-return when there are no BG points. With a clean DB the chart must
    // still build its frame (axes, now-line, LOW and HIGH bands) via the normalizer + dummy layers,
    // matching the COB graph. The per-layer logic below already handles empty series.

    modelProducer.runTransaction {
        // Block 1 → Basal layer (layer 0, start axis): actual first, then profile, as the lines
        lineModel {
            for (pts in listOf(basalActual, basalProfile)) {
                val (x, y) = if (pts.size >= 2) pts.map { it.first } to pts.map { it.second * basalToBg } else EMPTY_BASAL
                series(x = x, y = y)
            }
        }

        // Block 2 → Target, LOW and HIGH bands (layer 1, start axis), in the order of bandLines: the target,
        // the LOW and the HIGH band, then the LOW and the HIGH core line over them
        lineModel {
            if (input.targetData.targets.size >= 2) {
                val pts = input.targetData.targets
                    .map { timestampToX(it.timestamp, minTimestamp) to it.value }
                    .sortedBy { it.first }
                series(x = pts.map { it.first }, y = pts.map { it.second })
            } else {
                // Dummy series - invisible at y=0
                series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
            }
            val fullWidth = listOf(0.0, input.maxX)
            for (value in listOf(chartConfig.lowMark, chartConfig.highMark, chartConfig.lowMark, chartConfig.highMark)) {
                series(x = fullWidth, y = listOf(value, value))
            }
        }

        // Block 3 → BG layer (layer 2, start axis)
        lineModel {
            val withData = mutableListOf<String>()

            if (input.bgReadings.isNotEmpty()) {
                val dataPoints = input.bgReadings
                    .map { timestampToX(it.timestamp, minTimestamp) to it.value }
                    .sortedBy { it.first }
                series(x = dataPoints.map { it.first }, y = dataPoints.map { it.second })
                withData.add(SERIES_REGULAR)
            }

            if (input.bucketedData.isNotEmpty()) {
                val dataPoints = input.bucketedData
                    .map { timestampToX(it.timestamp, minTimestamp) to it.value }
                    .sortedBy { it.first }
                series(x = dataPoints.map { it.first }, y = dataPoints.map { it.second })
                withData.add(SERIES_BUCKETED)
            }

            // Prediction series - each type as a separate line
            for ((predSeries, type) in PREDICTION_SERIES) {
                val predPoints = input.predictions.filter { it.type == type }
                if (predPoints.isNotEmpty()) {
                    val dataPoints = predPoints
                        .map { timestampToX(it.timestamp, minTimestamp) to it.value }
                        .sortedBy { it.first }
                    series(x = dataPoints.map { it.first }, y = dataPoints.map { it.second })
                    withData.add(predSeries)
                }
            }

            // Normalizer series
            series(x = normalizerX(input.maxX), y = NORMALIZER_Y)

            activeSeries.value = withData.toList()
        }

        // Block 4 → EPS layer (layer 3, start axis — Y based on profile %, scaled into BG coordinate space)
        // Same principle as legacy (originalPercentage/100 * baseline), but measured on the axis that
        // is drawn, so the icon keeps its height while the graph is scrolled and zoomed, and a strong
        // profile is pinned below the top edge instead of leaving the graph. See epsY.
        lineModel {
            if (input.epsPoints.isNotEmpty()) {
                val pts = input.epsPoints
                    .map { eps -> timestampToX(eps.timestamp, minTimestamp) to epsY(eps.originalPercentage, niceBgScale.min, niceBgScale.max) }
                    .sortedBy { it.first }
                series(x = pts.map { it.first }, y = pts.map { it.second })
            } else {
                // Dummy series - invisible at y=0
                series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
            }
        }

        // Block 5 → Activity layer (layer 4, start axis — Y-values mapped into BG coordinate space)
        // Zero activity is drawn at the low mark, the LOW band:
        // a natural horizontal reference, with the room between it and the BG axis floor left
        // for the rare negative excursion. Scale comes from the visible window only — see
        // activityOverlayScale and activityScale above.
        lineModel {
            if (!input.showActivity || activityScale.scale <= 0.0 || activityData.activity.size < 2) {
                // Activity disabled or no data — emit dummy series (history + prediction)
                series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                series(x = listOf(0.0, 1.0), y = listOf(0.0, 0.0))
                return@lineModel
            }

            val pts = activityData.activity
                .map { timestampToX(it.timestamp, minTimestamp) to activityScale.map(it.value) }
                .sortedBy { it.first }
            series(x = pts.map { it.first }, y = pts.map { it.second })

            if (activityData.activityPrediction.size >= 2) {
                val predPts = activityData.activityPrediction
                    .map { timestampToX(it.timestamp, minTimestamp) to activityScale.map(it.value) }
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
        // Fixed series order, always all 10: SMB small/medium/large, insulin small/medium/large,
        // carbs small/medium/large, measured glucose. An SMB stands on the bottom edge, pointing up. An empty slot gets a clipped dummy, so the line list below is a
        // plain fixed list instead of something that has to be matched to the data.
        lineModel {
            fun markerSeries(markersOfTier: List<TreatmentMarker>) {
                if (markersOfTier.isEmpty()) series(x = listOf(0.0, 1.0), y = MARKER_EMPTY_Y)
                else series(x = markersOfTier.map { it.x }, y = markersOfTier.map { it.y })
            }
            for (tier in MarkerTier.entries) markerSeries(markers.smb.filter { it.tier == tier })
            for (tier in MarkerTier.entries) markerSeries(markers.insulin.filter { it.tier == tier })
            for (tier in MarkerTier.entries) markerSeries(markers.carbs.filter { it.tier == tier })
            val measured = markers.calibrations
            if (measured.isEmpty()) series(x = listOf(0.0, 1.0), y = MARKER_EMPTY_Y)
            else series(x = measured.map { it.x }, y = measured.map { it.y })
        }

        // Forces Vico to reprocess this transaction even when the series data above is
        // identical to last time (see BG_VISIBLE_RANGE_KEY doc) — otherwise scrolling/zooming
        // would re-submit the same partials and get silently skipped, never picking up the
        // updated startAxisRangeProvider.
        extras {
            it[BG_VISIBLE_RANGE_KEY] = visibleTimeRange?.first to visibleTimeRange?.second
            it[TREATMENT_MARKERS_KEY] = markers
        }
    }
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
 * The amount at each treatment marker: the insulin above its marker and the carbs below theirs, so
 * the text sits on the side away from the reading. A measured glucose value (calibration, BG check)
 * gets its value beside its icon, and a dotted line to the sensor curve at the same moment, so the
 * length of the line shows how far the sensor is off.
 *
 * Drawn here and not as a Vico data label: a data label shows the point's y value, and on this graph
 * that is the glucose the marker sits at, not the amount.
 *
 * @param formatGlucose a measured value, given in the user's units, as text with the user's decimals
 */
private class TreatmentLabels(
    insulinColor: Color,
    carbsColor: Color,
    private val measuredColor: Color,
    private val decimalFormatter: DecimalFormatter,
    private val formatGlucose: (Double) -> String
) : Decoration {

    private val insulinText = TextComponent(textStyle = TextStyle(color = insulinColor, fontSize = TREATMENT_LABEL_FONT_SIZE))
    private val carbsText = TextComponent(textStyle = TextStyle(color = carbsColor, fontSize = TREATMENT_LABEL_FONT_SIZE))
    private val measuredText = TextComponent(textStyle = TextStyle(color = measuredColor, fontSize = TREATMENT_LABEL_FONT_SIZE))

    /** The line from each measured value to the sensor curve, under the readings and the icon. */
    override fun drawUnderLayers(context: CartesianDrawingContext) {
        with(context) {
            val yRange = ranges.getYRange(Axis.Position.Vertical.Start)
            if (ranges.xStep == 0.0 || yRange.length == 0.0) return
            val markers = model.extraStore.getOrNull(TREATMENT_MARKERS_KEY) ?: return
            if (markers.calibrations.isEmpty()) return
            val dash = MEASURED_CONNECTOR_DASH.pixels
            val effect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))
            val color = measuredColor.copy(alpha = METER_LINE_ALPHA)
            for (marker in markers.calibrations) {
                val sensorY = marker.sensorY ?: continue
                val x = canvasX(marker.x)
                if (x < layerBounds.left || x > layerBounds.right) continue
                mutableDrawScope.drawLine(
                    color = color,
                    start = Offset(x, canvasY(marker.y)),
                    end = Offset(x, canvasY(sensorY)),
                    strokeWidth = MEASURED_CONNECTOR_WIDTH.pixels,
                    pathEffect = effect
                )
            }
        }
    }

    override fun drawOverLayers(context: CartesianDrawingContext) {
        with(context) {
            val yRange = ranges.getYRange(Axis.Position.Vertical.Start)
            if (ranges.xStep == 0.0 || yRange.length == 0.0) return
            val markers = model.extraStore.getOrNull(TREATMENT_MARKERS_KEY) ?: return
            // Half the marker box, see createMarkerLine. The box is centred on the reading and the shape
            // fills one half of it, so this is the distance from the reading to the far end of the shape
            fun halfBox(tier: MarkerTier): Float = ((tier.markerSize + MARKER_TIP_GAP * 2) / 2).pixels
            val air = AapsSpacing.extraSmall.pixels

            // The label goes on the far side of the marker from the reading. Where that does not fit
            // inside the graph (a bolus near the top, a carb marker near the bottom) it goes beside the
            // marker instead, so it never covers the edit button above the graph or the time labels
            // below it. A label that would cover one already drawn is left out: the larger amounts are
            // drawn first, so the smaller one gives way. Its amount is still in the inspect pill.
            val placed = mutableListOf<Rect>()
            fun fits(box: Rect): Boolean =
                box.top >= layerBounds.top && box.bottom <= layerBounds.bottom &&
                    box.left >= layerBounds.left && box.right <= layerBounds.right &&
                    placed.none { it.overlaps(box) }
            fun drawLabel(label: TextComponent, text: String, marker: TreatmentMarker, above: Boolean) {
                val x = canvasX(marker.x)
                if (x < layerBounds.left || x > layerBounds.right) return
                val width = label.getWidth(context, text)
                val height = label.getHeight(context, text)
                val reading = canvasY(marker.y)
                val half = halfBox(marker.tier)
                // On the far side of the marker
                val edgeY = if (above) reading - half - air else reading + half + air
                val endTop = if (above) edgeY - height else edgeY
                val endBox = Rect(x - width / 2, endTop, x + width / 2, endTop + height)
                if (fits(endBox)) {
                    placed += endBox
                    label.draw(context, text, x, edgeY, verticalPosition = if (above) Position.Vertical.Top else Position.Vertical.Bottom)
                    return
                }
                // Beside the marker, at the height of its middle
                val centerY = if (above) reading - half / 2 else reading + half / 2
                val sideX = x + half + air
                val sideBox = Rect(sideX, centerY - height / 2, sideX + width, centerY + height / 2)
                if (fits(sideBox)) {
                    placed += sideBox
                    label.draw(context, text, sideX, centerY, horizontalPosition = Position.Horizontal.End)
                }
            }
            // A measured value first: it is a glucose value, the strongest kind of mark on this graph.
            // Beside the icon, on its right, or on its left near the right edge
            for (marker in markers.calibrations) {
                val x = canvasX(marker.x)
                if (x < layerBounds.left || x > layerBounds.right) continue
                val text = formatGlucose(marker.value)
                val width = measuredText.getWidth(context, text)
                val height = measuredText.getHeight(context, text)
                val y = canvasY(marker.y)
                val offset = (MARKER_SIZE_CALIBRATION / 2).pixels + air
                val right = Rect(x + offset, y - height / 2, x + offset + width, y + height / 2)
                val left = Rect(x - offset - width, y - height / 2, x - offset, y + height / 2)
                val box = listOf(right, left).firstOrNull { fits(it) } ?: continue
                placed += box
                measuredText.draw(context, text, box.left, y, horizontalPosition = Position.Horizontal.End)
            }
            for (marker in markers.insulin.sortedByDescending { it.amount }) {
                drawLabel(insulinText, formatBolusLabel(marker.amount, decimalFormatter), marker, above = true)
            }
            for (marker in markers.carbs.sortedByDescending { it.amount }) {
                drawLabel(carbsText, formatCarbsLabel(marker.amount, decimalFormatter), marker, above = false)
            }
        }
    }

    private fun CartesianDrawingContext.canvasX(x: Double): Float =
        layerBounds.left + layerDimensions.startPadding +
            layerDimensions.xSpacing * ((x - ranges.minX) / ranges.xStep).toFloat() - scroll

    private fun CartesianDrawingContext.canvasY(y: Double): Float {
        val yRange = ranges.getYRange(Axis.Position.Vertical.Start)
        return layerBounds.bottom - ((y - yRange.minY) / yRange.length).toFloat() * layerBounds.height
    }

    private companion object {

        /** The line from a measured value to the sensor curve: thin and dotted, a hint, not a mark. */
        val MEASURED_CONNECTOR_WIDTH = 1.dp
        val MEASURED_CONNECTOR_DASH = 2.dp
    }
}


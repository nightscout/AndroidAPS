package app.aaps.ui.compose.overview.graphs

import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.configuration.Constants
import app.aaps.core.graph.vico.AdaptiveStep
import app.aaps.core.graph.vico.Square
import app.aaps.core.interfaces.overview.graph.DeviationType
import app.aaps.core.interfaces.overview.graph.GraphDataPoint
import app.aaps.core.interfaces.overview.graph.SeriesType
import app.aaps.core.interfaces.overview.graph.TreatmentGraphData
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalDecimalFormatter
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
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
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Position
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import kotlinx.coroutines.delay

/**
 * CartesianChartModelProducer.update() skips notifying receivers (and thus skips recomputing axis
 * ranges) when a transaction's partials AND extraStore are both unchanged from the last one. Since
 * scrolling/zooming re-submits identical series data (only the axis ranges changed), stashing
 * the axis setup here forces the extraStore to differ, so Vico actually reprocesses the
 * transaction instead of silently dropping it.
 */
private val AXIS_SCALE_KEY = ExtraStore.Key<AxisScale>()


/** The axis setup that a scroll or zoom can change. Equal values mean the axes did not change. */
private data class AxisScale(
    val left: CartesianLayerRangeProvider,
    val right: CartesianLayerRangeProvider,
    val startAxisItemPlacer: VerticalAxis.ItemPlacer,
    val endAxisItemPlacer: VerticalAxis.ItemPlacer
)

/** Everything the chart model of a graph is built from. Equal values give the same model. */
private data class ModelInput(
    val leftSpecs: List<SeriesSpec>,
    val rightSpecs: List<SeriesSpec>,
    val hasRightAxis: Boolean,
    val rightFirst: Boolean,
    val maxX: Double,
    val axisScale: AxisScale
)

/**
 * General-purpose secondary graph composable.
 *
 * Shows one or two series. Every series draws the same way, on either axis:
 * - IOB: Blue line with area fill + extended boluses. Manual boluses and SMBs are on the BG graph only:
 *   shown here too, they competed with BG
 * - COB: Orange adaptive-step line with area fill, nothing more. The carbs are marked on the BG graph,
 *   and the failover is a bar on the treatment belt: shown here too, they competed with BG.
 * - Simple line series (AbsIOB, BGI, Sensitivity, VarSens, DevSlope): Colored line with gradient fill
 * - Deviations: Per-type colored step lines with gradient fill (POSITIVE/NEGATIVE/EQUAL/UAM/CSF)
 * - Steps: Columns like the deviations, see [processStepsColumns]
 * - Heart rate: Thin curved line, one per stretch of readings, so it has a break where the watch sent nothing
 *
 * The first series is drawn against the left axis and the second against the right one, unless the
 * two share a unit and so one axis. The axes follow the rules in SecondaryGraphAxes.kt.
 */
@Composable
fun SecondaryGraphCompose(
    viewModel: GraphViewModel,
    seriesTypes: List<SeriesType>,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState,
    derivedTimeRange: Pair<Long, Long>?,
    nowTimestamp: Long,
    /** While holding the BG graph, a line at the inspected time. Null turns it off. */
    inspect: GraphInspectState? = null,
    modifier: Modifier = Modifier
) {
    val dateUtil = LocalDateUtil.current
    if (seriesTypes.isEmpty()) return

    val sides = remember(seriesTypes) { graphSides(seriesTypes) }

    val hasRealTimeRange = derivedTimeRange != null
    val (minTimestamp, maxTimestamp) = derivedTimeRange ?: run {
        val now = dateUtil.now()
        val dayAgo = now - Constants.GRAPH_TIME_RANGE_HOURS * 60 * 60 * 1000L
        dayAgo to now
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    val minX = 0.0
    val maxX = remember(minTimestamp, maxTimestamp) {
        timestampToX(maxTimestamp, minTimestamp)
    }
    // Only a key of the cached series below. Null until the real range is known: those series are empty then, and
    // a real range in the same minutes as the stand-in one kept them empty until the data changed (#5418).
    val stableTimeRange = remember(hasRealTimeRange, minTimestamp / 60000, maxTimestamp / 60000) {
        if (hasRealTimeRange) minTimestamp to maxTimestamp else null
    }

    // =========================================================================
    // Collect data flows — only for the series this graph shows
    // =========================================================================

    val hasIob = SeriesType.IOB in seriesTypes
    val hasCob = SeriesType.COB in seriesTypes

    val iobData = if (hasIob) viewModel.iobGraphFlow.collectAsStateWithLifecycle().value else null
    val cobData = if (hasCob) viewModel.cobGraphFlow.collectAsStateWithLifecycle().value else null
    val treatmentData = if (hasIob) viewModel.treatmentGraphFlow.collectAsStateWithLifecycle().value else null
    val absIobData = if (SeriesType.ABS_IOB in seriesTypes) viewModel.absIobGraphFlow.collectAsStateWithLifecycle().value else null
    val bgiData = if (SeriesType.BGI in seriesTypes) viewModel.bgiGraphFlow.collectAsStateWithLifecycle().value else null
    val deviationsData = if (SeriesType.DEVIATIONS in seriesTypes) viewModel.deviationsGraphFlow.collectAsStateWithLifecycle().value else null
    val ratioData = if (SeriesType.SENSITIVITY in seriesTypes) viewModel.ratioGraphFlow.collectAsStateWithLifecycle().value else null
    val varSensData = if (SeriesType.VAR_SENSITIVITY in seriesTypes) viewModel.varSensGraphFlow.collectAsStateWithLifecycle().value else null
    val devSlopeData = if (SeriesType.DEV_SLOPE in seriesTypes) viewModel.devSlopeGraphFlow.collectAsStateWithLifecycle().value else null
    val hrData = if (SeriesType.HEART_RATE in seriesTypes) viewModel.heartRateGraphFlow.collectAsStateWithLifecycle().value else null
    val stepsData = if (SeriesType.STEPS in seriesTypes) viewModel.stepsGraphFlow.collectAsStateWithLifecycle().value else null
    val activityData = if (SeriesType.ACTIVITY in seriesTypes) viewModel.activityGraphFlow.collectAsStateWithLifecycle().value else null

    // Cache last non-empty treatment data to survive reset() cycles
    val lastTreatmentData = remember { mutableStateOf(treatmentData ?: TreatmentGraphData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())) }
    // Any treatment counts, not only extended boluses: when the last extended bolus is deleted, its empty
    // list must still replace the cache, or the deleted bar stays on the graph
    if (treatmentData != null && (treatmentData.boluses.isNotEmpty() || treatmentData.carbs.isNotEmpty() || treatmentData.extendedBoluses.isNotEmpty())) {
        lastTreatmentData.value = treatmentData
    }

    // =========================================================================
    // Process all series into x/y coordinates
    // =========================================================================

    // Simple line series processing
    val processedSimpleSeries = remember(
        seriesTypes, stableTimeRange, absIobData, bgiData, ratioData, varSensData, devSlopeData, hrData, activityData
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
            // One line per stretch, so the line has a break where the watch sent nothing. x is whole minutes, and
            // two readings in the same minute would draw a vertical stroke.
            hrData?.segments?.forEach { segment ->
                add(SeriesType.HEART_RATE to processPoints(segment, minTimestamp, minX, maxX).distinctBy { it.first })
            }
            activityData?.let {
                if (it.activity.isNotEmpty()) add(SeriesType.ACTIVITY to processPoints(it.activity, minTimestamp, minX, maxX))
                if (it.activityPrediction.isNotEmpty()) add(SeriesType.ACTIVITY to processPoints(it.activityPrediction, minTimestamp, minX, maxX))
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

    // Steps as columns, like the deviations
    val processedSteps = remember(stepsData, stableTimeRange) {
        if (!hasRealTimeRange || stepsData == null) return@remember emptyList()
        processStepsColumns(stepsData.steps, minTimestamp, minX, maxX)
    }

    // IOB line processing
    val processedIob = remember(iobData, stableTimeRange) {
        if (!hasRealTimeRange || iobData == null) return@remember emptyList()
        processPoints(iobData.iob, minTimestamp, minX, maxX)
    }

    // Extended boluses on the IOB graph. hasIob is a key: the treatments outlive the series in lastTreatmentData.
    val processedExtBoluses = remember(hasIob, lastTreatmentData.value, stableTimeRange) {
        if (!hasRealTimeRange || !hasIob) return@remember emptyList()
        processExtendedBoluses(lastTreatmentData.value, minTimestamp, minX, maxX)
    }

    // COB line processing
    val processedCob = remember(cobData, stableTimeRange) {
        if (!hasRealTimeRange || cobData == null) return@remember emptyList()
        processPoints(cobData.cob, minTimestamp, minX, maxX)
    }

    // What each series draws: its line and its markers, the same on either axis
    val seriesSpecs = remember(
        seriesTypes, processedDeviationLines, processedSteps, processedIob, processedExtBoluses, processedCob, processedSimpleSeries, processedDevSlopeMin
    ) {
        seriesTypes.associateWith { type ->
            buildList {
                when (type) {
                    SeriesType.DEVIATIONS -> processedDeviationLines?.series?.forEach { (deviationType, yValues) ->
                        add(SeriesSpec(processedDeviationLines.allX, yValues, SeriesSlot.DeviationLine(deviationType)))
                    }

                    SeriesType.STEPS      -> addPoints(processedSteps, SeriesSlot.StepsColumns)

                    SeriesType.IOB        -> {
                        addPoints(processedIob, SeriesSlot.IobLine)
                        processedExtBoluses.forEach { (start, end, amount) ->
                            add(SeriesSpec(listOf(start, end), listOf(amount, amount), SeriesSlot.ExtBolus))
                        }
                    }

                    SeriesType.COB        -> addPoints(processedCob, SeriesSlot.CobLine)

                    else                  -> {
                        processedSimpleSeries.filter { it.first == type }.forEach { (_, points) -> addPoints(points, SeriesSlot.SimpleLine(type)) }
                        if (type == SeriesType.DEV_SLOPE) addPoints(processedDevSlopeMin, SeriesSlot.DevSlopeMin)
                    }
                }
            }
        }
    }
    // Columns first on an axis they share, so the line of the other series stays on top
    val leftSpecs = remember(sides, seriesSpecs) { sides.left.sortedBy { it !in COLUMN_SERIES }.flatMap { seriesSpecs[it].orEmpty() } }
    val rightSpecs = remember(sides, seriesSpecs) { sides.right.sortedBy { it !in COLUMN_SERIES }.flatMap { seriesSpecs[it].orEmpty() } }

    // Visible-window bounds (same x-unit as processed* points — minutes from minTimestamp) for
    // windowing the Y-axis scale to only the currently scrolled/zoomed portion of this graph's
    // own chart, instead of the full loaded time range.
    // The reporter writes into a StateFlow holder (not Compose state) on every draw pass — see
    // VisibleRangeHolder — and we collect it here, keeping Compose state writes off the draw path.
    // The axis ranges below are windowed to it, and the model is rebuilt when they change — Vico
    // only reliably re-applies axis ranges via a real modelProducer.runTransaction, not merely by
    // swapping the CartesianLayerRangeProvider instance.
    val visibleRangeHolder = remember { VisibleRangeHolder() }
    val visibleRangeReporter = rememberVisibleRangeReporter(visibleRangeHolder)

    var visibleRange by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // Runs only when the range changes. The delay limits updates to 20 per second during
    // scroll/zoom (each one rebuilds the model); the StateFlow keeps the latest value meanwhile.
    LaunchedEffect(visibleRangeHolder) {
        visibleRangeHolder.value.collect {
            visibleRange = it
            delay(50)
        }
    }

    val visibleMinX = visibleRange?.first
    val visibleMaxX = visibleRange?.second

    // Each axis by the rule of its series, from what the visible window shows. Every series counts,
    // markers too: an axis sized without the markers drawn on it cuts them off.
    val leftRange = remember(sides, leftSpecs, visibleMinX, visibleMaxX) {
        sides.left.firstOrNull()?.let { axisRange(axisRuleOf(it), windowedValues(leftSpecs, visibleMinX, visibleMaxX)) }
    }
    val rightRange = remember(sides, rightSpecs, visibleMinX, visibleMaxX) {
        sides.right.firstOrNull()?.let { axisRange(axisRuleOf(it), windowedValues(rightSpecs, visibleMinX, visibleMaxX)) }
    }

    val axes = remember(leftRange, rightRange) { graphAxes(leftRange, rightRange) }
    val hasRightAxis = sides.right.isNotEmpty()
    // Columns are drawn first, so the line of the other axis stays on top of them
    val rightFirst = sides.right.any { it in COLUMN_SERIES }

    // =========================================================================
    // Line styles
    // =========================================================================

    val seriesColors = rememberSeriesColors()
    val iobLineStyle = rememberIobLineStyles()
    val cobLine = rememberCobLine()
    val normalizerLine = remember { createNormalizerLine() }

    // The normalizer comes first, here and in the model below. Vico pairs lines and series by index,
    // and when the series of this graph change, it draws the new lines with the old model until the
    // new model is built. With the normalizer at index 0 it is still paired with itself then, so the
    // point spacing stays the same as in the other graphs. When it came last, the spacing shrank for
    // that moment, Vico cut the scroll down to the new end, and the graph stayed shifted sideways.
    val leftLines = remember(leftSpecs, seriesColors, iobLineStyle, cobLine, normalizerLine) {
        listOf(normalizerLine) + leftSpecs.map { lineFor(it.slot, seriesColors, iobLineStyle, cobLine) }
    }
    val rightLines = remember(rightSpecs, seriesColors, iobLineStyle, cobLine, normalizerLine) {
        listOf(normalizerLine) + rightSpecs.map { lineFor(it.slot, seriesColors, iobLineStyle, cobLine) }
    }

    // =========================================================================
    // Chart rendering
    // =========================================================================

    val bgReadings by viewModel.bgReadingsFlow.collectAsStateWithLifecycle()
    val bottomAxisItemPlacer = rememberBottomAxisItemPlacer(minTimestamp)
    val nowLineColor = MaterialTheme.colorScheme.onSurface
    val nowLine = rememberNowLine(minTimestamp, nowTimestamp, nowLineColor)
    val inspectLine = rememberInspectLine(inspect)
    val clockChanges = rememberClockChangeDecoration(minTimestamp, maxTimestamp, bgReadings)
    val decorations = remember(nowLine, visibleRangeReporter, clockChanges, inspectLine) {
        listOfNotNull(nowLine, visibleRangeReporter, clockChanges, inspectLine)
    }

    val leftRangeProvider = remember(maxX, axes.left) {
        CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX, minY = axes.left.min, maxY = axes.left.max)
    }
    val rightRangeProvider = remember(maxX, axes.right) {
        val right = axes.right ?: EMPTY_AXIS
        CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX, minY = right.min, maxY = right.max)
    }
    // Build chart layers
    val leftLayer = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(leftLines),
        rangeProvider = leftRangeProvider,
        verticalAxisPosition = Axis.Position.Vertical.Start
    )
    val rightLayer = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(rightLines),
        rangeProvider = rightRangeProvider,
        verticalAxisPosition = Axis.Position.Vertical.End
    )

    // Labels only where the series has values: not on the
    // part of an axis that was stretched to put its zero next to the other axis' zero, and none at all
    // on an axis whose series has no values
    val startAxisItemPlacer = remember(leftRange) {
        leftRange?.let { NiceVerticalAxisItemPlacer(labelMin = it.min, labelMax = it.max) } ?: NoLabelsAxisItemPlacer
    }
    val endAxisItemPlacer = remember(rightRange) {
        rightRange?.let { NiceVerticalAxisItemPlacer(labelMin = it.min, labelMax = it.max) } ?: NoLabelsAxisItemPlacer
    }

    // Rebuilt only when the data or the axes change, not on every scroll step
    val axisScale = AxisScale(leftRangeProvider, rightRangeProvider, startAxisItemPlacer, endAxisItemPlacer)
    val modelInput = ModelInput(
        leftSpecs, rightSpecs, hasRightAxis, rightFirst, maxX, axisScale
    )
    val latestModelInput by rememberUpdatedState(modelInput)
    // One transaction after the other, and none is cancelled half-way. When the input changes during a
    // transaction, the newest input waits for it, and the inputs in between are skipped. An effect keyed
    // on the input cancelled the running transaction instead. When that came just after Vico had
    // stopped the animation of the transaction before, the lines stayed where the animation stopped,
    // part-way between the old and the new scale, while the labels showed the new scale. If the next
    // input was equal to the last finished one (axes going A, B, A), Vico skipped it as a repeat, and
    // the lines stayed like that.
    LaunchedEffect(modelProducer) {
        snapshotFlow { latestModelInput }.collect { input ->
            // Always populate the model — even with no data / no real time range — so the chart frame
            // (axes, grid, now-line) renders the empty-state normalizer series instead of staying blank.
            // (Matches TreatmentBeltGraphCompose.) When data is absent the processed* lists are empty,
            // so only the normalizer is emitted; the left range provider anchors an empty 0..1 axis.
            // Each layer gets the normalizer first and then its series in the exact order of its specs,
            // so the line styles (built from the same lists) map 1:1 to the series. The normalizer keeps
            // the maxPointSize the same across all charts, see leftLines.
            val normalizer = normalizerX(input.maxX) to NORMALIZER_Y
            val left = listOf(normalizer) + input.leftSpecs.map { it.x to it.y }
            val end = if (input.hasRightAxis) listOf(normalizer) + input.rightSpecs.map { it.x to it.y } else null
            // The same order as the layers handed to the chart below
            val layerSeries = if (end != null && input.rightFirst) listOf(end, left) else listOfNotNull(left, end)
            modelProducer.runTransaction {
                for (seriesOfLayer in layerSeries) lineModel { seriesOfLayer.forEach { (x, y) -> series(x = x, y = y) } }

                // Forces Vico to reprocess this transaction even when the series data above is
                // identical to last time (see AXIS_SCALE_KEY doc) — otherwise scrolling/zooming
                // would re-submit the same partials and get silently skipped, never picking up the
                // updated range providers.
                extras { it[AXIS_SCALE_KEY] = input.axisScale }
            }
        }
    }

    val startAxis = VerticalAxis.rememberStart(
        itemPlacer = startAxisItemPlacer,
        label = rememberTextComponent(
            style = TextStyle(color = MaterialTheme.colorScheme.onSurface),
            minWidth = TextComponent.MinWidth.fixed(30.dp)
        ),
        // No axis line and no ticks, as on the BG graph: the labels show the scale, and the default
        // near-white line made an empty graph a heavy box
        line = null,
        tick = null,
        guideline = rememberGridLine()
    )
    // The right axis has its labels inside the plot, in the color of its series, and no line, tick or
    // grid of its own. So it takes no width, and the hour lines stay under those of the BG graph.
    val endAxis = VerticalAxis.rememberEnd(
        line = null,
        label = rememberTextComponent(style = TextStyle(color = seriesColors.colorFor(sides.right.firstOrNull() ?: SeriesType.COB))),
        horizontalLabelPosition = VerticalAxis.HorizontalLabelPosition.Inside,
        tick = null,
        guideline = null,
        itemPlacer = endAxisItemPlacer
    )
    // No time labels: the BG graph above shows them, and the hour lines stay aligned with it.
    // This keeps the graphs below BG compact.
    val bottomAxis = HorizontalAxis.rememberBottom(
        itemPlacer = bottomAxisItemPlacer,
        label = null,
        // The zero line, solid and quiet in the grid colour; dotted hour lines
        line = LineComponent(fill = Fill(AapsTheme.generalColors.graphGrid)),
        tick = null,
        guideline = rememberGridLine()
    )

    // Press and hold works here too. The marker draws nothing, it only hands the time under the
    // finger to the shared inspect state; the line, the dot and the pill are drawn elsewhere.
    val inspectMarker = remember(inspect) { inspect?.let { SilentInspectMarker } }
    val defaultMarkerController = CartesianMarkerController.rememberShowOnPress()
    val markerController = inspect?.controller ?: defaultMarkerController

    val layers = when {
        !hasRightAxis  -> listOf(leftLayer)
        rightFirst     -> listOf(rightLayer, leftLayer)
        else           -> listOf(leftLayer, rightLayer)
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            *layers.toTypedArray(),
            startAxis = startAxis,
            endAxis = endAxis.takeIf { rightRange != null },
            bottomAxis = bottomAxis, decorations = decorations, getXStep = { _, _, _ -> 1.0 },
            marker = inspectMarker, markerVisibilityListener = inspect?.visibilityListener, markerController = markerController
        ),
        modelProducer = modelProducer,
        modifier = modifier.fillMaxWidth(),
        scrollState = scrollState, zoomState = zoomState
    )
}

/** The series drawn as columns. They are drawn before the other series, so a line is never hidden behind them. */
private val COLUMN_SERIES = setOf(SeriesType.DEVIATIONS, SeriesType.STEPS)

/** The line style of one series. */
private fun lineFor(slot: SeriesSlot, colors: SeriesColors, iobStyles: IobLineStyles, cobLine: LineCartesianLayer.Line): LineCartesianLayer.Line = when (slot) {
    is SeriesSlot.DeviationLine -> createColumnLine(deviationColor(slot.type))
    SeriesSlot.StepsColumns     -> createColumnLine(colors.steps)
    SeriesSlot.IobLine          -> iobStyles.iobLine
    SeriesSlot.ExtBolus         -> iobStyles.extBolusLine
    SeriesSlot.CobLine          -> cobLine
    SeriesSlot.DevSlopeMin      -> createDevSlopeMinLine()
    is SeriesSlot.SimpleLine    -> createSeriesLine(slot.type, colors)
}

// =========================================================================
// Series slot identifiers (for matching line styles to data series)
// =========================================================================

/** One drawn series: its x/y data plus the slot that selects its line style. */
internal class SeriesSpec(val x: List<Double>, val y: List<Double>, val slot: SeriesSlot)

/** Adds [points] as one series, unless there are none. */
private fun MutableList<SeriesSpec>.addPoints(points: List<Pair<Double, Double>>, slot: SeriesSlot) {
    if (points.isNotEmpty()) add(SeriesSpec(points.map { it.first }, points.map { it.second }, slot))
}

internal sealed class SeriesSlot {
    data class DeviationLine(val type: DeviationType) : SeriesSlot()
    data object StepsColumns : SeriesSlot()
    data object IobLine : SeriesSlot()
    data object ExtBolus : SeriesSlot()
    data object CobLine : SeriesSlot()
    data class SimpleLine(val type: SeriesType) : SeriesSlot()
    data object DevSlopeMin : SeriesSlot()
}

// =========================================================================
// Data processing helpers
// =========================================================================

/**
 * The y values of the [series], inside [visibleMinX]..[visibleMaxX]. When none
 * of them has a point there (e.g. scrolled into a future gap with no data), all their values: the
 * fallback applies to the whole set, so windowed points of one series are never mixed with all the
 * points of another.
 */
internal fun windowedValues(series: List<SeriesSpec>, visibleMinX: Double?, visibleMaxX: Double?): List<Double> {
    fun inWindow(x: Double) = visibleMinX == null || visibleMaxX == null || x in visibleMinX..visibleMaxX
    return series.flatMap { spec -> spec.x.indices.filter { inWindow(spec.x[it]) }.map { spec.y[it] } }
        .ifEmpty { series.flatMap { it.y } }
}

@Suppress("SameParameterValue")
private fun processPoints(points: List<GraphDataPoint>, minTimestamp: Long, minX: Double, maxX: Double): List<Pair<Double, Double>> {
    val pts = points.map { timestampToX(it.timestamp, minTimestamp) to it.value }
    return filterToRange(pts, minX, maxX)
}


/** The minutes a steps point counts, see `StepsGraphData`. */
private const val STEPS_COLUMN_MINUTES = 5.0

/**
 * The steps as columns, drawn with [Square]. A point counts the steps of the [STEPS_COLUMN_MINUTES]
 * that start at its time. The watch sends one about every 40 seconds, so a column simply reaches to
 * the next point. Where the next point is further away, the watch sent nothing: the column ends after
 * its own minutes and the line goes down to zero. Without that, a gap of hours would be drawn as hours
 * of the last count. The last column ends the same way.
 *
 * x is whole minutes, so two points can fall on the same minute. Only the newer one is kept.
 */
internal fun processStepsColumns(points: List<GraphDataPoint>, minTimestamp: Long, minX: Double, maxX: Double): List<Pair<Double, Double>> {
    val perMinute = points.sortedBy { it.timestamp }.map { timestampToX(it.timestamp, minTimestamp) to it.value }.toMap().toList()
    val columns = filterToRange(perMinute, minX, maxX)
    return buildList {
        columns.forEachIndexed { index, (x, y) ->
            add(x to y)
            val end = x + STEPS_COLUMN_MINUTES
            val next = columns.getOrNull(index + 1)?.first
            val close = minOf(end, maxX)
            if ((next == null || next > end) && close > x) add(close to 0.0)
        }
    }
}

/**
 * The extended boluses of the IOB graph, each as (start x, end x, amount), cut to [minX]..[maxX].
 * Manual boluses and SMBs are not here: they are on the BG graph.
 */
internal fun processExtendedBoluses(data: TreatmentGraphData, minTimestamp: Long, minX: Double, maxX: Double): List<Triple<Double, Double, Double>> =
    data.extendedBoluses.mapNotNull { eb ->
        val startX = timestampToX(eb.timestamp, minTimestamp)
        val endX = timestampToX(eb.timestamp + eb.duration, minTimestamp)
        if (endX < minX || startX > maxX) return@mapNotNull null
        val clampedStart = startX.coerceIn(minX, maxX)
        val clampedEnd = endX.coerceIn(minX, maxX)
        if (clampedStart == clampedEnd) return@mapNotNull null
        Triple(clampedStart, clampedEnd, eb.amount)
    }

// =========================================================================
// Line styles
// =========================================================================

/**
 * Where an area fill starts under its line, fading to nothing at the bottom. One value for every
 * series, IOB and COB too: the line carries the value, the fill only shows how much, and it must not
 * be louder than the BG graph above.
 */
private const val AREA_FILL_ALPHA = 0.3f

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

/**
 * The line of a series on the graphs below BG: its colour at the strength of the second tier, see
 * [SECOND_TIER_ALPHA]. Only the line: the axis labels keep the full colour, and an area fill starts
 * from the full colour at [AREA_FILL_ALPHA].
 */
private fun seriesLineFill(color: Color): LineCartesianLayer.LineFill =
    LineCartesianLayer.LineFill.single(Fill(color.copy(alpha = SECOND_TIER_ALPHA)))

/** DevSlope min line — magenta, no fill (matches @color/devSlopeNeg #FF00FF) */
private fun createDevSlopeMinLine(): LineCartesianLayer.Line {
    return LineCartesianLayer.Line(
        fill = seriesLineFill(Color(0xFFFF00FF)),
        areaFill = null
    )
}

/** Create a line style for a given series type, matching legacy rendering */
fun createSeriesLine(type: SeriesType, colors: SeriesColors): LineCartesianLayer.Line {
    val color = colors.colorFor(type)
    return when (type) {
        // Step function with area fill (like IOB)
        SeriesType.ABS_IOB                                                       -> LineCartesianLayer.Line(
            fill = seriesLineFill(color),
            areaFill = LineCartesianLayer.AreaFill.single(
                Fill(Brush.verticalGradient(listOf(color.copy(alpha = AREA_FILL_ALPHA), Color.Transparent)))
            ),
            interpolator = Square
        )
        // Line only, no fill
        SeriesType.DEV_SLOPE, SeriesType.SENSITIVITY, SeriesType.VAR_SENSITIVITY -> LineCartesianLayer.Line(
            fill = seriesLineFill(color),
            areaFill = null
        )
        // Thin curved line, no fill, so it reads well over the steps columns
        SeriesType.HEART_RATE                                                    -> LineCartesianLayer.Line(
            fill = seriesLineFill(color),
            stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 1.5.dp),
            areaFill = null,
            interpolator = LineCartesianLayer.Interpolator.cubic()
        )
        // Default: smooth line with gradient area fill
        else                                                                     -> LineCartesianLayer.Line(
            fill = seriesLineFill(color),
            areaFill = LineCartesianLayer.AreaFill.single(
                Fill(Brush.verticalGradient(listOf(color.copy(alpha = AREA_FILL_ALPHA), Color.Transparent)))
            )
        )
    }
}

// =========================================================================
// IOB line styles (extended boluses)
// =========================================================================

data class IobLineStyles(
    val iobLine: LineCartesianLayer.Line,
    val extBolusLine: LineCartesianLayer.Line
)

/**
 * One size for the bolus and carbs labels on the graphs. Vico gives a label no more width than the
 * gap to its neighbour in the same series and ends it with "..." when the text is wider. The bolus
 * labels used 16 sp and lost that race far more often than the 14 sp carbs labels, so both use the
 * smaller size now.
 */
internal val TREATMENT_LABEL_FONT_SIZE = 14.sp

@Composable
fun rememberIobLineStyles(): IobLineStyles {
    val iobColor = AapsTheme.generalColors.iobPrediction
    val extBolusColor = AapsTheme.elementColors.extendedBolus
    val decimalFormatter = LocalDecimalFormatter.current

    val extBolusLabelComponent = remember(extBolusColor) {
        TextComponent(textStyle = TextStyle(color = extBolusColor, fontSize = TREATMENT_LABEL_FONT_SIZE))
    }
    val extBolusValueFormatter = remember {
        CartesianValueFormatter { _, value, _ -> formatBolusLabel(value, decimalFormatter) }
    }

    return remember(iobColor, extBolusColor, extBolusLabelComponent, extBolusValueFormatter) {
        IobLineStyles(
            iobLine = LineCartesianLayer.Line(
                fill = seriesLineFill(iobColor),
                areaFill = LineCartesianLayer.AreaFill.single(
                    Fill(Brush.verticalGradient(listOf(iobColor.copy(alpha = AREA_FILL_ALPHA), Color.Transparent)))
                ),
                interpolator = Square
            ),
            // The line in the second tier, its amount labels at full colour like a bolus on BG
            extBolusLine = LineCartesianLayer.Line(
                fill = seriesLineFill(extBolusColor),
                areaFill = null,
                dataLabel = extBolusLabelComponent,
                dataLabelPosition = Position.Vertical.Top,
                dataLabelValueFormatter = extBolusValueFormatter
            )
        )
    }
}

// =========================================================================
// COB line
// =========================================================================

/**
 * The COB line: an orange adaptive-step line in the second tier ([SECOND_TIER_ALPHA]) with the area fill of
 * every series, see [AREA_FILL_ALPHA].
 */
@Composable
fun rememberCobLine(): LineCartesianLayer.Line {
    val cobColor = AapsTheme.generalColors.cobPrediction
    return remember(cobColor) {
        LineCartesianLayer.Line(
            fill = seriesLineFill(cobColor),
            areaFill = LineCartesianLayer.AreaFill.single(
                Fill(Brush.verticalGradient(listOf(cobColor.copy(alpha = AREA_FILL_ALPHA), Color.Transparent)))
            ),
            interpolator = AdaptiveStep
        )
    }
}

// =========================================================================
// Label formatters (shared by the IOB and BG graphs)
// =========================================================================

/** Formats bolus amount: 1.0->"1", 1.2->"1.2", 0.8->".8" (drop leading zero) */
internal fun formatBolusLabel(value: Double, decimalFormatter: DecimalFormatter): String {
    if (value == 0.0) return ""
    val formatted = decimalFormatter.to2Decimal(value).trimEnd('0').trimEnd('.').trimEnd(',')
    return if (formatted.startsWith("0.")) formatted.substring(1) else formatted
}

/** Formats carbs amount: 45.0->"45", 7.5->"7.5", 0.0->"" */
internal fun formatCarbsLabel(value: Double, decimalFormatter: DecimalFormatter): String {
    if (value == 0.0) return ""
    return decimalFormatter.to1Decimal(value).trimEnd('0').trimEnd('.').trimEnd(',')
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

/** Columns: a step line with solid area fill and no stroke. Used for the deviations (one color per type) and the steps. */
private fun createColumnLine(color: Color): LineCartesianLayer.Line {
    return LineCartesianLayer.Line(
        fill = LineCartesianLayer.LineFill.single(Fill(color)),
        stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 0.dp),
        areaFill = LineCartesianLayer.AreaFill.single(Fill(color.copy(alpha = 0.5f))),
        interpolator = Square
    )
}

/** Processed deviation data split by type for multi-series line rendering */
internal data class ProcessedDeviationLines(
    val allX: List<Double>,
    val series: Map<DeviationType, List<Double>>
)

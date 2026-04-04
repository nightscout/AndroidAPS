package app.aaps.ui.compose.overview.graphs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.graph.vico.AdaptiveStep
import app.aaps.core.graph.vico.Square
import app.aaps.core.interfaces.overview.graph.BolusType
import app.aaps.core.interfaces.overview.graph.GraphDataPoint
import app.aaps.core.interfaces.overview.graph.SeriesType
import app.aaps.core.interfaces.overview.graph.TreatmentGraphData
import app.aaps.core.ui.compose.AapsTheme
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.VicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Position
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent

/**
 * General-purpose secondary graph composable.
 *
 * Renders any combination of series types on a single chart.
 * Each series type has its own color and rendering style:
 * - IOB: Blue line with area fill + bolus markers (SMBs, normal boluses, extended boluses)
 * - COB: Orange adaptive-step line with area fill + carbs markers + failover dots
 * - Simple line series (AbsIOB, BGI, Sensitivity, VarSens, DevSlope, HR, Steps): Colored line with gradient fill
 * - Deviations: Colored line (TODO: bar rendering in future)
 */
@Composable
fun SecondaryGraphCompose(
    viewModel: GraphViewModel,
    seriesTypes: Set<SeriesType>,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState,
    derivedTimeRange: Pair<Long, Long>?,
    nowTimestamp: Long,
    modifier: Modifier = Modifier
) {
    if (seriesTypes.isEmpty()) return

    val hasRealTimeRange = derivedTimeRange != null
    val (minTimestamp, maxTimestamp) = derivedTimeRange ?: run {
        val now = System.currentTimeMillis()
        val dayAgo = now - 24 * 60 * 60 * 1000L
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

    val hasIob = SeriesType.IOB in seriesTypes
    val hasCob = SeriesType.COB in seriesTypes

    val iobData = if (hasIob) viewModel.iobGraphFlow.collectAsStateWithLifecycle().value else null
    val cobData = if (hasCob) viewModel.cobGraphFlow.collectAsStateWithLifecycle().value else null
    val treatmentData = if (hasIob || hasCob) viewModel.treatmentGraphFlow.collectAsStateWithLifecycle().value else null
    val absIobData = if (SeriesType.ABS_IOB in seriesTypes) viewModel.absIobGraphFlow.collectAsStateWithLifecycle().value else null
    val bgiData = if (SeriesType.BGI in seriesTypes) viewModel.bgiGraphFlow.collectAsStateWithLifecycle().value else null
    val deviationsData = if (SeriesType.DEVIATIONS in seriesTypes) viewModel.deviationsGraphFlow.collectAsStateWithLifecycle().value else null
    val ratioData = if (SeriesType.SENSITIVITY in seriesTypes) viewModel.ratioGraphFlow.collectAsStateWithLifecycle().value else null
    val varSensData = if (SeriesType.VAR_SENSITIVITY in seriesTypes) viewModel.varSensGraphFlow.collectAsStateWithLifecycle().value else null
    val devSlopeData = if (SeriesType.DEV_SLOPE in seriesTypes) viewModel.devSlopeGraphFlow.collectAsStateWithLifecycle().value else null
    val hrData = if (SeriesType.HEART_RATE in seriesTypes) viewModel.heartRateGraphFlow.collectAsStateWithLifecycle().value else null
    val stepsData = if (SeriesType.STEPS in seriesTypes) viewModel.stepsGraphFlow.collectAsStateWithLifecycle().value else null

    // Cache last non-empty treatment data to survive reset() cycles
    val lastTreatmentData = remember { mutableStateOf(treatmentData ?: TreatmentGraphData(emptyList(), emptyList(), emptyList(), emptyList())) }
    if (treatmentData != null && (treatmentData.boluses.isNotEmpty() || treatmentData.carbs.isNotEmpty() || treatmentData.extendedBoluses.isNotEmpty())) {
        lastTreatmentData.value = treatmentData
    }

    // =========================================================================
    // Process all series into x/y coordinates
    // =========================================================================

    // Simple line series processing
    val processedSimpleSeries = remember(
        stableTimeRange, absIobData, bgiData, deviationsData, ratioData, varSensData, devSlopeData, hrData, stepsData
    ) {
        if (!hasRealTimeRange) return@remember emptyList<Pair<SeriesType, List<Pair<Double, Double>>>>()
        buildList {
            absIobData?.absIob?.takeIf { it.isNotEmpty() }?.let {
                add(SeriesType.ABS_IOB to processPoints(it, minTimestamp, minX, maxX))
            }
            bgiData?.let {
                if (it.bgi.isNotEmpty()) add(SeriesType.BGI to processPoints(it.bgi, minTimestamp, minX, maxX))
                // TODO: differentiate prediction line style (e.g., dashed) from historical
                if (it.bgiPrediction.isNotEmpty()) add(SeriesType.BGI to processPoints(it.bgiPrediction, minTimestamp, minX, maxX))
            }
            deviationsData?.deviations?.takeIf { it.isNotEmpty() }?.let { devs ->
                val pts = devs.map { GraphDataPoint(it.timestamp, it.value) }
                add(SeriesType.DEVIATIONS to processPoints(pts, minTimestamp, minX, maxX))
            }
            ratioData?.ratio?.takeIf { it.isNotEmpty() }?.let {
                add(SeriesType.SENSITIVITY to processPoints(it, minTimestamp, minX, maxX))
            }
            varSensData?.varSens?.takeIf { it.isNotEmpty() }?.let {
                add(SeriesType.VAR_SENSITIVITY to processPoints(it, minTimestamp, minX, maxX))
            }
            devSlopeData?.let {
                // TODO: differentiate dsMax vs dsMin line style (e.g., dashed for min)
                if (it.dsMax.isNotEmpty()) add(SeriesType.DEV_SLOPE to processPoints(it.dsMax, minTimestamp, minX, maxX))
                if (it.dsMin.isNotEmpty()) add(SeriesType.DEV_SLOPE to processPoints(it.dsMin, minTimestamp, minX, maxX))
            }
            hrData?.heartRates?.takeIf { it.isNotEmpty() }?.let {
                add(SeriesType.HEART_RATE to processPoints(it, minTimestamp, minX, maxX))
            }
            stepsData?.steps?.takeIf { it.isNotEmpty() }?.let {
                add(SeriesType.STEPS to processPoints(it, minTimestamp, minX, maxX))
            }
        }
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
        if (!hasRealTimeRange || cobData == null) return@remember Pair(emptyList<Pair<Double, Double>>(), emptyList<Pair<Double, Double>>())
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

    // =========================================================================
    // Track which series slots are active (for line style matching)
    // =========================================================================

    // Use a sealed approach: track series slot identifiers
    val activeSlotState = remember { mutableStateOf(emptyList<SeriesSlot>()) }

    LaunchedEffect(processedSimpleSeries, processedIob, processedIobTreatments, processedCob, processedCarbs, maxX) {
        if (!hasRealTimeRange) return@LaunchedEffect

        val slots = mutableListOf<SeriesSlot>()
        modelProducer.runTransaction {
            lineSeries {
                // IOB line
                if (processedIob.isNotEmpty()) {
                    series(x = processedIob.map { it.first }, y = processedIob.map { it.second })
                    slots.add(SeriesSlot.IobLine)
                }

                // IOB overlays: SMBs (small, medium, large), normal boluses, extended boluses
                if (processedIobTreatments.smallSmbs.isNotEmpty()) {
                    series(x = processedIobTreatments.smallSmbs.map { it.first }, y = processedIobTreatments.smallSmbs.map { it.second })
                    slots.add(SeriesSlot.SmallSmb)
                }
                if (processedIobTreatments.mediumSmbs.isNotEmpty()) {
                    series(x = processedIobTreatments.mediumSmbs.map { it.first }, y = processedIobTreatments.mediumSmbs.map { it.second })
                    slots.add(SeriesSlot.MediumSmb)
                }
                if (processedIobTreatments.largeSmbs.isNotEmpty()) {
                    series(x = processedIobTreatments.largeSmbs.map { it.first }, y = processedIobTreatments.largeSmbs.map { it.second })
                    slots.add(SeriesSlot.LargeSmb)
                }
                if (processedIobTreatments.normalBoluses.isNotEmpty()) {
                    series(x = processedIobTreatments.normalBoluses.map { it.first }, y = processedIobTreatments.normalBoluses.map { it.second })
                    slots.add(SeriesSlot.NormalBolus)
                }
                for ((start, end, amount) in processedIobTreatments.extBoluses) {
                    series(x = listOf(start, end), y = listOf(amount, amount))
                    slots.add(SeriesSlot.ExtBolus)
                }

                // COB line
                val (cobFiltered, failoverFiltered) = processedCob
                if (cobFiltered.isNotEmpty()) {
                    series(x = cobFiltered.map { it.first }, y = cobFiltered.map { it.second })
                    slots.add(SeriesSlot.CobLine)
                }
                if (failoverFiltered.isNotEmpty()) {
                    series(x = failoverFiltered.map { it.first }, y = failoverFiltered.map { it.second })
                    slots.add(SeriesSlot.FailoverDots)
                }
                if (processedCarbs.isNotEmpty()) {
                    series(x = processedCarbs.map { it.first }, y = processedCarbs.map { it.second })
                    slots.add(SeriesSlot.CarbsMarker)
                }

                // Simple line series
                for ((type, pts) in processedSimpleSeries) {
                    if (pts.isNotEmpty()) {
                        series(x = pts.map { it.first }, y = pts.map { it.second })
                        slots.add(SeriesSlot.SimpleLine(type))
                    }
                }

                // Normalizer — ensures identical maxPointSize across all charts
                series(x = normalizerX(maxX), y = NORMALIZER_Y)
            }
        }
        activeSlotState.value = slots
    }

    // =========================================================================
    // Line styles
    // =========================================================================

    val seriesColors = rememberSeriesColors()
    val iobLineStyle = rememberIobLineStyles()
    val cobLineStyle = rememberCobLineStyles()
    val normalizerLine = remember { createNormalizerLine() }

    val activeSlots by activeSlotState
    val lines = remember(activeSlots, seriesColors, iobLineStyle, cobLineStyle, normalizerLine) {
        buildList {
            for (slot in activeSlots) {
                add(
                    when (slot) {
                        SeriesSlot.IobLine      -> iobLineStyle.iobLine
                        SeriesSlot.SmallSmb     -> iobLineStyle.smallSmbLine
                        SeriesSlot.MediumSmb    -> iobLineStyle.mediumSmbLine
                        SeriesSlot.LargeSmb     -> iobLineStyle.largeSmbLine
                        SeriesSlot.NormalBolus  -> iobLineStyle.bolusLine
                        SeriesSlot.ExtBolus     -> iobLineStyle.extBolusLine
                        SeriesSlot.CobLine      -> cobLineStyle.cobLine
                        SeriesSlot.FailoverDots -> cobLineStyle.failoverDotsLine
                        SeriesSlot.CarbsMarker  -> cobLineStyle.carbsLine
                        is SeriesSlot.SimpleLine -> createSeriesLine(slot.type, seriesColors)
                    }
                )
            }
            add(normalizerLine)
        }
    }

    // =========================================================================
    // Chart rendering
    // =========================================================================

    val timeFormatter = rememberTimeFormatter(minTimestamp)
    val bottomAxisItemPlacer = rememberBottomAxisItemPlacer(minTimestamp)
    val nowLineColor = MaterialTheme.colorScheme.onSurface
    val nowLine = rememberNowLine(minTimestamp, nowTimestamp, nowLineColor)
    val decorations = remember(nowLine) { listOf(nowLine) }
    val rangeProvider = remember(maxX) { CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX) }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(lines),
                rangeProvider = rangeProvider
            ),
            startAxis = VerticalAxis.rememberStart(
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
            getXStep = { 1.0 }
        ),
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp),
        scrollState = scrollState,
        zoomState = zoomState
    )
}

// =========================================================================
// Series slot identifiers (for matching line styles to data series)
// =========================================================================

private sealed class SeriesSlot {
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
}

// =========================================================================
// Data processing helpers
// =========================================================================

private fun processPoints(points: List<GraphDataPoint>, minTimestamp: Long, minX: Double, maxX: Double): List<Pair<Double, Double>> {
    val pts = points.map { timestampToX(it.timestamp, minTimestamp) to it.value }
    return filterToRange(pts, minX, maxX)
}

/** Process IOB treatment overlays: split SMBs by size, extract boluses and extended boluses */
private fun processIobOverlays(data: TreatmentGraphData, minTimestamp: Long, minX: Double, maxX: Double): ProcessedIobOverlays {
    val smbs = data.boluses.filter { it.bolusType == BolusType.SMB }

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
    val steps: Color
)

@Composable
fun rememberSeriesColors(): SeriesColors {
    val iobColor = AapsTheme.generalColors.iobPrediction
    val cobColor = AapsTheme.generalColors.cobPrediction
    val onSurface = MaterialTheme.colorScheme.onSurface
    return remember(iobColor, cobColor, onSurface) {
        SeriesColors(
            iob = iobColor,
            absIob = iobColor,                      // same blue as IOB
            cob = cobColor,
            bgi = Color(0xFF00EEEE),                // cyan (matches @color/bgi)
            deviations = Color(0xFF00EEEE),         // cyan (matches bgiColor attr for deviations)
            sensitivity = onSurface,                // black in light mode, white in dark (matches @color/ratio)
            varSensitivity = onSurface,             // same as sensitivity
            devSlope = Color(0xFF00EEEE),            // cyan
            heartRate = Color(0xFFFFFF66),           // yellow (matches @color/heartRate)
            steps = Color(0xFF66FFB8)               // mint green (matches @color/steps)
        )
    }
}

/** Create a simple line style for a given series type */
fun createSeriesLine(type: SeriesType, colors: SeriesColors): LineCartesianLayer.Line {
    val color = when (type) {
        SeriesType.IOB             -> colors.iob
        SeriesType.ABS_IOB         -> colors.absIob
        SeriesType.COB             -> colors.cob
        SeriesType.BGI             -> colors.bgi
        SeriesType.DEVIATIONS      -> colors.deviations
        SeriesType.SENSITIVITY     -> colors.sensitivity
        SeriesType.VAR_SENSITIVITY -> colors.varSensitivity
        SeriesType.DEV_SLOPE       -> colors.devSlope
        SeriesType.HEART_RATE      -> colors.heartRate
        SeriesType.STEPS           -> colors.steps
    }
    return LineCartesianLayer.Line(
        fill = LineCartesianLayer.LineFill.single(Fill(color)),
        areaFill = LineCartesianLayer.AreaFill.single(
            Fill(Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), Color.Transparent)))
        )
    )
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
                pointConnector = Square
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
                pointConnector = AdaptiveStep
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

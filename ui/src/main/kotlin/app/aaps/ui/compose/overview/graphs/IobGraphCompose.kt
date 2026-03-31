package app.aaps.ui.compose.overview.graphs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.graph.vico.Square
import app.aaps.core.interfaces.overview.graph.BolusType
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
 * IOB (Insulin On Board) Graph using Vico.
 *
 * Dynamic series (order matches lines list built in composition):
 *   - IOB data (conditional)
 *   - Small SMB triangles (conditional)
 *   - Medium SMB triangles (conditional)
 *   - Large SMB triangles (conditional)
 *   - Normal bolus markers (conditional)
 *   - Extended bolus lines (one series per extended bolus, conditional)
 *   - Normalizer (always last — ensures identical maxPointSize, see GraphUtils.kt)
 */
@Composable
fun IobGraphCompose(
    viewModel: GraphViewModel,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState,
    derivedTimeRange: Pair<Long, Long>?,
    modifier: Modifier = Modifier
) {
    // Collect flows independently
    val iobGraphData by viewModel.iobGraphFlow.collectAsStateWithLifecycle()
    val treatmentGraphData by viewModel.treatmentGraphFlow.collectAsStateWithLifecycle()

    val hasRealTimeRange = derivedTimeRange != null
    val (minTimestamp, maxTimestamp) = derivedTimeRange ?: run {
        val now = System.currentTimeMillis()
        val dayAgo = now - 24 * 60 * 60 * 1000L
        dayAgo to now
    }

    val modelProducer = remember { CartesianChartModelProducer() }

    // Colors from theme
    val iobColor = AapsTheme.generalColors.activeInsulinText
    val smbColor = AapsTheme.elementColors.insulin
    val extBolusColor = AapsTheme.elementColors.extendedBolus

    val minX = 0.0
    val maxX = remember(minTimestamp, maxTimestamp) {
        timestampToX(maxTimestamp, minTimestamp)
    }

    val stableTimeRange = remember(minTimestamp / 60000, maxTimestamp / 60000) {
        minTimestamp to maxTimestamp
    }

    // Cache last non-empty treatment data to survive reset() cycles
    val lastTreatmentData = remember { mutableStateOf(treatmentGraphData) }
    if (treatmentGraphData.boluses.isNotEmpty() || treatmentGraphData.extendedBoluses.isNotEmpty()) {
        lastTreatmentData.value = treatmentGraphData
    }

    // Cache processed IOB data — only recomputes when IOB data or time range changes
    val processedIob = remember(iobGraphData, stableTimeRange) {
        if (!hasRealTimeRange) return@remember emptyList()
        val pts = iobGraphData.iob.map { timestampToX(it.timestamp, minTimestamp) to it.value }
        filterToRange(pts, minX, maxX)
    }

    // Cache processed treatment data — only recomputes when treatments or time range change
    val processedTreatments = remember(lastTreatmentData.value, stableTimeRange) {
        if (!hasRealTimeRange) return@remember ProcessedIobTreatments.EMPTY
        val data = lastTreatmentData.value
        val smbs = data.boluses.filter { it.bolusType == BolusType.SMB }

        // Split SMBs into 3 size categories
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
            val pts = normalBoluses.map { timestampToX(it.timestamp, minTimestamp) to it.amount }
            filterToRange(pts, minX, maxX)
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

        ProcessedIobTreatments(
            filterToRange(smallSmbs, minX, maxX),
            filterToRange(mediumSmbs, minX, maxX),
            filterToRange(largeSmbs, minX, maxX),
            bolusFiltered, extBoluses
        )
    }

    // Track which series are present (for dynamic line matching)
    val hasIobDataState = remember { mutableStateOf(false) }
    val hasSmallSmbsState = remember { mutableStateOf(false) }
    val hasMediumSmbsState = remember { mutableStateOf(false) }
    val hasLargeSmbsState = remember { mutableStateOf(false) }
    val hasNormalBolusesState = remember { mutableStateOf(false) }
    val extBolusCountState = remember { mutableIntStateOf(0) }

    LaunchedEffect(processedIob, processedTreatments, maxX) {
        if (!hasRealTimeRange) return@LaunchedEffect

        var hasIob = false
        var hasSmall = false
        var hasMedium = false
        var hasLarge = false
        var hasBoluses = false

        modelProducer.runTransaction {
            lineSeries {
                // IOB data
                if (processedIob.isNotEmpty()) {
                    series(x = processedIob.map { it.first }, y = processedIob.map { it.second })
                    hasIob = true
                }

                // SMBs by size (small, medium, large)
                if (processedTreatments.smallSmbs.isNotEmpty()) {
                    series(x = processedTreatments.smallSmbs.map { it.first }, y = processedTreatments.smallSmbs.map { it.second })
                    hasSmall = true
                }
                if (processedTreatments.mediumSmbs.isNotEmpty()) {
                    series(x = processedTreatments.mediumSmbs.map { it.first }, y = processedTreatments.mediumSmbs.map { it.second })
                    hasMedium = true
                }
                if (processedTreatments.largeSmbs.isNotEmpty()) {
                    series(x = processedTreatments.largeSmbs.map { it.first }, y = processedTreatments.largeSmbs.map { it.second })
                    hasLarge = true
                }

                // Normal boluses
                if (processedTreatments.normalBoluses.isNotEmpty()) {
                    series(x = processedTreatments.normalBoluses.map { it.first }, y = processedTreatments.normalBoluses.map { it.second })
                    hasBoluses = true
                }

                // Extended boluses — one 2-point series per extended bolus
                for ((start, end, amount) in processedTreatments.extBoluses) {
                    series(x = listOf(start, end), y = listOf(amount, amount))
                }

                // Normalizer — ensures identical maxPointSize across all charts (see GraphUtils.kt)
                series(x = normalizerX(maxX), y = NORMALIZER_Y)
            }
        }

        // Update state after transaction
        hasIobDataState.value = hasIob
        hasSmallSmbsState.value = hasSmall
        hasMediumSmbsState.value = hasMedium
        hasLargeSmbsState.value = hasLarge
        hasNormalBolusesState.value = hasBoluses
        extBolusCountState.intValue = processedTreatments.extBoluses.size
    }

    // Time formatter and axis configuration
    val timeFormatter = rememberTimeFormatter(minTimestamp)
    val bottomAxisItemPlacer = rememberBottomAxisItemPlacer(minTimestamp)

    // Line styles
    val iobLine = remember(iobColor) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(iobColor)),
            areaFill = LineCartesianLayer.AreaFill.single(
                Fill(Brush.verticalGradient(listOf(iobColor.copy(alpha = 1f), Color.Transparent)))
            ),
            pointConnector = Square
        )
    }

    val smallSmbLine = remember(smbColor) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
            areaFill = null,
            pointProvider = LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    component = ShapeComponent(fill = Fill(smbColor), shape = TriangleShape),
                    size = 10.dp
                )
            )
        )
    }
    val mediumSmbLine = remember(smbColor) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
            areaFill = null,
            pointProvider = LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    component = ShapeComponent(fill = Fill(smbColor), shape = TriangleShape),
                    size = 16.dp
                )
            )
        )
    }
    val largeSmbLine = remember(smbColor) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
            areaFill = null,
            pointProvider = LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    component = ShapeComponent(fill = Fill(smbColor), shape = TriangleShape),
                    size = 22.dp
                )
            )
        )
    }

    val bolusLabelComponent = remember(smbColor) {
        TextComponent(textStyle = TextStyle(color = smbColor, fontSize = 16.sp))
    }
    val bolusValueFormatter = remember {
        CartesianValueFormatter { _, value, _ -> formatBolusLabel(value) }
    }
    val bolusLine = remember(smbColor, bolusLabelComponent, bolusValueFormatter) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
            areaFill = null,
            pointProvider = LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    component = ShapeComponent(fill = Fill(smbColor), shape = InvertedTriangleShape),
                    size = 22.dp
                )
            ),
            dataLabel = bolusLabelComponent,
            dataLabelPosition = Position.Vertical.Top,
            dataLabelValueFormatter = bolusValueFormatter
        )
    }

    // Extended bolus line style — visible purple line with label
    val extBolusLabelComponent = remember(extBolusColor) {
        TextComponent(textStyle = TextStyle(color = extBolusColor, fontSize = 16.sp))
    }
    val extBolusValueFormatter = remember {
        CartesianValueFormatter { _, value, _ -> formatBolusLabel(value) }
    }
    val extBolusLine = remember(extBolusColor, extBolusLabelComponent, extBolusValueFormatter) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(extBolusColor)),
            areaFill = null,
            dataLabel = extBolusLabelComponent,
            dataLabelPosition = Position.Vertical.Top,
            dataLabelValueFormatter = extBolusValueFormatter
        )
    }

    // Normalizer line — invisible 22dp-point line that equalizes maxPointSize across all charts.
    val normalizerLine = remember { createNormalizerLine() }

    // Build lines list dynamically — MUST match series order from LaunchedEffect
    val hasIobData by hasIobDataState
    val hasSmallSmbs by hasSmallSmbsState
    val hasMediumSmbs by hasMediumSmbsState
    val hasLargeSmbs by hasLargeSmbsState
    val hasNormalBoluses by hasNormalBolusesState
    val extBolusCount by extBolusCountState
    val lines = remember(
        hasIobData, hasSmallSmbs, hasMediumSmbs, hasLargeSmbs,
        hasNormalBoluses, extBolusCount,
        iobLine, smallSmbLine, mediumSmbLine, largeSmbLine, bolusLine, extBolusLine, normalizerLine
    ) {
        buildList {
            if (hasIobData) add(iobLine)
            if (hasSmallSmbs) add(smallSmbLine)
            if (hasMediumSmbs) add(mediumSmbLine)
            if (hasLargeSmbs) add(largeSmbLine)
            if (hasNormalBoluses) add(bolusLine)
            repeat(extBolusCount) { add(extBolusLine) }
            add(normalizerLine)  // Always last
        }
    }

    // Now line decoration
    val nowLineColor = MaterialTheme.colorScheme.onSurface
    val nowTimestamp by viewModel.nowTimestamp.collectAsStateWithLifecycle()
    val nowLine = rememberNowLine(minTimestamp, nowTimestamp, nowLineColor)
    val decorations = remember(nowLine) { listOf(nowLine) }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(lines),
                rangeProvider = remember(maxX) { CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX) }
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

/** Cached processed treatment data for IOB graph — avoids reprocessing when only IOB changes */
private data class ProcessedIobTreatments(
    val smallSmbs: List<Pair<Double, Double>>,
    val mediumSmbs: List<Pair<Double, Double>>,
    val largeSmbs: List<Pair<Double, Double>>,
    val normalBoluses: List<Pair<Double, Double>>,
    val extBoluses: List<Triple<Double, Double, Double>>
) {

    companion object {

        val EMPTY = ProcessedIobTreatments(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}

/** Formats bolus amount: 1.0→"1", 1.2→"1.2", 0.8→".8" (drop leading zero) */
private fun formatBolusLabel(value: Double): String {
    if (value == 0.0) return ""
    val formatted = "%.2f".format(value).trimEnd('0').trimEnd('.').trimEnd(',')
    return if (formatted.startsWith("0.")) formatted.substring(1) else formatted
}

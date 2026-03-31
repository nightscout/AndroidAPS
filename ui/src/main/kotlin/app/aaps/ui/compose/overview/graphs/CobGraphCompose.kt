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
 * COB (Carbs On Board) Graph using Vico.
 *
 * Architecture:
 * - Collects cobGraphFlow independently
 * - Single orange line for COB values
 * - Uses same x-axis coordinate system as BG graph
 * - Same axis configuration as BG graph (Y-axis + X-axis with time labels)
 *
 * X-Axis Range Alignment:
 * - Uses CartesianLayerRangeProvider.fixed() and getXStep = { 1.0 } for alignment with BG graph
 * - See GraphUtils.kt for the 3 pillars of graph alignment
 *
 * Scroll/Zoom:
 * - Receives scroll/zoom state from BG graph (synchronized automatically)
 * - User cannot manually scroll/zoom this graph - it follows BG graph
 *
 * Rendering:
 * - COB line: Orange adaptive line (AdaptiveStep PointConnector)
 * - Steep changes (>45° angle): Step connector (horizontal→vertical, staircase effect)
 * - Gradual changes (≤45° angle): Straight line connector (smooth)
 * - Uses fast ratio calculation (|dy/dx| > 1.0) instead of trigonometry
 * - Gradient fill from semi-transparent orange to transparent
 * - Failover regions: Semi-transparent yellow/amber shaded areas (XRangeDecoration)
 *   Consecutive failover points (within 10 minutes) are grouped into ranges
 */
@Composable
fun CobGraphCompose(
    viewModel: GraphViewModel,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState,
    derivedTimeRange: Pair<Long, Long>?,
    modifier: Modifier = Modifier
) {
    // Collect flows independently
    val cobGraphData by viewModel.cobGraphFlow.collectAsStateWithLifecycle()
    val treatmentGraphData by viewModel.treatmentGraphFlow.collectAsStateWithLifecycle()

    // Use derived time range or fall back to default (last 24 hours)
    val (minTimestamp, maxTimestamp) = derivedTimeRange ?: run {
        val now = System.currentTimeMillis()
        val dayAgo = now - 24 * 60 * 60 * 1000L
        dayAgo to now
    }

    // Single model producer for COB line
    val modelProducer = remember { CartesianChartModelProducer() }

    // Colors from theme
    val cobColor = AapsTheme.generalColors.cobPrediction

    // Calculate x-axis range (must match BG graph for alignment)
    val minX = 0.0
    val maxX = remember(minTimestamp, maxTimestamp) {
        timestampToX(maxTimestamp, minTimestamp)
    }

    // Cache last non-empty treatment data to survive reset() cycles
    val lastTreatmentData = remember { mutableStateOf(treatmentGraphData) }
    if (treatmentGraphData.carbs.isNotEmpty()) {
        lastTreatmentData.value = treatmentGraphData
    }

    // Use remember to cache and only update when time range changes by more than 1 minute
    val stableTimeRange = remember(minTimestamp / 60000, maxTimestamp / 60000) {
        minTimestamp to maxTimestamp
    }

    // Cache processed COB data — only recomputes when COB data or time range changes
    val processedCob = remember(cobGraphData, stableTimeRange) {
        val cobFiltered = run {
            val pts = cobGraphData.cob.map { timestampToX(it.timestamp, minTimestamp) to it.value }
            filterToRange(pts, minX, maxX)
        }
        val failoverFiltered = run {
            val pts = cobGraphData.failOverPoints.map { timestampToX(it.timestamp, minTimestamp) to it.cobValue }
            filterToRange(pts, minX, maxX)
        }
        cobFiltered to failoverFiltered
    }

    // Cache processed carbs data — only recomputes when treatments or time range change
    val processedCarbs = remember(lastTreatmentData.value, stableTimeRange) {
        val pts = lastTreatmentData.value.carbs.map { timestampToX(it.timestamp, minTimestamp) to it.amount }
        filterToRange(pts, minX, maxX)
    }

    // Track which series are currently included (for matching LineProvider)
    val hasCobDataState = remember { mutableStateOf(false) }
    val hasFailoverDataState = remember { mutableStateOf(false) }
    val hasCarbsDataState = remember { mutableStateOf(false) }

    LaunchedEffect(processedCob, processedCarbs, maxX) {
        val (cobFiltered, failoverFiltered) = processedCob

        var hasCobData = false
        var hasFailoverData = false
        var hasCarbsData = false

        modelProducer.runTransaction {
            lineSeries {
                if (cobFiltered.isNotEmpty()) {
                    series(x = cobFiltered.map { it.first }, y = cobFiltered.map { it.second })
                    hasCobData = true
                }
                if (failoverFiltered.isNotEmpty()) {
                    series(x = failoverFiltered.map { it.first }, y = failoverFiltered.map { it.second })
                    hasFailoverData = true
                }
                if (processedCarbs.isNotEmpty()) {
                    series(x = processedCarbs.map { it.first }, y = processedCarbs.map { it.second })
                    hasCarbsData = true
                }
                series(x = normalizerX(maxX), y = NORMALIZER_Y)
            }
        }

        hasCobDataState.value = hasCobData
        hasFailoverDataState.value = hasFailoverData
        hasCarbsDataState.value = hasCarbsData
    }

    // Time formatter and axis configuration
    val timeFormatter = rememberTimeFormatter(minTimestamp)
    val bottomAxisItemPlacer = rememberBottomAxisItemPlacer(minTimestamp)

    // Line style for COB: solid orange line with adaptive step connector
    val cobLine = remember(cobColor) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(cobColor)),
            areaFill = LineCartesianLayer.AreaFill.single(
                Fill(
                    Brush.verticalGradient(
                        listOf(
                            cobColor.copy(alpha = 1f),
                            Color.Transparent
                        )
                    )
                )
            ),
            pointConnector = AdaptiveStep  // Adaptive: step for steep angles (>45°), line for gradual
        )
    }

    // Failover dots style - same color as COB line, dots only, no line
    val failoverDotsLine = remember(cobColor) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
            areaFill = null,
            pointProvider = LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    component = ShapeComponent(
                        fill = Fill(cobColor),
                        shape = CircleShape
                    ),
                    size = 6.dp
                )
            )
        )
    }

    // Carbs marker line style - inverted triangle with data label
    val carbsColor = AapsTheme.elementColors.carbs
    val carbsLabelComponent = remember(carbsColor) {
        TextComponent(textStyle = TextStyle(color = carbsColor, fontSize = 14.sp))
    }
    val carbsValueFormatter = remember {
        CartesianValueFormatter { _, value, _ -> formatCarbsLabel(value) }
    }
    val carbsLine = remember(carbsColor, carbsLabelComponent, carbsValueFormatter) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
            areaFill = null,
            pointProvider = LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    component = ShapeComponent(fill = Fill(carbsColor), shape = InvertedTriangleShape),
                    size = 22.dp
                )
            ),
            dataLabel = carbsLabelComponent,
            dataLabelPosition = Position.Vertical.Top,
            dataLabelValueFormatter = carbsValueFormatter
        )
    }

    // Normalizer line — invisible 22dp-point line that equalizes maxPointSize across all charts.
    // Without this, charts with different point sizes get different xSpacing and unscalableStartPadding,
    // breaking pixel-based scroll/zoom sync. See GraphUtils.kt for details.
    val normalizerLine = remember { createNormalizerLine() }

    // Build lines list dynamically - MUST match series order exactly
    val hasCobData by hasCobDataState
    val hasFailoverData by hasFailoverDataState
    val hasCarbsData by hasCarbsDataState
    val lines = remember(hasCobData, hasFailoverData, hasCarbsData, cobLine, failoverDotsLine, carbsLine, normalizerLine) {
        buildList {
            if (hasCobData) add(cobLine)
            if (hasFailoverData) add(failoverDotsLine)
            if (hasCarbsData) add(carbsLine)
            add(normalizerLine)  // Always last — normalizes layout
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

/** Formats carbs amount: 45.0→"45", 7.5→"7.5", 0.0→"" */
private fun formatCarbsLabel(value: Double): String {
    if (value == 0.0) return ""
    val formatted = "%.1f".format(value).trimEnd('0').trimEnd('.').trimEnd(',')
    return formatted
}

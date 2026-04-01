package app.aaps.ui.compose.overview.graphs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Shared utilities for Vico graphs in AndroidAPS.
 *
 * CRITICAL: All graphs MUST use the same x-coordinate system to ensure proper alignment.
 * This uses whole minutes from minTimestamp to avoid label repetition and precision errors.
 *
 * **Graph Alignment (3 pillars):**
 * All graphs MUST have identical x-axis configuration for pixel-based scroll/zoom sync:
 * 1. `rangeProvider = CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX)` — same X range
 * 2. `getXStep = { 1.0 }` — same xStep (1 minute per unit)
 * 3. Normalizer line ([createNormalizerLine] + [NORMALIZER_X]/[NORMALIZER_Y] dummy series) —
 *    ensures identical maxPointSize across all charts → same xSpacing and unscalable padding
 *
 * **Scroll/Zoom Synchronization:**
 * - BG graph (primary): scrollEnabled = true, zoomEnabled = true
 * - Secondary graphs: scrollEnabled = false, zoomEnabled = false
 * - Pixel-based sync: snapshotFlow { bgScrollState.value to bgZoomState.value }
 * - See OverviewGraphsSection for full implementation
 *
 * **Point Connectors:**
 * - Adaptive step graphs (COB): Use `AdaptiveStep` - steps for steep angles (>45°), lines for gradual
 * - Fixed step graphs (IOB, AbsIOB): Use `Square` PointConnector from core.graph.vico
 * - Smooth graphs (Activity, BGI, Ratio): Use default connector (no pointConnector parameter)
 */

/**
 * Convert timestamp to x-value (whole minutes from minTimestamp).
 *
 * CRITICAL: This is the standard x-coordinate calculation for ALL graphs.
 * - Uses whole minutes (not milliseconds or fractional hours)
 * - Prevents label repetition (Vico increments by 1)
 * - Avoids precision errors with decimals
 *
 * @param timestamp The data point timestamp in milliseconds
 * @param minTimestamp The reference timestamp (start of graph time range)
 * @return X-value in whole minutes from minTimestamp
 */
fun timestampToX(timestamp: Long, minTimestamp: Long): Double =
    ((timestamp - minTimestamp) / 60000).toDouble()

/**
 * Creates a time formatter for X-axis labels showing hours (HH format).
 *
 * @param minTimestamp The reference timestamp for x-value calculation
 * @return CartesianValueFormatter that converts x-values back to time labels
 */
@Composable
fun rememberTimeFormatter(minTimestamp: Long): CartesianValueFormatter {
    return remember(minTimestamp) {
        val dateFormat = SimpleDateFormat("HH", Locale.getDefault())
        CartesianValueFormatter { _, value, _ ->
            val timestamp = minTimestamp + (value * 60000).toLong()
            dateFormat.format(Date(timestamp))
        }
    }
}

/**
 * Creates an item placer for X-axis that shows labels at whole hour intervals.
 *
 * Calculates offset from minTimestamp to align labels with whole hours (e.g., 12:00, 13:00).
 *
 * @param minTimestamp The reference timestamp for calculating hour alignment
 * @return HorizontalAxis.ItemPlacer with 60-minute spacing aligned to whole hours
 */
@OptIn(ExperimentalTime::class)
@Composable
fun rememberBottomAxisItemPlacer(minTimestamp: Long): HorizontalAxis.ItemPlacer {
    return remember(minTimestamp) {
        val instant = Instant.fromEpochMilliseconds(minTimestamp)
        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val minutesIntoHour = localDateTime.minute
        val offsetToNextHour = if (minutesIntoHour == 0) 0 else 60 - minutesIntoHour

        HorizontalAxis.ItemPlacer.aligned(
            spacing = { 60 },  // 60 minutes between labels
            offset = { offsetToNextHour }
        )
    }
}

/**
 * Default zoom level for graphs - shows 6 hours of data (360 minutes).
 */
const val DEFAULT_GRAPH_ZOOM_MINUTES = 360.0

/**
 * Filters data points to only include those within the valid x-axis range.
 *
 * Use this when you have data that might extend beyond the visible time range
 * and you want to exclude out-of-range points from rendering.
 *
 * @param dataPoints List of (x, y) coordinate pairs
 * @param minX Minimum X value for the graph range
 * @param maxX Maximum X value for the graph range
 * @return Filtered and sorted list of (x, y) pairs within [minX, maxX]
 */
fun filterToRange(
    dataPoints: List<Pair<Double, Double>>,
    minX: Double,
    maxX: Double
): List<Pair<Double, Double>> {
    return dataPoints
        .filter { (x, _) -> x in minX..maxX }
        .sortedBy { (x, _) -> x }  // CRITICAL: Sort by x-value for Vico
}

/**
 * Target point size for layout normalization across all synchronized graphs.
 * Must be >= the largest actual point size used in any graph (currently 22dp from IOB SMB/bolus markers).
 *
 * Every graph includes an invisible normalizer line with this point size (via [createNormalizerLine]).
 * This ensures all charts have the same maxPointSize, which makes Vico compute identical:
 * - `xSpacing` (maxPointSize + pointSpacing) → same content width → pixel scroll sync works
 * - `unscalableStartPadding` (maxPointSize / 2) → same content offset → no start alignment shift
 *
 * Without this, each chart's different point sizes cause different layout, breaking pixel-based sync.
 */
val NORMALIZER_POINT_SIZE: Dp = 22.dp

/**
 * Creates an invisible line with [NORMALIZER_POINT_SIZE] transparent points.
 * Include this in every chart's lines list to normalize layout across synchronized graphs.
 */
fun createNormalizerLine(): LineCartesianLayer.Line =
    LineCartesianLayer.Line(
        fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
        areaFill = null,
        pointProvider = LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.Point(
                component = ShapeComponent(fill = Fill(Color.Transparent), shape = CircleShape),
                size = NORMALIZER_POINT_SIZE
            )
        )
    )

/**
 * Y data for the normalizer dummy series. Always add this to every chart's lineSeries block.
 * Two points at y=0, invisible, just to occupy a series slot for the normalizer line.
 */
val NORMALIZER_Y = listOf(0.0, 0.0)

/**
 * X data for the normalizer dummy series spanning the full chart range.
 * Must reach [maxX] so Vico computes the same scrollable content width across all charts.
 * Without this, charts without prediction data (IOB, COB) have shorter scroll extent
 * than the BG chart, causing them to stop following when scrolling into the forecast area.
 */
fun normalizerX(maxX: Double): List<Double> = listOf(0.0, maxX)

/**
 * Triangle shape pointing upward (apex at top center, flat base at bottom).
 *
 * Used for rendering SMB markers on graphs. The triangle sits on the X axis
 * with the point facing up, making it visually distinct from circle dots.
 */
val TriangleShape: Shape = GenericShape { size, _ ->
    val baseHalf = size.width * 0.3f         // Narrow base for sharper triangle
    val cx = size.width / 2f
    moveTo(cx, 0f)                           // Top center (apex)
    lineTo(cx + baseHalf, size.height / 2f)  // Right base
    lineTo(cx - baseHalf, size.height / 2f)  // Left base
    close()
}

/**
 * Inverted triangle shape pointing downward (flat base at top, apex at bottom).
 *
 * Used for rendering bolus markers on graphs. The base sits at the data point's
 * y-coordinate with the apex pointing down.
 */
val InvertedTriangleShape: Shape = GenericShape { size, _ ->
    val baseHalf = size.width * 0.3f         // Narrow base for sharper triangle
    val cx = size.width / 2f
    moveTo(cx - baseHalf, 0f)               // Top left (base)
    lineTo(cx + baseHalf, 0f)               // Top right (base)
    lineTo(cx, size.height / 2f)            // Bottom center (apex)
    close()
}

/**
 * Creates a line for BG prediction series.
 * Transparent connecting line with small filled circle points in the given color.
 * Each prediction type (IOB, COB, UAM, ZT, aCOB) uses a different color.
 */
fun createPredictionLine(color: Color): LineCartesianLayer.Line =
    LineCartesianLayer.Line(
        fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
        areaFill = null,
        pointProvider = LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.Point(
                component = ShapeComponent(
                    fill = Fill(color),
                    shape = CircleShape
                ),
                size = 4.dp
            )
        )
    )

/**
 * "Now" vertical dotted line decoration for Vico charts.
 * Draws a dotted vertical line at the current time position across the full chart height.
 * Shared across all graphs (BG, IOB, COB, Treatment Belt) for consistent "now" indication.
 *
 * @param nowX The x-value for "now" (minutes from minTimestamp, via [timestampToX])
 * @param color The line color
 * @param strokeWidthPx Line stroke width in pixels
 * @param dashLengthPx Dash segment length in pixels
 * @param gapLengthPx Gap between dashes in pixels
 */
class NowLine(
    private val nowX: Double,
    private val color: Color,
    private val strokeWidthPx: Float = 2f,
    private val dashLengthPx: Float = 6f,
    private val gapLengthPx: Float = 4f
) : Decoration {

    override fun drawOverLayers(context: CartesianDrawingContext) {
        with(context) {
            val xStep = ranges.xStep
            if (xStep == 0.0) return

            // Convert x-value to canvas coordinate (mirrors Vico's internal getDrawX logic)
            val canvasX = layerBounds.left +
                layerDimensions.startPadding +
                layerDimensions.xSpacing * ((nowX - ranges.minX) / xStep).toFloat() -
                scroll

            // Skip if outside visible area
            if (canvasX < layerBounds.left || canvasX > layerBounds.right) return

            with(mutableDrawScope) {
                drawLine(
                    color = this@NowLine.color,
                    start = Offset(canvasX, layerBounds.top),
                    end = Offset(canvasX, layerBounds.bottom),
                    strokeWidth = strokeWidthPx,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(dashLengthPx, gapLengthPx), 0f
                    )
                )
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is NowLine &&
            nowX == other.nowX &&
            color == other.color &&
            strokeWidthPx == other.strokeWidthPx

    override fun hashCode(): Int {
        var result = nowX.hashCode()
        result = 31 * result + color.hashCode()
        result = 31 * result + strokeWidthPx.hashCode()
        return result
    }
}

/**
 * Remember a [NowLine] decoration for the current time.
 * @param nowTimestamp current time in millis — pass a ticker value so the line updates periodically
 */
@Composable
fun rememberNowLine(minTimestamp: Long, nowTimestamp: Long, color: Color): NowLine {
    return remember(minTimestamp, nowTimestamp, color) {
        NowLine(nowX = timestampToX(nowTimestamp, minTimestamp), color = color)
    }
}

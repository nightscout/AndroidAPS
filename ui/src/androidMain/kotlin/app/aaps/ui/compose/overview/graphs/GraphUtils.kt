package app.aaps.ui.compose.overview.graphs

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.overview.graph.SeriesType
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared utilities for Vico graphs in AndroidAPS.
 *
 * CRITICAL: All graphs MUST use the same x-coordinate system to ensure proper alignment.
 * This uses whole minutes from minTimestamp to avoid label repetition and precision errors.
 *
 * **Graph Alignment (3 pillars):**
 * All graphs MUST have identical x-axis configuration for pixel-based scroll/zoom sync:
 * 1. `rangeProvider = CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX)` — same X range
 * 2. `getXStep = { _, _, _ -> 1.0 }` — same xStep (1 minute per unit)
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
 * Maximum zoom-in level — never show fewer than this many minutes.
 * Prevents Vico's internal label/constraint math from overflowing at extreme zoom
 * (Compose `Constraints` can't represent the resulting data-label widths → crash).
 */
const val MIN_GRAPH_ZOOM_MINUTES = 30.0

/**
 * Grace period after the last user pan/zoom during which auto-scroll on new BG is suppressed,
 * so the viewport doesn't snap back while the user is examining the graph.
 */
const val INTERACTION_GRACE_MS = 60_000L

/**
 * Fraction of the graph height occupied by the basal overlay.
 *
 * The basal layer lives on its own (end) axis; the rest of the height is left for the primary data.
 * Two coupled formulas derive from this single value and MUST stay in sync — keep them expressed in
 * terms of this constant rather than hard-coded numbers:
 * - Basal axis max  = `maxBasal / BASAL_HEIGHT_FRACTION` → maxBasal plots at this fraction of the height.
 * - Primary axis max = `dataMax / (1 - BASAL_HEIGHT_FRACTION)` → primary (IOB) data fills the remaining
 *   height, reserving the basal fraction at the edge. Used by SecondaryGraphCompose (basal at top);
 *   BgGraphCompose overlays basal at the bottom and has no primary reservation.
 *
 * e.g. 0.5 → basal occupies half the height, the primary data the other half.
 */
const val BASAL_HEIGHT_FRACTION = 0.5

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
 * Y data for the normalizer dummy series. Always add this to every chart's lineModel block.
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

/**
 * Plain (non-Compose-state) holder for the latest visible x-range, written from
 * [VisibleRangeReporter.drawOverLayers] on every draw pass.
 *
 * Deliberately NOT a Compose `State`: writing Compose state synchronously from the draw phase
 * fights with Vico's own gesture-driven scroll/zoom mutations — the resulting invalidate-during-draw
 * starves pinch-zoom gesture recognition (observed: zoom became unresponsive). Callers must poll
 * [value] from a coroutine (e.g. a `LaunchedEffect` with a periodic `delay`) and only then write it
 * into real Compose state.
 */
class VisibleRangeHolder {
    @Volatile
    var value: Pair<Double, Double>? = null
}

/**
 * Reports the currently visible x-range (same "minutes from minTimestamp" unit as [timestampToX])
 * into [holder] on every draw pass. Purely observational — draws nothing, and never touches
 * Compose state directly (see [VisibleRangeHolder]).
 *
 * Inverse of [NowLine]'s x-value-to-canvas transform: canvasX = layerBounds.left + startPadding +
 * xSpacing * ((x - minX) / xStep) - scroll. Solving for x at the left/right edges of layerBounds
 * gives the visible x-range.
 */
class VisibleRangeReporter(
    private val holder: VisibleRangeHolder
) : Decoration {

    override fun drawOverLayers(context: CartesianDrawingContext) {
        with(context) {
            val xStep = ranges.xStep
            val xSpacing = layerDimensions.xSpacing
            if (xStep == 0.0 || xSpacing <= 0f) return

            val visibleMinX = ranges.minX + xStep * (scroll - layerDimensions.startPadding) / xSpacing
            val visibleWidth = xStep * layerBounds.width / xSpacing
            holder.value = visibleMinX to (visibleMinX + visibleWidth)
        }
    }

    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * Remember a [VisibleRangeReporter] decoration that writes the visible x-range into [holder]
 * on every draw pass.
 */
@Composable
fun rememberVisibleRangeReporter(holder: VisibleRangeHolder): VisibleRangeReporter {
    return remember(holder) { VisibleRangeReporter(holder) }
}

// =========================================================================
// Nice-numbers axis scaling ("Nice Numbers for Graph Labels", Paul Heckbert)
// =========================================================================
//
// Vico's default Y-axis item placer divides a fixed [minY, maxY] range into evenly-spaced
// ticks with no rounding, so an arbitrary data-driven range (e.g. 12.39..57.39) produces ugly
// tick labels (12.39, 27.39, 42.39...). These helpers snap axis bounds and tick spacing to
// round numbers (1, 2, 5, 10, 20, 50... times a power of ten) instead.

/** A "nice" axis range: bounds and tick spacing all rounded to 1/2/5/10 x 10^n. */
data class NiceScale(val min: Double, val max: Double, val step: Double)

/**
 * Rounds [range] to a value of the form 1/2/2.5/5/10 x 10^n. [round] chooses the nearest such
 * value (used for tick spacing) vs. always rounding up (used for axis bounds, so the bound never
 * clips inside the data). [range] must be > 0.
 *
 * The round-up variant includes a 2.5 tier (a known variant of Heckbert's original {1,2,5,10}
 * set) to avoid an overly coarse jump between the 2 and 5 tiers — e.g. without it, a BG max of
 * 210 would round all the way up to 300 (skipping straight from a 50-step scale to a 100-step
 * one); with it, 210 rounds to 250 first, only reaching 300 once the value exceeds 250.
 */
private fun niceNum(range: Double, round: Boolean): Double {
    val exponent = floor(log10(range))
    val fraction = range / 10.0.pow(exponent)
    val niceFraction = if (round) {
        when {
            fraction < 1.5 -> 1.0
            fraction < 3.0 -> 2.0
            fraction < 7.0 -> 5.0
            else           -> 10.0
        }
    } else {
        when {
            fraction <= 1.0 -> 1.0
            fraction <= 2.0 -> 2.0
            fraction <= 2.5 -> 2.5
            fraction <= 5.0 -> 5.0
            else            -> 10.0
        }
    }
    return niceFraction * 10.0.pow(exponent)
}

/**
 * Standard nice-ify: snaps [min, max] and the tick spacing to round numbers. Free-range, no
 * zero or pivot anchoring — used for series like VAR_SENSITIVITY and HEART_RATE.
 */
fun niceScale(min: Double, max: Double, maxTickCount: Int = 5): NiceScale {
    val safeMax = if (max > min) max else min + 1.0
    val range = niceNum(safeMax - min, round = false)
    val step = niceNum(range / (maxTickCount - 1), round = true)
    return NiceScale(floor(min / step) * step, ceil(safeMax / step) * step, step)
}

/** Rounds [value] up to the nearest nice number (1/2/5/10 x 10^n). Used for a lone axis bound. */
fun niceUp(value: Double): Double {
    if (value <= 0.0) return 0.0
    return niceNum(value, round = false)
}

/**
 * Rounds [value] (expected negative) further negative to the nearest nice magnitude — e.g.
 * -0.3 -> -0.5, -0.07 -> -0.1 (but -0.05 is already nice and is left unchanged). Used to give a
 * small negative excursion its own clean bound, decoupled from a much larger positive-side scale
 * (see [zeroFloorNiceRange]).
 */
fun niceNegativeSliver(value: Double): Double {
    if (value >= 0.0) return 0.0
    return -niceNum(-value, round = false)
}

/**
 * Minimum half-width for SENSITIVITY's pivot scale (see [niceScaleAroundPivot]'s `minDeviation`
 * param) — below this, snap to a fixed 95%/100%/105% scale instead of nice-ifying a near-zero
 * deviation (e.g. a SENS ratio sitting flat around 100%).
 */
const val SENS_MIN_DEVIATION = 5.0

/**
 * Nice-ify a symmetric deviation around [pivot] (e.g. SENSITIVITY around 100%, DEV_SLOPE
 * around 0), so [pivot] always lands exactly in the middle of the axis. Nice-ifying [min, max]
 * directly (via [niceScale]) would not preserve that centering.
 */
fun niceScaleAroundPivot(min: Double, max: Double, pivot: Double, maxTickCount: Int = 5, minDeviation: Double = 0.0): NiceScale {
    val rawDeviation = maxOf(abs(pivot - min), abs(max - pivot))
    // Below the floor (e.g. SENS sitting flat around 100%): snap to a fixed 3-tick scale
    // (pivot-minDeviation, pivot, pivot+minDeviation) instead of nice-ifying a near-zero range,
    // which would otherwise produce an overly tight, non-round scale (e.g. 99/99.5/100/100.5/101).
    if (minDeviation > 0.0 && rawDeviation <= minDeviation) return NiceScale(pivot - minDeviation, pivot + minDeviation, minDeviation)
    val deviation = if (rawDeviation > 0.0) rawDeviation else 1.0
    val niceDeviation = niceNum(deviation, round = false)
    val step = niceNum(2 * niceDeviation / (maxTickCount - 1), round = true)
    val steppedDeviation = ceil(niceDeviation / step) * step
    return NiceScale(pivot - steppedDeviation, pivot + steppedDeviation, step)
}

/**
 * Series that are 0-floored by default (see [zeroFloorNiceRange]): mostly-positive, allowing a
 * disparity-aware negative excursion without ever centering zero. IOB follows the same rule but
 * isn't included here — its basal-overlay combo graph has its own dedicated range computation
 * (SecondaryGraphCompose's `primaryYMaxResult`) that calls [zeroFloorNiceRange] directly.
 */
val ZERO_FLOOR_SERIES_TYPES = setOf(SeriesType.BGI, SeriesType.DEVIATIONS, SeriesType.ACTIVITY, SeriesType.STEPS, SeriesType.ABS_IOB)

/**
 * Zero-floor axis range, disparity-aware: when the negative excursion is tiny relative to the
 * positive side (ratio >= [disparityRatio]), one shared nice tick spacing across the whole
 * range would make the small negative part look arbitrary — so the two sides are nice-ified
 * independently instead (a small negative sliver just large enough to show the excursion, plus
 * a normal positive-side scale). When the two sides are comparable in magnitude, a single
 * unified nice scale spans the whole range, negative and positive sharing the same tick step.
 * Used for IOB and [ZERO_FLOOR_SERIES_TYPES].
 */
fun zeroFloorNiceRange(dataMin: Double, dataMax: Double, maxTickCount: Int = 5, disparityRatio: Double = 10.0): NiceScale {
    if (dataMin >= 0.0) return niceScale(0.0, dataMax, maxTickCount)
    val absMin = -dataMin
    val ratio = if (absMin > 0.0) dataMax / absMin else Double.MAX_VALUE
    return if (ratio >= disparityRatio) {
        val niceMax = niceUp(dataMax)
        val niceMin = niceNegativeSliver(dataMin)
        val step = niceNum(niceMax / (maxTickCount - 1), round = true)
        NiceScale(niceMin, niceMax, step)
    } else {
        niceScale(dataMin, dataMax, maxTickCount)
    }
}

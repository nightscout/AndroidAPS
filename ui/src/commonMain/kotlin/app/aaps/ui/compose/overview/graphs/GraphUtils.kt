package app.aaps.ui.compose.overview.graphs

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.overview.graph.SeriesType
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Shared utilities for Vico graphs in AAPS.
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
        CartesianValueFormatter { _, value, _ ->
            val timestamp = minTimestamp + (value * 60000).toLong()
            // Always 24 hour, as SimpleDateFormat("HH") was: on an axis a bare 12 hour label would
            // not say which half of the day it belongs to.
            Instant.fromEpochMilliseconds(timestamp)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .hour.toString().padStart(2, '0')
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
 * Calls [onGesture] when the user scrolls or zooms the chart with a pointer: when a finger moves past the
 * touch slop or a second finger comes down, again when the gesture ends (so the [INTERACTION_GRACE_MS]
 * window counts from the last touch), and on a mouse wheel event.
 *
 * Taps do not count. Scroll or zoom changes made by code do not reach here, which is the point: the
 * "use scroll or zoom on BG chart" objective must only be completed by the user.
 *
 * Events are observed in the [PointerEventPass.Initial] pass and never consumed, so the chart still
 * gets every event.
 */
fun Modifier.onScrollOrZoomGesture(onGesture: () -> Unit): Modifier = pointerInput(onGesture) {
    awaitPointerEventScope {
        var downPosition: Offset? = null
        var moved = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            val start = downPosition
            when {
                event.type == PointerEventType.Scroll -> onGesture()

                pressed.isEmpty()                     -> {
                    if (moved) onGesture()
                    downPosition = null
                    moved = false
                }

                start == null                         -> downPosition = pressed.first().position

                !moved && (pressed.size > 1 || (pressed.first().position - start).getDistance() > viewConfiguration.touchSlop) -> {
                    moved = true
                    onGesture()
                }
            }
        }
    }
}

/**
 * Fraction of the graph height occupied by the basal overlay.
 *
 * The basal layer lives on its own (end) axis: basal axis max = `maxBasal / BASAL_HEIGHT_FRACTION`,
 * so the highest basal of the visible window plots at this fraction of the height. BgGraphCompose
 * draws it up from the bottom, SecondaryGraphCompose down from the top.
 *
 * e.g. 0.5 → basal occupies half the height.
 */
const val BASAL_HEIGHT_FRACTION = 0.5

/**
 * Fraction of the IOB/BAS graph height for IOB and the bolus markers, counted from the bottom.
 * IOB axis max = `dataMax / IOB_HEIGHT_FRACTION`.
 *
 * It overlaps the basal band from the top ([BASAL_HEIGHT_FRACTION]), between 50% and 75% of the
 * height. Both meet there only when a high temp basal runs with a high IOB; the loop sets a low or
 * zero temp most of the time, and basal then stays at the top. IOB gets the room in return: with
 * no overlap it had only the lower half. The free quarter above holds the label of a bolus given
 * at the IOB peak.
 */
const val IOB_HEIGHT_FRACTION = 0.75

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
 * Used for a manual bolus on the BG and IOB graphs, and for carbs on the COB graph. The apex
 * sits at the data point's y-coordinate with the base above it, so it points at the value.
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
 * Triangle pointing upward, drawn in the lower half of its box: apex at the data point's
 * y-coordinate, flat base below it.
 *
 * Vico centers a point component on its data point, and both [TriangleShape] and
 * [InvertedTriangleShape] draw in the upper half of the box, so neither can sit below a point.
 * This one does, which is what the carbs half of a treatment marker on the BG graph needs: the
 * bolus points down at the reading from above ([InvertedTriangleShape]) and the carbs point up at
 * it from below, so both tips meet at the moment on the curve.
 * Same proportions as the other two.
 */
val TriangleBelowShape: Shape = GenericShape { size, _ ->
    val baseHalf = size.width * 0.3f         // Narrow base for sharper triangle
    val cx = size.width / 2f
    moveTo(cx, size.height / 2f)             // Top center (apex), at the data point
    lineTo(cx + baseHalf, size.height)       // Right base
    lineTo(cx - baseHalf, size.height)       // Left base
    close()
}

/**
 * Size of a treatment marker (manual bolus, carbs) per [MarkerTier], the same on every graph.
 * The largest must stay within [NORMALIZER_POINT_SIZE].
 */
internal val MarkerTier.markerSize: Dp
    get() = when (this) {
        MarkerTier.SMALL  -> 12.dp
        MarkerTier.MEDIUM -> 17.dp
        MarkerTier.LARGE  -> 22.dp
    }

/**
 * Draws every point of a treatment series in the size of its amount: [tierOf] against the largest
 * amount of its kind in the loaded range, which the chart model carries under [maxAmountKey]. The
 * y of a point is its amount.
 *
 * One series with a size per point, not one series per size: Vico shortens a label only against
 * its neighbours in the same series, so with three series the labels of two close boluses of
 * different size would be drawn over each other.
 */
internal data class TieredPointProvider(
    private val shape: Shape,
    private val color: Color,
    private val maxAmountKey: ExtraStore.Key<Double>
) : LineCartesianLayer.PointProvider {

    private val points = MarkerTier.entries.associateWith { tier ->
        LineCartesianLayer.Point(component = ShapeComponent(fill = Fill(color), shape = shape), size = tier.markerSize)
    }

    override fun getPoint(entry: LineCartesianLayerModel.Entry, extraStore: ExtraStore): LineCartesianLayer.Point =
        points.getValue(tierOf(entry.y, extraStore.getOrNull(maxAmountKey)))

    override fun getLargestPoint(extraStore: ExtraStore): LineCartesianLayer.Point = points.getValue(MarkerTier.LARGE)
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
 * Holder for the latest visible x-range, written from [VisibleRangeReporter.drawOverLayers] on
 * every draw pass.
 *
 * Deliberately NOT a Compose `State`: writing Compose state synchronously from the draw phase
 * fights with Vico's own gesture-driven scroll/zoom mutations — the resulting invalidate-during-draw
 * starves pinch-zoom gesture recognition (observed: zoom became unresponsive). A [StateFlow] write
 * invalidates nothing: it only schedules the collector, which runs after the draw pass and writes
 * real Compose state there. Equal values are not emitted, so a redraw without scroll or zoom (or no
 * draw at all, e.g. the app in background) wakes nobody. Do not replace this with a polling loop.
 */
class VisibleRangeHolder {
    private val _value = MutableStateFlow<Pair<Double, Double>?>(null)
    val value: StateFlow<Pair<Double, Double>?> = _value.asStateFlow()

    fun report(range: Pair<Double, Double>) {
        _value.value = range
    }
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
            holder.report(visibleMinX to (visibleMinX + visibleWidth))
        }
    }

    // No equals/hashCode overrides on purpose. Vico compares decorations to decide what to redraw,
    // and this one must compare by identity: two reporters writing to different holders are not
    // interchangeable. A plain (non-data) class already gets exactly that from Any - reference
    // equality and an identity hash - on every platform. The previous explicit pair said the same
    // thing through System.identityHashCode, which does not exist outside the JVM.
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
// Axis scaling: tight bounds, round labels
// =========================================================================
//
// Every Y range follows its data in the visible window. The bounds and the labels are two
// separate things:
// - a bound is rounded out only a little (see [boundGrid]), so the curve fills its graph,
// - the labels sit on round values, as many as fit the height of the graph (see
//   [NiceVerticalAxisItemPlacer]). A bound does not need to carry a label.
//
// Before, one "nice numbers" scale (Paul Heckbert) did both. The bounds had to be label values,
// and a short graph has room for only a few labels, so a bound could land almost twice as far out
// as the data: an IOB of 2.9 U got an axis up to 4 or even 5 U.

/** A Y range, rounded out from the data. See [niceScale]. */
data class NiceScale(val min: Double, val max: Double)

/**
 * Rounds [value] to 1, 2, 5 or 10 x 10^n: the nearest one when [round], else the smallest one
 * that is not below [value]. [value] must be > 0.
 */
private fun niceNum(value: Double, round: Boolean): Double {
    val exponent = floor(log10(value))
    val fraction = value / 10.0.pow(exponent)
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
            fraction <= 5.0 -> 5.0
            else            -> 10.0
        }
    }
    return niceFraction * 10.0.pow(exponent)
}

/**
 * A bound is rounded out to a multiple of this: about a tenth of [span], as 1, 2 or 5 x 10^n.
 * So it lies at most about a sixth of the span beyond the data, and it still moves in steps, not
 * with every scroll.
 */
private fun boundGrid(span: Double): Double = niceNum(span / 10.0, round = true)

/** A value on the grid that is off by a floating point error must not move one grid step further. */
private const val GRID_EPSILON = 1e-9

private fun ceilToGrid(value: Double, grid: Double): Double = ceil(value / grid - GRID_EPSILON) * grid

private fun floorToGrid(value: Double, grid: Double): Double = floor(value / grid + GRID_EPSILON) * grid

/**
 * [min] and [max] rounded out to [boundGrid] of the span. Free range, no zero or pivot anchor:
 * used for BG and for series like VAR_SENSITIVITY and HEART_RATE. A zero-width range is widened
 * by 1, so the axis never collapses.
 */
fun niceScale(min: Double, max: Double): NiceScale {
    val safeMax = if (max > min) max else min + 1.0
    val grid = boundGrid(safeMax - min)
    return NiceScale(floorToGrid(min, grid), ceilToGrid(safeMax, grid))
}

/** Rounds [value] up to [boundGrid] of itself, for a lone bound. 0 for a value that is not positive. */
fun niceUp(value: Double): Double {
    if (value <= 0.0) return 0.0
    return ceilToGrid(value, boundGrid(value))
}

/**
 * Minimum half-width for SENSITIVITY's pivot scale (see [niceScaleAroundPivot]'s `minDeviation`
 * param) — below this, snap to a fixed 95%/100%/105% scale instead of nice-ifying a near-zero
 * deviation (e.g. a SENS ratio sitting flat around 100%).
 */
const val SENS_MIN_DEVIATION = 5.0

/**
 * A range symmetric around [pivot] (e.g. SENSITIVITY around 100%, DEV_SLOPE around 0), so
 * [pivot] always lands exactly in the middle of the axis. Rounding [min] and [max] on their own
 * (via [niceScale]) would not keep it there.
 */
fun niceScaleAroundPivot(min: Double, max: Double, pivot: Double, minDeviation: Double = 0.0): NiceScale {
    val rawDeviation = maxOf(abs(pivot - min), abs(max - pivot))
    // Below the floor (e.g. SENS sitting flat around 100%): a fixed pivot ± minDeviation
    // (95..105%) instead of a range tight around a near-zero deviation (e.g. 99.5..100.5).
    if (minDeviation > 0.0 && rawDeviation <= minDeviation) return NiceScale(pivot - minDeviation, pivot + minDeviation)
    val deviation = niceUp(if (rawDeviation > 0.0) rawDeviation else 1.0)
    return NiceScale(pivot - deviation, pivot + deviation)
}

/**
 * Series that are 0-floored by default (see [zeroFloorNiceRange]): mostly-positive, allowing a
 * disparity-aware negative excursion without ever centering zero. IOB follows the same rule but
 * isn't included here — its basal-overlay combo graph has its own dedicated range computation
 * (SecondaryGraphCompose's `primaryDataNiceRange`) that calls [zeroFloorNiceRange] directly.
 */
val ZERO_FLOOR_SERIES_TYPES = setOf(SeriesType.BGI, SeriesType.DEVIATIONS, SeriesType.ACTIVITY, SeriesType.STEPS, SeriesType.ABS_IOB)

/**
 * Zero-floor axis range, disparity-aware. When the negative excursion is tiny next to the
 * positive side (ratio >= [disparityRatio]), the two sides are rounded each on its own, so the
 * negative part stays a thin sliver instead of being rounded out to the grid of the whole range.
 * When the two sides are comparable, both are rounded on one grid.
 * Used for IOB and [ZERO_FLOOR_SERIES_TYPES].
 */
fun zeroFloorNiceRange(dataMin: Double, dataMax: Double, disparityRatio: Double = 10.0): NiceScale {
    if (dataMin >= 0.0) return niceScale(0.0, dataMax)
    val absMin = -dataMin
    val ratio = if (absMin > 0.0) dataMax / absMin else Double.MAX_VALUE
    return if (ratio >= disparityRatio) NiceScale(-niceUp(absMin), niceUp(dataMax))
    else niceScale(dataMin, dataMax)
}

/**
 * Labels are at least this many label heights apart, which leaves half a label of free space
 * between two of them. Vico packs labels with no space at all, and on a short graph that reads as
 * one block of numbers.
 */
internal const val AXIS_LABEL_SPACING = 1.5

/**
 * The values to label on an axis from [min] to [max] that is [axisHeight] px high, with labels
 * [labelHeight] px high: the multiples of the smallest 1, 2 or 5 x 10^n step that keeps the labels
 * [AXIS_LABEL_SPACING] label heights apart. A taller graph gets a finer step: 0, 0.5, 1, 1.5 U
 * instead of 0, 1 U.
 *
 * Multiples of the step, not steps counted from [min], so the labels are round numbers whatever the
 * bounds are, and 0 is a label whenever it is on the axis (SENS' 100% too, for any step up to 100).
 * Only the values in [labelMin]..[labelMax] are returned.
 *
 * When that spacing leaves fewer than two labels, they may come closer, down to just touching. One
 * label alone says nothing about the scale. On the 100 dp IOB graph an IOB of 4.4 U would otherwise
 * get only "0": the step is 5, and the labels stop at the IOB top of 4.5.
 */
internal fun niceAxisLabelValues(
    min: Double,
    max: Double,
    axisHeight: Float,
    labelHeight: Float,
    labelMin: Double = Double.NEGATIVE_INFINITY,
    labelMax: Double = Double.POSITIVE_INFINITY
): List<Double> {
    val span = max - min
    if (!(span > 0.0)) return emptyList()
    val low = maxOf(min, labelMin)
    val high = minOf(max, labelMax)
    if (low > high) return emptyList()

    fun labels(spacing: Double): List<Double> {
        // While the axis is measured for its width there may be no height yet. Any round step does
        // there: the widest label is at one end of the axis either way.
        val minStep = if (axisHeight > 0f && labelHeight > 0f) span * labelHeight * spacing / axisHeight else span / 4.0
        val step = niceNum(minStep, round = false)
        val first = ceil(low / step - GRID_EPSILON).toLong()
        val last = floor(high / step + GRID_EPSILON).toLong()
        return (first..last).map { it * step }
    }

    return labels(AXIS_LABEL_SPACING).takeIf { it.size >= 2 } ?: labels(spacing = 1.0)
}

/**
 * Places the labels and grid lines of a vertical axis on [niceAxisLabelValues]. The margins come
 * from Vico's own step placer.
 *
 * [labelMin] and [labelMax] keep labels off a part of the axis the series does not reach: the IOB
 * axis goes on above IOB to leave room for basal (see `iobBasalScale`), and a zero-aligned axis can
 * reach below the series' own floor, where a label would suggest a value it never has.
 *
 * A data class, so two placers with the same limits are equal and the graph does not redraw
 * when nothing changed.
 */
internal data class NiceVerticalAxisItemPlacer(
    private val labelMin: Double = Double.NEGATIVE_INFINITY,
    private val labelMax: Double = Double.POSITIVE_INFINITY
) : VerticalAxis.ItemPlacer by VerticalAxis.ItemPlacer.step() {

    override fun getLabelValues(context: CartesianDrawingContext, axisHeight: Float, maxLabelHeight: Float, position: Axis.Position.Vertical): List<Double> =
        labelValues(context, axisHeight, maxLabelHeight, position)

    override fun getWidthMeasurementLabelValues(context: CartesianMeasuringContext, axisHeight: Float, maxLabelHeight: Float, position: Axis.Position.Vertical): List<Double> =
        labelValues(context, axisHeight, maxLabelHeight, position)

    private fun labelValues(context: CartesianMeasuringContext, axisHeight: Float, maxLabelHeight: Float, position: Axis.Position.Vertical): List<Double> {
        val range = context.ranges.getYRange(position)
        return niceAxisLabelValues(range.minY, range.maxY, axisHeight, maxLabelHeight, labelMin, labelMax)
    }
}

// =========================================================================
// Activity overlay scaling
// =========================================================================

/**
 * Fraction of the space above the zero line that the activity overlay's positive peak may use.
 *
 * The activity curve is never the primary series of a graph — it is always drawn on top of another
 * series (BG or IOB), inside that host's own axis, and its own axis is never shown. So it only
 * needs to stay inside the frame and keep a readable height; it deliberately does not fill the
 * host's full range.
 */
const val ACTIVITY_HEIGHT_FRACTION = 0.8

/**
 * Linear map of an activity curve into a host graph's Y coordinates: activity 0 is drawn at
 * [zeroLevel], and one unit of activity is [scale] host units. See [activityOverlayScale].
 */
data class ActivityOverlayScale(val zeroLevel: Double, val scale: Double) {

    /** Host-axis Y coordinate for an activity [value]. */
    fun map(value: Double) = zeroLevel + value * scale
}

/**
 * Scale factor for drawing the insulin activity curve inside a host graph's axis.
 *
 * The activity overlay has no axis of its own — Vico only has a start and an end vertical axis and
 * both are already taken on every graph that shows activity, so the points must be converted into
 * the host axis' own units before they reach the model. This computes that conversion.
 *
 * The host scale is never modified to fit the activity. The caller passes the room that already
 * exists on each side of [zeroLevel] and the result is sized to fit inside it:
 * - positive side: the nice-ified activity max plots at [heightFraction] of [roomAbove], leaving a
 *   margin below the top of the host graph.
 * - negative side: the nice-ified activity min plots at most at [roomBelow] below [zeroLevel]. No
 *   margin here — [roomBelow] is already small, and a negative excursion is the rare case.
 *
 * One single factor is used for both sides, so the curve stays linear. The smaller of the two
 * limits wins — meaning a deep negative excursion shrinks the whole curve rather than clipping it.
 * A window holding only negative activity is scaled by its negative side alone.
 *
 * When [roomBelow] is 0 the negative side is ignored and the positive side keeps its full scale;
 * the negative tail is then clipped at the host's floor. This is the usual case on the BG graph: the
 * BG axis floor is `dataMin` rounded down to a grid of about a tenth of the range, and `dataMin` is
 * itself floored at the low mark, so the floor lands on the low mark (70/80/90/100 mg/dL, 4.0/5.0
 * mmol/L) or just under it whenever the visible window holds no BG below it. Accepted: the
 * alternative would move the BG scale, which must stay untouched.
 *
 * [activityMin] and [activityMax] are taken from the visible window only, like every other scale in
 * these graphs. They are nice-ified ([zeroFloorNiceRange]) even though no label is ever drawn from
 * them, so the curve's height steps in discrete jumps instead of wobbling on every scroll tick.
 *
 * A window whose activity is zero everywhere returns a zero scale, so the caller draws nothing.
 */
fun activityOverlayScale(
    activityMin: Double,
    activityMax: Double,
    zeroLevel: Double,
    roomAbove: Double,
    roomBelow: Double,
    heightFraction: Double = ACTIVITY_HEIGHT_FRACTION
): ActivityOverlayScale {
    // Activity is zero across the whole window (no insulin on board) — there is no curve to draw.
    // This has to be caught before the nice-ification: zeroFloorNiceRange(0.0, 0.0) comes back as
    // 0..1, because niceScale widens a zero-width range by 1 so that the axis never collapses.
    // That would give a positive scale and draw a flat line on [zeroLevel] — on the BG graph
    // that is the low mark, the bottom edge of the in-range belt, where it reads as a second
    // target line.
    if (activityMin == 0.0 && activityMax == 0.0) return ActivityOverlayScale(zeroLevel, 0.0)
    val nice = zeroFloorNiceRange(activityMin, activityMax)
    val positiveLimit = if (nice.max > 0.0) roomAbove * heightFraction / nice.max else Double.MAX_VALUE
    val negativeLimit = if (nice.min < 0.0 && roomBelow > 0.0) roomBelow / -nice.min else Double.MAX_VALUE
    val scale = minOf(positiveLimit, negativeLimit)
    // Both sides unconstrained means there is nothing to draw (no data, or no room at all on the
    // only side that has data) — a zero scale makes the caller skip the series.
    return ActivityOverlayScale(zeroLevel, if (scale == Double.MAX_VALUE) 0.0 else maxOf(scale, 0.0))
}

package app.aaps.plugins.source.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aaps.plugins.source.Libre3PresoakPoint
import app.aaps.plugins.source.R

/** Sizes of the pre-soak curve. Only this chart uses them, so they are not in the shared spacing. */
private object Libre3CurveSpacing {

    /** Tall enough to read a trend, short enough to leave the card it sits in easy to scan. */
    val height = 140.dp

    /** The trace itself. */
    val lineStroke = 2.dp

    /** A guide line behind the trace. Thin on purpose, so it never competes with the trace. */
    val guideStroke = 1.dp
}

/**
 * The box the pre-soak curve is drawn in: the glucose range on the left, the time range at the
 * bottom.
 *
 * @param lowMgdl glucose at the bottom edge.
 * @param highMgdl glucose at the top edge.
 * @param startMs time at the left edge.
 * @param endMs time at the right edge.
 */
internal data class Libre3PresoakCurveAxis(
    val lowMgdl: Double,
    val highMgdl: Double,
    val startMs: Long,
    val endMs: Long,
)

/**
 * Where each pre-soak reading sits in the drawing box.
 *
 * Pure and without Compose, so the scaling rule is unit tested instead of eyeballed on a screen.
 */
internal object Libre3PresoakCurveAxes {

    /** Bottom guide line. Kept in view even when every reading is above it, so a flat trace has a mark to sit against. */
    const val LOW_GUIDE_MGDL = 70.0

    /** Top guide line, same idea as [LOW_GUIDE_MGDL]. */
    const val HIGH_GUIDE_MGDL = 180.0

    /** Shortest time window drawn. Below it two readings a minute apart would be stretched over the whole width. */
    const val MIN_WINDOW_MS = 60L * 60L * 1000L

    /**
     * Works out the drawing box for a set of readings.
     *
     * The glucose range always holds both guide lines, so the trace keeps its meaning: a flat run
     * at 120 is drawn as a flat line in the middle and not as a jagged line filling the box.
     *
     * @param nowMs the phone clock, which is the right edge. A reading newer than that (clock
     *   change) pushes the edge out rather than falling off the box.
     * @return null when there is nothing to draw.
     */
    fun axisFor(points: List<Libre3PresoakPoint>, nowMs: Long): Libre3PresoakCurveAxis? {
        if (points.isEmpty()) return null
        val values = points.map { it.mgdl }
        val times = points.map { it.timestampMs }
        val end = maxOf(nowMs, times.max())
        return Libre3PresoakCurveAxis(
            lowMgdl = minOf(values.min(), LOW_GUIDE_MGDL),
            highMgdl = maxOf(values.max(), HIGH_GUIDE_MGDL),
            startMs = minOf(times.min(), end - MIN_WINDOW_MS),
            endMs = end,
        )
    }

    /** Where a time sits across the box: 0 at the left edge, 1 at the right one. */
    fun xFraction(axis: Libre3PresoakCurveAxis, timestampMs: Long): Float {
        val span = (axis.endMs - axis.startMs).toDouble()
        if (span <= 0.0) return 1f
        return ((timestampMs - axis.startMs) / span).toFloat().coerceIn(0f, 1f)
    }

    /** Where a reading sits up the box: 0 at the bottom edge, 1 at the top one. */
    fun yFraction(axis: Libre3PresoakCurveAxis, mgdl: Double): Float {
        val span = axis.highMgdl - axis.lowMgdl
        if (span <= 0.0) return 0.5f
        return ((mgdl - axis.lowMgdl) / span).toFloat().coerceIn(0f, 1f)
    }
}

/**
 * The pre-soak readings so far, as a line.
 *
 * A pre-soak asks the user to wait for hours on a sensor whose readings are never published, so
 * without a picture a dead sensor and a healthy one look the same. Drawn with a plain
 * [androidx.compose.foundation.Canvas]: this module has no charting library and the pre-soak is not
 * a reason to add one.
 *
 * @param points the collected readings, oldest first. Empty during warm-up, and empty again after
 *   an app restart — the buffer is in memory only.
 * @param nowMs the phone clock, used as the right edge of the drawing box.
 * @param formatGlucose turns mg/dL into the user's own unit. Passed in, because a screen must not
 *   show mg/dL to somebody who reads mmol/L.
 */
@Composable
fun Libre3PresoakCurve(
    points: List<Libre3PresoakPoint>,
    nowMs: Long,
    formatGlucose: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    val axis = remember(points, nowMs) { Libre3PresoakCurveAxes.axisFor(points, nowMs) }
    val lineColor = MaterialTheme.colorScheme.primary
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    val density = LocalDensity.current
    val lineStrokePx = with(density) { Libre3CurveSpacing.lineStroke.toPx() }
    val guideStrokePx = with(density) { Libre3CurveSpacing.guideStroke.toPx() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Libre3CurveSpacing.height),
        contentAlignment = Alignment.Center,
    ) {
        if (axis == null) {
            // Never a blank box: an empty chart with no words reads as a broken sensor.
            Text(
                text = stringResource(R.string.libre3_presoak_curve_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawGuide(axis, Libre3PresoakCurveAxes.LOW_GUIDE_MGDL, guideColor, guideStrokePx)
                drawGuide(axis, Libre3PresoakCurveAxes.HIGH_GUIDE_MGDL, guideColor, guideStrokePx)
                drawTrace(points, axis, lineColor, lineStrokePx)
            }
            // The two edge values, so the line can be read as glucose and not only as a shape.
            Text(
                text = formatGlucose(axis.highMgdl),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.TopStart),
            )
            Text(
                text = formatGlucose(axis.lowMgdl),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

/** One horizontal guide line at a fixed glucose value. */
private fun DrawScope.drawGuide(
    axis: Libre3PresoakCurveAxis,
    mgdl: Double,
    color: Color,
    strokePx: Float,
) {
    val y = size.height * (1f - Libre3PresoakCurveAxes.yFraction(axis, mgdl))
    drawLine(
        color = color,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = strokePx,
    )
}

/** The readings themselves. A single reading is a dot, because a line needs two points. */
private fun DrawScope.drawTrace(
    points: List<Libre3PresoakPoint>,
    axis: Libre3PresoakCurveAxis,
    color: Color,
    strokePx: Float,
) {
    fun xOf(point: Libre3PresoakPoint) = size.width * Libre3PresoakCurveAxes.xFraction(axis, point.timestampMs)
    fun yOf(point: Libre3PresoakPoint) = size.height * (1f - Libre3PresoakCurveAxes.yFraction(axis, point.mgdl))
    if (points.size == 1) {
        val only = points.first()
        drawCircle(color = color, radius = strokePx, center = Offset(xOf(only), yOf(only)))
        return
    }
    val path = Path()
    points.forEachIndexed { index, point ->
        if (index == 0) path.moveTo(xOf(point), yOf(point)) else path.lineTo(xOf(point), yOf(point))
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

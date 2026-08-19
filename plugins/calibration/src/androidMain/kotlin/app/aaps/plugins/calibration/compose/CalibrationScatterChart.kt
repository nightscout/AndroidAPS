package app.aaps.plugins.calibration.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.CAL
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.plugins.calibration.CalibrationFit
import app.aaps.plugins.calibration.weightFor
import kotlin.math.max
import kotlin.math.min

private const val CHART_MIN_BG = 40f
private const val CHART_MAX_BG = 400f
private const val AXIS_PAD = 20f
private const val MIN_SPAN = 80f

// Used to size the left axis margin so labels never clip, even under large font scales.
// Covers the widest plausible axis tick across both units (e.g. "22.2" mmol/L).
private const val LONGEST_AXIS_LABEL_SAMPLE = "22.2"

@Composable
internal fun CalibrationScatterChart(
    entries: List<CAL>,
    fit: CalibrationFit?,
    selectedEntryId: Long?,
    now: Long,
    glucoseUnit: GlucoseUnit,
    modifier: Modifier = Modifier
) {
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val identityColor = MaterialTheme.colorScheme.outline
    val regressionColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.onSurface
    val selectedColor = MaterialTheme.colorScheme.primary
    val selectedHaloColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val labelArgb = axisColor.toArgb()
    val density = LocalDensity.current

    val labelPaint = remember(labelArgb, density) {
        android.graphics.Paint().apply {
            color = labelArgb
            textSize = with(density) { 11.sp.toPx() }
            isAntiAlias = true
        }
    }

    // Derive left-axis margin from the widest plausible label + gap on both sides, so
    // labels never clip when the user's system font scale is large.
    val leftAxisWidthPx = remember(labelPaint, density) {
        val labelGapPx = with(density) { 4.dp.toPx() }
        labelPaint.measureText(LONGEST_AXIS_LABEL_SAMPLE) + labelGapPx * 2f
    }
    // Bottom axis margin = labelGap above label + full text height + small bottom pad.
    val bottomAxisHeightPx = remember(labelPaint, density) {
        val labelGapPx = with(density) { 4.dp.toPx() }
        val fm = labelPaint.fontMetrics
        labelGapPx + (fm.descent - fm.ascent) + labelGapPx
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        val topPad = 8.dp.toPx()
        val rightPad = 8.dp.toPx()

        val plotOrigin = Offset(leftAxisWidthPx, topPad)
        val plotSize = Size(
            width = size.width - leftAxisWidthPx - rightPad,
            height = size.height - topPad - bottomAxisHeightPx
        )
        if (plotSize.width <= 0f || plotSize.height <= 0f) return@Canvas

        val (axisMin, axisMax) = computeAxisRange(entries)
        val span = axisMax - axisMin

        fun xToPx(v: Float) = plotOrigin.x + ((v - axisMin) / span) * plotSize.width
        fun yToPx(v: Float) = plotOrigin.y + plotSize.height - ((v - axisMin) / span) * plotSize.height

        drawAxes(plotOrigin, plotSize, axisColor)
        drawAxisLabels(plotOrigin, plotSize, axisMin, axisMax, glucoseUnit, labelPaint, density)
        drawIdentityLine(::xToPx, ::yToPx, axisMin, axisMax, identityColor)
        fit?.takeIf { it.isApplicable }?.let {
            drawRegressionLine(::xToPx, ::yToPx, axisMin, axisMax, it, regressionColor)
        }
        drawEntries(entries, selectedEntryId, now, ::xToPx, ::yToPx, dotColor, selectedColor, selectedHaloColor, density)
    }
}

private fun computeAxisRange(entries: List<CAL>): Pair<Float, Float> {
    if (entries.isEmpty()) return 40f to 200f
    var lo = Float.POSITIVE_INFINITY
    var hi = Float.NEGATIVE_INFINITY
    for (e in entries) {
        lo = min(lo, min(e.sensorMgdlAtPairing, e.fingerstickMgdl).toFloat())
        hi = max(hi, max(e.sensorMgdlAtPairing, e.fingerstickMgdl).toFloat())
    }
    var axisMin = (lo - AXIS_PAD).coerceAtLeast(CHART_MIN_BG)
    var axisMax = (hi + AXIS_PAD).coerceAtMost(CHART_MAX_BG)
    if (axisMax - axisMin < MIN_SPAN) {
        val mid = (axisMax + axisMin) / 2f
        axisMin = (mid - MIN_SPAN / 2f).coerceAtLeast(CHART_MIN_BG)
        axisMax = (mid + MIN_SPAN / 2f).coerceAtMost(CHART_MAX_BG)
    }
    return axisMin to axisMax
}

private fun DrawScope.drawAxes(origin: Offset, size: Size, color: Color) {
    val strokeW = 1.dp.toPx()
    drawLine(
        color = color,
        start = origin,
        end = Offset(origin.x, origin.y + size.height),
        strokeWidth = strokeW
    )
    drawLine(
        color = color,
        start = Offset(origin.x, origin.y + size.height),
        end = Offset(origin.x + size.width, origin.y + size.height),
        strokeWidth = strokeW
    )
}

private fun DrawScope.drawAxisLabels(
    origin: Offset,
    size: Size,
    axisMin: Float,
    axisMax: Float,
    glucoseUnit: GlucoseUnit,
    paint: android.graphics.Paint,
    density: Density
) {
    val labelGap = with(density) { 4.dp.toPx() }
    val minLabel = formatAxisLabel(axisMin, glucoseUnit)
    val maxLabel = formatAxisLabel(axisMax, glucoseUnit)
    val canvas = drawContext.canvas.nativeCanvas

    val fm = paint.fontMetrics
    // For text vertically centred on Y: baseline = Y - (ascent + descent) / 2.
    // ascent is negative, descent positive; centerOffset is positive (baseline below Y).
    val centerOffset = -(fm.ascent + fm.descent) / 2f

    // Left axis labels — vertically centred on the top and bottom plot edges.
    val leftTopBaseline = origin.y + centerOffset
    val leftBottomBaseline = origin.y + size.height + centerOffset
    canvas.drawText(maxLabel, origin.x - paint.measureText(maxLabel) - labelGap, leftTopBaseline, paint)
    canvas.drawText(minLabel, origin.x - paint.measureText(minLabel) - labelGap, leftBottomBaseline, paint)

    // Bottom axis labels — top of glyphs sits `labelGap` below the axis line.
    // top-of-text = baseline + ascent (negative), so baseline = axisBottom + labelGap - ascent.
    val bottomBaseline = origin.y + size.height + labelGap - fm.ascent
    canvas.drawText(minLabel, origin.x - paint.measureText(minLabel) / 2f, bottomBaseline, paint)
    canvas.drawText(maxLabel, origin.x + size.width - paint.measureText(maxLabel) / 2f, bottomBaseline, paint)
}

private fun formatAxisLabel(mgdl: Float, glucoseUnit: GlucoseUnit): String = when (glucoseUnit) {
    GlucoseUnit.MGDL -> mgdl.toInt().toString()
    GlucoseUnit.MMOL -> "%.1f".format(mgdl * Constants.MGDL_TO_MMOLL)
}

private fun DrawScope.drawIdentityLine(
    xToPx: (Float) -> Float,
    yToPx: (Float) -> Float,
    axisMin: Float,
    axisMax: Float,
    color: Color
) {
    val dashLen = 8.dp.toPx()
    drawLine(
        color = color.copy(alpha = 0.5f),
        start = Offset(xToPx(axisMin), yToPx(axisMin)),
        end = Offset(xToPx(axisMax), yToPx(axisMax)),
        strokeWidth = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLen, dashLen))
    )
}

private fun DrawScope.drawRegressionLine(
    xToPx: (Float) -> Float,
    yToPx: (Float) -> Float,
    axisMin: Float,
    axisMax: Float,
    fit: CalibrationFit,
    color: Color
) {
    fun model(x: Float): Float = (fit.slope * x + fit.offset).toFloat()
    var x1 = axisMin
    var y1 = model(x1)
    var x2 = axisMax
    var y2 = model(x2)
    if ((y1 !in axisMin..axisMax || y2 !in axisMin..axisMax) && fit.slope != 0.0) {
        val invSlope = 1.0 / fit.slope
        fun xAtY(y: Double): Double = (y - fit.offset) * invSlope
        if (y1 < axisMin) {
            y1 = axisMin; x1 = xAtY(axisMin.toDouble()).toFloat()
        }
        if (y1 > axisMax) {
            y1 = axisMax; x1 = xAtY(axisMax.toDouble()).toFloat()
        }
        if (y2 < axisMin) {
            y2 = axisMin; x2 = xAtY(axisMin.toDouble()).toFloat()
        }
        if (y2 > axisMax) {
            y2 = axisMax; x2 = xAtY(axisMax.toDouble()).toFloat()
        }
    }
    // After clipping, both endpoints can collapse to the same boundary corner; skip the line in that case.
    val degenerate = x1 == x2 && y1 == y2
    if (!degenerate && x1 in axisMin..axisMax && x2 in axisMin..axisMax) {
        drawLine(
            color = color,
            start = Offset(xToPx(x1), yToPx(y1)),
            end = Offset(xToPx(x2), yToPx(y2)),
            strokeWidth = 2.5.dp.toPx()
        )
    }
}

private fun DrawScope.drawEntries(
    entries: List<CAL>,
    selectedEntryId: Long?,
    now: Long,
    xToPx: (Float) -> Float,
    yToPx: (Float) -> Float,
    dotColor: Color,
    selectedColor: Color,
    haloColor: Color,
    density: Density
) {
    val normalRadius = with(density) { 3.5.dp.toPx() }
    val selectedRadius = with(density) { 6.dp.toPx() }
    val haloRadius = with(density) { 11.dp.toPx() }
    val selectedStroke = with(density) { 1.5.dp.toPx() }

    for (e in entries) {
        if (e.id == selectedEntryId) continue
        val w = weightFor(e.timestamp, now).toFloat()
        val alpha = (0.2f + 0.8f * w).coerceIn(0.2f, 1f)
        drawCircle(
            color = dotColor.copy(alpha = alpha),
            radius = normalRadius,
            center = Offset(xToPx(e.sensorMgdlAtPairing.toFloat()), yToPx(e.fingerstickMgdl.toFloat()))
        )
    }
    entries.firstOrNull { it.id == selectedEntryId }?.let { e ->
        val center = Offset(xToPx(e.sensorMgdlAtPairing.toFloat()), yToPx(e.fingerstickMgdl.toFloat()))
        drawCircle(color = haloColor, radius = haloRadius, center = center)
        drawCircle(color = selectedColor, radius = selectedRadius, center = center)
        drawCircle(color = dotColor, radius = selectedRadius, center = center, style = Stroke(width = selectedStroke))
    }
}

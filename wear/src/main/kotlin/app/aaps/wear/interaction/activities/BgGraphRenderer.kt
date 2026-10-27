package app.aaps.wear.interaction.activities

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import app.aaps.core.data.ui.MarkerSizeClasses
import app.aaps.core.data.ui.MarkerTier
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.wear.data.ComplicationData
import app.aaps.wear.interaction.actions.CarbsOrange
import app.aaps.wear.interaction.actions.InsulinBlue
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

internal val BgInRangeColor = Color(0xFF00FF00)
internal val BgHighColor    = Color(0xFFFFFF00)
internal val BgLowColor     = Color(0xFFFF0000)
// Magenta: the only hue on the graph not taken by BG (green/yellow/red), carbs (orange) or insulin (cyan)
internal val CarbsRemovalMagenta = Color(0xFFE040FB)
internal val SecondaryText  = Color(0xFFAAAAAA)
internal val TempTargetColor     = Color(0xFFFDD835)
internal val AutosensTargetColor = Color(0xFF77DD77)

internal val historyHoursCycle = listOf(3, 6, 1)

internal fun bgColor(sgvLevel: Long): Color = when (sgvLevel.toInt()) {
    -1   -> BgLowColor
    1    -> BgHighColor
    else -> BgInRangeColor
}

internal fun formatTtDuration(durationMs: Long, hourUnit: String): String {
    val totalMinutes = (durationMs / 60_000).toInt().coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}$hourUnit ${minutes}'"
        hours > 0                -> "${hours}$hourUnit"
        else                     -> "${minutes}'"
    }
}

internal fun ageColor(ageMs: Long): Color {
    val minutes = ageMs / 60_000
    return when {
        minutes < 4  -> BgInRangeColor
        minutes < 10 -> Color(0xFFFF9800)
        else         -> BgLowColor
    }
}

/** Half width of a triangle as a factor of the BG dot radius, one per [MarkerTier]. */
private class SizeFactors(private val small: Float, private val medium: Float, private val large: Float) {

    fun of(size: MarkerTier?): Float = when (size) {
        MarkerTier.SMALL        -> small
        MarkerTier.LARGE        -> large
        MarkerTier.MEDIUM, null -> medium
    }
}

// The phone's sizes against its 6 dp reading dot: a triangle there fills a box of 10, 16 and 22 dp
// for an SMB on the IOB graph, 12, 17 and 22 dp for a bolus or carbs on the BG graph, with the base
// 0.6 of the box wide and the apex half the box up. Measured in dot radii that is a half width of
// one tenth of the box, and a height of 5/3 of the half width.
// The SMBs are the exception: on the phone they have the IOB graph to themselves, here they share
// the bottom line, so the two larger classes are held back and the largest SMB stays clearly
// smaller than a large bolus.
private val SMB_FACTORS = SizeFactors(small = 1.0f, medium = 1.4f, large = 1.8f)
private val MEAL_FACTORS = SizeFactors(small = 1.2f, medium = 1.7f, large = 2.2f)
private const val TRIANGLE_HEIGHT_RATIO = 5f / 3f

/**
 * Gives the triangle size factor for an amount, so a big SMB, bolus or meal is seen without opening
 * the log. The cuts come from the phone, taken over a whole day, so a treatment has the same size on
 * the phone, the tile and the activity. A phone that does not send them gets the same rule over the
 * treatments the watch has, the last hours. Without any entry everything is medium.
 */
private fun sizeFactor(smallBelow: Double, largeFrom: Double, watchClasses: () -> MarkerSizeClasses?, factors: SizeFactors): (Double) -> Float {
    val classes = if (smallBelow >= 0.0 && largeFrom >= 0.0) MarkerSizeClasses(smallBelow, largeFrom) else watchClasses()
    return { amount -> factors.of(classes?.of(amount)) }
}

/** SMB sizes: thirds from zero to the day's largest, the rule of the phone's IOB graph. */
internal fun smbSizeFactor(treatmentData: EventData.TreatmentData): (Double) -> Float = sizeFactor(
    treatmentData.smbSmallBelow, treatmentData.smbLargeFrom,
    { MarkerSizeClasses.ofMax(treatmentData.boluses.filter { it.isValid && it.isSMB && it.bolus > 0 }.map { it.bolus }) },
    SMB_FACTORS
)

/** Manual bolus sizes: thirds from zero to the day's largest, the rule of the phone's BG graph. */
internal fun bolusSizeFactor(treatmentData: EventData.TreatmentData): (Double) -> Float = sizeFactor(
    treatmentData.bolusSmallBelow, treatmentData.bolusLargeFrom,
    { MarkerSizeClasses.ofMax(treatmentData.boluses.filter { it.isValid && !it.isSMB && it.bolus > 0 }.map { it.bolus }) },
    MEAL_FACTORS
)

/** Carbs sizes, the same rule: a piece of extended carbs comes out small next to the meal it belongs to. */
internal fun carbsSizeFactor(treatmentData: EventData.TreatmentData): (Double) -> Float = sizeFactor(
    treatmentData.carbsSmallBelow, treatmentData.carbsLargeFrom,
    { MarkerSizeClasses.ofMax(treatmentData.boluses.filter { it.isValid && it.carbs > 0 }.map { it.carbs }) },
    MEAL_FACTORS
)

/** Max gap to the nearest reading for a marker to sit on the curve. Three reading intervals, like the phone. */
private const val MARKER_MAX_READING_GAP_MS = 15 * 60_000L

/** A bolus and a carbs entry this close apart are one meal and are drawn at the same height. */
private const val MARKER_PAIR_MS = 5 * 60_000L

/**
 * Glucose at [time], interpolated between the readings on each side of it.
 *
 * Null when the nearest reading is more than [MARKER_MAX_READING_GAP_MS] away: in a sensor gap, or
 * in the future, there is no honest height for a marker, so the caller puts it on the bottom line
 * instead. Across a long gap the near side is used alone, so no height is invented between two
 * far readings. [readings] must be sorted by time.
 */
internal fun interpolatedSgvAt(time: Long, readings: List<EventData.SingleBg>): Float? {
    val before = readings.lastOrNull { it.timeStamp <= time }?.takeIf { time - it.timeStamp <= MARKER_MAX_READING_GAP_MS }
    val after = readings.firstOrNull { it.timeStamp >= time }?.takeIf { it.timeStamp - time <= MARKER_MAX_READING_GAP_MS }
    return when {
        before != null && after != null -> {
            val span = (after.timeStamp - before.timeStamp).toDouble()
            if (span == 0.0) before.sgv.toFloat()
            else (before.sgv + (after.sgv - before.sgv) * ((time - before.timeStamp) / span)).toFloat()
        }

        before != null                  -> before.sgv.toFloat()
        after != null                   -> after.sgv.toFloat()
        else                            -> null
    }
}

/**
 * The time a marker takes its height at: the treatment's own, or that of a partner entry (carbs for
 * a bolus, a bolus for carbs) within [MARKER_PAIR_MS] that came first. Both halves of a meal are
 * then drawn at the same height and line up into one marker instead of a sheared one.
 */
internal fun markerAnchorTime(time: Long, partners: List<Long>): Long {
    val partner = partners.filter { abs(it - time) <= MARKER_PAIR_MS }.minByOrNull { abs(it - time) }
    return if (partner != null && partner < time) partner else time
}

/**
 * [now] is the moment the graph is drawn for; a screen that ticks passes its own clock, so the drawing
 * follows it. [minutesText] writes the minutes left of the "time to eat" timer at its end line; null
 * leaves the minutes out, the line stays.
 */
internal fun DrawScope.renderBgGraph(
    data: ComplicationData,
    historyHours: Int,
    showNowLabel: Boolean = true,
    now: Long = System.currentTimeMillis(),
    minutesText: ((Int) -> String)? = null
) {
    val historyMs = historyHours * 60 * 60 * 1000L
    // Cap predictions at the history span so short ranges (1h) aren't dominated by the prediction area
    val predictionMs = min(90 * 60 * 1000L, historyMs)
    val startTime = now - historyMs
    val endTime = now + predictionMs
    val timeSpan = (endTime - startTime).toFloat()

    val w = size.width
    val h = size.height
    val pad = w * 0.03f
    val drawW = w - 2 * pad
    val drawH = h - 2 * pad
    val bottomReserve = 12.dp.toPx()
    val graphH = drawH - bottomReserve

    val bgData      = data.bgData
    val entries     = data.graphData.entries
    val predictions = data.treatmentData.predictions

    val actualMax = entries
        .filter { it.timeStamp in startTime..now && it.sgv > 0 }
        .maxOfOrNull { it.sgv.toFloat() } ?: 200f
    val predMax = predictions
        .filter { it.timeStamp in (now + 1)..endTime && it.sgv > 0 }
        .maxOfOrNull { it.sgv.toFloat() } ?: 0f
    val dataMax = predMax.coerceAtMost(actualMax + 50f).coerceAtLeast(actualMax)
    val yMin = 40f
    val yMax = maxOf(dataMax, bgData.high.toFloat()) + 30f
    val ySpan = yMax - yMin

    fun timeToX(t: Long) = pad + drawW * ((t - startTime).toFloat() / timeSpan)
    fun sgvToY(sgv: Float) = pad + graphH * (1f - (sgv.coerceIn(yMin, yMax) - yMin) / ySpan)

    val hourMarks = mutableListOf<Long>()
    val cal = Calendar.getInstance().apply {
        timeInMillis = startTime
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.HOUR_OF_DAY, 1)
    }
    while (cal.timeInMillis <= endTime) {
        hourMarks.add(cal.timeInMillis)
        cal.add(Calendar.HOUR_OF_DAY, 1)
    }

    for (hourMs in hourMarks) {
        drawLine(
            color = Color.White.copy(alpha = 0.12f),
            start = Offset(timeToX(hourMs), pad),
            end = Offset(timeToX(hourMs), pad + graphH),
            strokeWidth = 0.5.dp.toPx()
        )
    }

    val nowX = timeToX(now)
    drawLine(
        color = Color.White.copy(alpha = 0.35f),
        start = Offset(nowX, pad),
        end = Offset(nowX, pad + graphH),
        strokeWidth = 1.dp.toPx()
    )

    val hourFormat = SimpleDateFormat("HH", Locale.getDefault())
    drawIntoCanvas { canvas ->
        val hourPaint = Paint().apply {
            color = android.graphics.Color.argb(140, 170, 170, 170)
            textSize = 8.dp.toPx()
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        for (hourMs in hourMarks) {
            canvas.nativeCanvas.drawText(
                hourFormat.format(Date(hourMs)),
                timeToX(hourMs),
                pad + drawH - 4f,
                hourPaint
            )
        }
        if (showNowLabel) {
            val nowPaint = Paint().apply {
                color = android.graphics.Color.argb(178, 255, 255, 255)
                textSize = 8.dp.toPx()
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            val nowLabel = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now))
            canvas.nativeCanvas.drawText(nowLabel, nowX, pad + nowPaint.textSize + 2f, nowPaint)
        }
    }

    // The "time to eat" timer: a dashed line where it rings, with the minutes left at it, one line under
    // the now label so the two never meet. Past the window's end the line stands at the edge
    val eatAt = data.statusData.eatReminderEndsAt
    if (eatAt != null && eatAt > now) {
        val x = timeToX(min(eatAt, endTime))
        drawLine(
            color = CarbsOrange,
            start = Offset(x, pad),
            end = Offset(x, pad + graphH),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
        )
        val minutesLeft = ((eatAt - now + 59_999L) / 60_000L).toInt()
        if (minutesText != null) drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                color = CarbsOrange.toArgb()
                textSize = 8.dp.toPx()
                isAntiAlias = true
                isFakeBoldText = true
            }
            val text = minutesText(minutesLeft)
            val margin = 2.dp.toPx()
            // Right of the line when there is room, else left of it
            val fits = x + margin + paint.measureText(text) <= pad + drawW
            paint.textAlign = if (fits) Paint.Align.LEFT else Paint.Align.RIGHT
            canvas.nativeCanvas.drawText(text, if (fits) x + margin else x - margin, pad + paint.textSize * 2 + 4f, paint)
        }
    }

    val high = bgData.high.toFloat()
    if (high in yMin..yMax) {
        drawLine(
            color = BgHighColor.copy(alpha = 0.35f),
            start = Offset(pad, sgvToY(high)),
            end = Offset(pad + drawW, sgvToY(high)),
            strokeWidth = 1.dp.toPx()
        )
    }

    val low = bgData.low.toFloat()
    if (low in yMin..yMax) {
        drawLine(
            color = BgLowColor.copy(alpha = 0.35f),
            start = Offset(pad, sgvToY(low)),
            end = Offset(pad + drawW, sgvToY(low)),
            strokeWidth = 1.dp.toPx()
        )
    }

    val dotRadius = w * 0.013f

    for (entry in entries) {
        if (entry.timeStamp !in startTime..now) continue
        drawCircle(
            color = bgColor(entry.sgvLevel),
            radius = dotRadius,
            center = Offset(timeToX(entry.timeStamp), sgvToY(entry.sgv.toFloat()))
        )
    }

    val predRadius = dotRadius * 0.6f
    for (pred in predictions) {
        if (pred.timeStamp !in (now + 1)..endTime) continue
        val color = if (pred.color != 0) {
            Color(pred.color).copy(alpha = 0.7f)
        } else {
            bgColor(pred.sgvLevel).copy(alpha = 0.6f)
        }
        drawCircle(
            color = color,
            radius = predRadius,
            center = Offset(timeToX(pred.timeStamp), sgvToY(pred.sgv.toFloat()))
        )
    }

    // Treatments, drawn like the phone's BG graph: a bolus sits above the reading of its moment and
    // points down at it, carbs sit below it and point up, each the way it pushes the glucose. The
    // tips almost touch the reading, with a thin gap, so a meal with its bolus is one marker in two
    // halves. Where no reading is near (a sensor gap, or a future piece of extended carbs) the
    // marker waits on the bottom line until the readings catch up. SMBs stay on the bottom line,
    // pointing up as before: one every few minutes on the curve would turn it into a picket fence.
    val boluses = data.treatmentData.boluses
    val smbFactor = smbSizeFactor(data.treatmentData)
    val bolusFactor = bolusSizeFactor(data.treatmentData)
    val carbsFactor = carbsSizeFactor(data.treatmentData)
    // From the reading's centre to the tip: the tip stops just outside the dot's edge
    val gap = dotRadius + 1.dp.toPx()
    val bottom = pad + graphH

    // The readings the heights come from: the dots drawn above, in time order
    val readings = entries.filter { it.timeStamp in startTime..now && it.sgv > 0 }.sortedBy { it.timeStamp }
    val bolusTimes = boluses.filter { it.isValid && !it.isSMB && it.bolus > 0 }.map { it.date }
    val carbsTimes = boluses.filter { it.isValid && it.carbs != 0.0 }.map { it.date }

    /** A triangle with its tip at [apexY] and its body above it: insulin, pointing down. */
    fun drawPointingDown(x: Float, apexY: Float, halfWidth: Float, height: Float, color: Color) = drawPath(
        Path().apply {
            moveTo(x, apexY)
            lineTo(x - halfWidth, apexY - height)
            lineTo(x + halfWidth, apexY - height)
            close()
        },
        color
    )

    /** A triangle with its tip at [apexY] and its body below it: carbs and SMBs, pointing up. */
    fun drawPointingUp(x: Float, apexY: Float, halfWidth: Float, height: Float, color: Color) = drawPath(
        Path().apply {
            moveTo(x, apexY)
            lineTo(x - halfWidth, apexY + height)
            lineTo(x + halfWidth, apexY + height)
            close()
        },
        color
    )

    for (treatment in boluses) {
        if (!treatment.isValid || treatment.date !in startTime..endTime) continue
        val x = timeToX(treatment.date)
        when {
            treatment.isSMB         -> {
                if (treatment.bolus <= 0) continue
                val halfWidth = dotRadius * smbFactor(treatment.bolus)
                val height = halfWidth * TRIANGLE_HEIGHT_RATIO
                // Stands on the bottom line, base down, as before
                drawPointingUp(x, bottom - height, halfWidth, height, InsulinBlue.copy(alpha = 0.85f))
            }

            treatment.bolus > 0     -> {
                val halfWidth = dotRadius * bolusFactor(treatment.bolus)
                // The tip points at the reading from above. With no reading near, it touches the bottom line.
                val apexY = interpolatedSgvAt(markerAnchorTime(treatment.date, carbsTimes), readings)?.let { sgvToY(it) - gap } ?: bottom
                drawPointingDown(x, apexY, halfWidth, halfWidth * TRIANGLE_HEIGHT_RATIO, InsulinBlue)
            }

            // carbs != 0: negative entries (COB removal) are drawn too, in magenta to tell them apart
            treatment.carbs != 0.0  -> {
                val halfWidth = dotRadius * carbsFactor(abs(treatment.carbs))
                val height = halfWidth * TRIANGLE_HEIGHT_RATIO
                val color = if (treatment.carbs < 0) CarbsRemovalMagenta else CarbsOrange
                // The tip points at the reading from below. With no reading near, the marker stands
                // on the bottom line with its base on it.
                val apexY = interpolatedSgvAt(markerAnchorTime(treatment.date, bolusTimes), readings)?.let { sgvToY(it) + gap } ?: (bottom - height)
                drawPointingUp(x, apexY, halfWidth, height, color)
            }
        }
    }
}

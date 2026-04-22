package app.aaps.wear.interaction.activities

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import app.aaps.wear.data.ComplicationData
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

internal val BgInRangeColor = Color(0xFF00FF00)
internal val BgHighColor    = Color(0xFFFFFF00)
internal val BgLowColor     = Color(0xFFFF0000)
internal val IobColor       = Color(0xFF1E88E5)
internal val CarbsColor     = Color(0xFFFF6D00)
internal val BasalColor     = Color(0xFF90CAF9)
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

internal fun DrawScope.renderBgGraph(data: ComplicationData, historyHours: Int) {
    val now = System.currentTimeMillis()
    val historyMs = historyHours * 60 * 60 * 1000L
    val predictionMs = 90 * 60 * 1000L
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

    val nowLabel = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now))
    val hourFormat = SimpleDateFormat("HH", Locale.getDefault())
    drawIntoCanvas { canvas ->
        val hourPaint = Paint().apply {
            color = android.graphics.Color.argb(140, 170, 170, 170)
            textSize = 8.dp.toPx()
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val nowPaint = Paint().apply {
            color = android.graphics.Color.argb(178, 255, 255, 255)
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
        canvas.nativeCanvas.drawText(nowLabel, nowX, pad + nowPaint.textSize + 2f, nowPaint)
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

    val boluses = data.treatmentData.boluses
    val triSize   = dotRadius * 1.4f
    val triHeight = dotRadius * 2.4f
    val smbTriSize = dotRadius * 1.2f

    val bottom = pad + graphH

    for (treatment in boluses) {
        if (!treatment.isValid || treatment.isSMB) continue
        if (treatment.carbs <= 0 || treatment.date !in startTime..endTime) continue
        val x = timeToX(treatment.date)
        drawPath(
            Path().apply {
                moveTo(x, bottom - triHeight)
                lineTo(x - triSize, bottom)
                lineTo(x + triSize, bottom)
                close()
            },
            CarbsColor
        )
    }

    for (treatment in boluses) {
        if (!treatment.isValid) continue
        if (treatment.bolus <= 0 || treatment.date !in startTime..endTime) continue
        val x = timeToX(treatment.date)
        if (treatment.isSMB) {
            val nearestEntry = entries.filter { it.sgv > 0 }
                .minByOrNull { abs(it.timeStamp - treatment.date) }
            if (nearestEntry != null && abs(nearestEntry.timeStamp - treatment.date) < 10 * 60_000L) {
                val bgY = sgvToY(nearestEntry.sgv.toFloat())
                val tipY = bgY - dotRadius * 1.5f
                drawPath(
                    Path().apply {
                        moveTo(x, tipY)
                        lineTo(x - smbTriSize, tipY - smbTriSize * 1.8f)
                        lineTo(x + smbTriSize, tipY - smbTriSize * 1.8f)
                        close()
                    },
                    IobColor.copy(alpha = 0.85f)
                )
            }
        } else {
            drawPath(
                Path().apply {
                    moveTo(x, bottom)
                    lineTo(x - triSize, bottom - triHeight)
                    lineTo(x + triSize, bottom - triHeight)
                    close()
                },
                IobColor
            )
        }
    }
}

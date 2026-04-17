package app.aaps.wear.interaction.activities

import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.AnchorType
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.curvedText
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.wear.R
import app.aaps.wear.data.ComplicationData
import app.aaps.wear.data.ComplicationDataRepository
import app.aaps.wear.interaction.menus.MainMenuActivity
import app.aaps.wear.interaction.utils.DisplayFormat
import dagger.android.AndroidInjection
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// Colors matching CustomWatchFace defaults
private val BgInRangeColor = Color(0xFF00FF00)
private val BgHighColor    = Color(0xFFFFFF00)
private val BgLowColor     = Color(0xFFFF0000)
private val IobColor       = Color(0xFF1E88E5)
private val CarbsColor     = Color(0xFFFF6D00)
private val BasalColor      = Color(0xFF90CAF9)
private val SecondaryText   = Color(0xFFAAAAAA)
private val TempTargetColor = Color(0xFFFDD835)

private val historyHoursCycle = listOf(3, 6, 9, 12, 24, 1)

private fun bgColor(sgvLevel: Long): Color = when (sgvLevel.toInt()) {
    -1   -> BgLowColor
    1    -> BgHighColor
    else -> BgInRangeColor
}

private fun formatTtDuration(durationMs: Long, hourUnit: String): String {
    val totalMinutes = (durationMs / 60_000).toInt().coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}$hourUnit ${minutes}'"
        hours > 0                -> "${hours}$hourUnit"
        else                     -> "${minutes}'"
    }
}

private fun ageColor(ageMs: Long): Color {
    val minutes = ageMs / 60_000
    return when {
        minutes < 4  -> BgInRangeColor
        minutes < 10 -> Color(0xFFFF9800)
        else         -> BgLowColor
    }
}

class BgGraphActivity : AppCompatActivity() {

    @Inject lateinit var complicationDataRepository: ComplicationDataRepository
    @Inject lateinit var displayFormat: DisplayFormat
    @Inject lateinit var aapsLogger: AAPSLogger

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BgGraphScreen(
                    repository = complicationDataRepository,
                    displayFormat = displayFormat
                )
            }
        }
    }
}

@Composable
private fun BgGraphScreen(repository: ComplicationDataRepository, displayFormat: DisplayFormat) {
    val data by repository.complicationData.collectAsState(initial = ComplicationData())
    var windowIdx by remember { mutableIntStateOf(0) }
    val historyHours = historyHoursCycle[windowIdx]

    val bgData = data.bgData
    val statusData = data.statusData
    val now = System.currentTimeMillis()
    val ageMs = now - bgData.timeStamp
    val ageMin = ageMs / 60_000
    val hourUnit = stringResource(R.string.hour_short)
    val minUnit = stringResource(R.string.minute_short)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        CurvedLayout(anchor = 270f, anchorType = AnchorType.Center) {
            curvedText(
                text = "${historyHours}$hourUnit",
                color = SecondaryText,
                fontSize = 11.sp
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val context = LocalContext.current

            // Top area: BG + stats — double-tap opens main menu
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            context.startActivity(
                                Intent(context, MainMenuActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                                }
                            )
                        })
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                // BG value + trend + delta + age
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${bgData.sgvString}${bgData.slopeArrow}\uFE0E",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = bgColor(bgData.sgvLevel)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = bgData.delta,
                            fontSize = 12.sp,
                            color = SecondaryText
                        )
                        Text(
                            text = "${ageMin}$minUnit",
                            fontSize = 12.sp,
                            color = ageColor(ageMs)
                        )
                    }
                }

                // IOB / COB / Basal / Target
                val hasTT = statusData.tempTargetDuration >= 0
                val targetText = if (hasTT) {
                    "${statusData.tempTarget} (${formatTtDuration(statusData.tempTargetDuration, hourUnit)})"
                } else {
                    statusData.tempTarget
                }
                val insulinUnit = stringResource(R.string.insulin_unit_short)
                val basalText = displayFormat.basalRateSymbol().trimEnd() + statusData.currentBasal.replaceFirst(" ", "")
                val combinedLen = "${statusData.iobSum}$insulinUnit".length +
                    statusData.cob.length +
                    basalText.length +
                    targetText.length
                val statsFontSize = if (combinedLen > 30) 11.sp else 12.sp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "${statusData.iobSum}$insulinUnit", fontSize = statsFontSize, color = IobColor)
                    Text(text = statusData.cob, fontSize = statsFontSize, color = CarbsColor)
                    Text(text = basalText, fontSize = statsFontSize, color = BasalColor)
                    Text(text = targetText, fontSize = statsFontSize, color = if (hasTT) TempTargetColor else SecondaryText)
                }
            }

            // Graph — tap cycles history window
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable { windowIdx = (windowIdx + 1) % historyHoursCycle.size }
            ) {
                renderBgGraph(data, historyHours)
            }
        }
    }
}

private fun DrawScope.renderBgGraph(data: ComplicationData, historyHours: Int) {
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

    val bgData      = data.bgData
    val entries     = data.graphData.entries
    val predictions = data.treatmentData.predictions

    // Dynamic Y range: top adapts to data, bottom fixed at 40
    val visibleSgvValues = buildList {
        entries.forEach { if (it.timeStamp in startTime..now) add(it.sgv.toFloat()) }
        predictions.forEach { if (it.timeStamp in (now + 1)..endTime) add(it.sgv.toFloat()) }
    }.filter { it > 0 }
    val dataMax = visibleSgvValues.maxOrNull() ?: 200f
    val yMin = 40f
    val yMax = maxOf(dataMax, bgData.high.toFloat()) + 30f
    val ySpan = yMax - yMin

    fun timeToX(t: Long) = pad + drawW * ((t - startTime).toFloat() / timeSpan)
    fun sgvToY(sgv: Float) = pad + drawH * (1f - (sgv.coerceIn(yMin, yMax) - yMin) / ySpan)

    // Collect hour marks for grid lines and labels
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

    // Hour grid lines
    for (hourMs in hourMarks) {
        drawLine(
            color = Color.White.copy(alpha = 0.12f),
            start = Offset(timeToX(hourMs), pad),
            end = Offset(timeToX(hourMs), pad + drawH),
            strokeWidth = 0.5.dp.toPx()
        )
    }

    // "Now" line
    val nowX = timeToX(now)
    drawLine(
        color = Color.White.copy(alpha = 0.35f),
        start = Offset(nowX, pad),
        end = Offset(nowX, pad + drawH),
        strokeWidth = 1.dp.toPx()
    )

    // Text labels: hour numbers at bottom, current time at top of "now" line
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
        val labelStep = if (historyHours >= 18) 2 else 1
        for (hourMs in hourMarks) {
            val hour = Calendar.getInstance().apply { timeInMillis = hourMs }.get(Calendar.HOUR_OF_DAY)
            if (hour % labelStep != 0) continue
            canvas.nativeCanvas.drawText(
                hourFormat.format(Date(hourMs)),
                timeToX(hourMs),
                pad + drawH - 4f,
                hourPaint
            )
        }
        canvas.nativeCanvas.drawText(nowLabel, nowX, pad + nowPaint.textSize + 2f, nowPaint)
    }

    // High/low threshold lines
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

    val dotRadius = w * 0.016f

    // BG history dots
    for (entry in entries) {
        if (entry.timeStamp < startTime || entry.timeStamp > now) continue
        drawCircle(
            color = bgColor(entry.sgvLevel),
            radius = dotRadius,
            center = Offset(timeToX(entry.timeStamp), sgvToY(entry.sgv.toFloat()))
        )
    }

    // Prediction dots (slightly smaller)
    val predRadius = dotRadius * 0.8f
    for (pred in predictions) {
        if (pred.timeStamp <= now || pred.timeStamp > endTime) continue
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
}

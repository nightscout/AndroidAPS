package app.aaps.wear.interaction.activities

import android.graphics.Paint
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
private val BasalColor     = Color(0xFF90CAF9)
private val SecondaryText  = Color(0xFFAAAAAA)

private val historyHoursCycle = listOf(3, 4, 5, 6, 7, 8)

private fun bgColor(sgvLevel: Long): Color = when (sgvLevel.toInt()) {
    -1   -> BgLowColor
    1    -> BgHighColor
    else -> BgInRangeColor
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
    @Inject lateinit var aapsLogger: AAPSLogger

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BgGraphScreen(repository = complicationDataRepository)
            }
        }
    }
}

@Composable
private fun BgGraphScreen(repository: ComplicationDataRepository) {
    val data by repository.complicationData.collectAsState(initial = ComplicationData())
    var windowIdx by remember { mutableIntStateOf(0) }
    val historyHours = historyHoursCycle[windowIdx]

    val bgData = data.bgData
    val statusData = data.statusData
    val now = System.currentTimeMillis()
    val ageMs = now - bgData.timeStamp
    val ageMin = ageMs / 60_000

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        CurvedLayout(anchor = 270f, anchorType = AnchorType.Center) {
            curvedText(
                text = "${historyHours}h",
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
                        text = "${ageMin}m",
                        fontSize = 12.sp,
                        color = ageColor(ageMs)
                    )
                }
            }

            // IOB / COB / Basal above graph so they aren't clipped by circular bezel
            val insulinUnit = stringResource(R.string.insulin_unit_short)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = "IOB ${statusData.iobSum}$insulinUnit",
                    fontSize = 11.sp,
                    color = IobColor,
                    textAlign = TextAlign.Center
                )
                Text(
                    modifier = Modifier.weight(1f),
                    text = "COB ${statusData.cob}",
                    fontSize = 11.sp,
                    color = CarbsColor,
                    textAlign = TextAlign.Center
                )
                Text(
                    modifier = Modifier.weight(1f),
                    text = statusData.currentBasal,
                    fontSize = 11.sp,
                    color = BasalColor,
                    textAlign = TextAlign.Center
                )
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
    val predictionMs = 30 * 60 * 1000L
    val startTime = now - historyMs
    val endTime = now + predictionMs
    val timeSpan = (endTime - startTime).toFloat()

    val w = size.width
    val h = size.height
    val pad = w * 0.03f
    val drawW = w - 2 * pad
    val drawH = h - 2 * pad

    val yMin = 40f
    val yMax = 360f
    val ySpan = yMax - yMin

    fun timeToX(t: Long) = pad + drawW * ((t - startTime).toFloat() / timeSpan)
    fun sgvToY(sgv: Float) = pad + drawH * (1f - (sgv.coerceIn(yMin, yMax) - yMin) / ySpan)

    val bgData      = data.bgData
    val entries     = data.graphData.entries
    val predictions = data.treatmentData.predictions

    // Hour grid lines (subtle)
    val cal = Calendar.getInstance().apply {
        timeInMillis = startTime
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.HOUR_OF_DAY, 1)
    }
    while (cal.timeInMillis <= endTime) {
        val x = timeToX(cal.timeInMillis)
        drawLine(
            color = Color.White.copy(alpha = 0.12f),
            start = Offset(x, pad),
            end = Offset(x, pad + drawH),
            strokeWidth = 0.5.dp.toPx()
        )
        cal.add(Calendar.HOUR_OF_DAY, 1)
    }

    // "Now" line with current time label
    val nowX = timeToX(now)
    drawLine(
        color = Color.White.copy(alpha = 0.35f),
        start = Offset(nowX, pad),
        end = Offset(nowX, pad + drawH),
        strokeWidth = 1.dp.toPx()
    )
    val nowLabel = SimpleDateFormat("H:mm", Locale.getDefault()).format(Date(now))
    drawIntoCanvas { canvas ->
        val textPaint = Paint().apply {
            color = android.graphics.Color.argb(178, 255, 255, 255)
            textSize = 8.dp.toPx()
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.nativeCanvas.drawText(nowLabel, nowX, pad + textPaint.textSize + 2f, textPaint)
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

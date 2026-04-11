package app.aaps.ui.compose.overview

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aaps.core.ui.compose.LocalDateUtil
import kotlinx.coroutines.delay

@Composable
fun LargeClock(
    bgTimestamp: Long?,
    modifier: Modifier = Modifier
) {
    val dateUtil = LocalDateUtil.current
    var timeText by remember { mutableStateOf(dateUtil.timeString()) }
    var agoText by remember(bgTimestamp) { mutableStateOf(dateUtil.minAgoShort(bgTimestamp)) }

    LaunchedEffect(bgTimestamp) {
        while (true) {
            val now = System.currentTimeMillis()
            val msToNextMinute = 60_000L - (now % 60_000L)
            delay(msToNextMinute)
            timeText = dateUtil.timeString()
            agoText = dateUtil.minAgoShort(bgTimestamp)
        }
    }

    LargeClockText(timeText = timeText, agoText = agoText, modifier = modifier)
}

@Composable
private fun LargeClockText(
    timeText: String,
    agoText: String,
    modifier: Modifier = Modifier
) {
    BasicText(
        text = if (agoText.isNotEmpty()) "$timeText$agoText" else timeText,
        style = TextStyle(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        ),
        autoSize = TextAutoSize.StepBased(
            minFontSize = 20.sp,
            maxFontSize = 56.sp,
            stepSize = 2.sp
        ),
        maxLines = 1,
        modifier = modifier.padding(horizontal = 8.dp)
    )
}

@Preview(showBackground = true, widthDp = 220)
@Composable
private fun LargeClockPreview() {
    MaterialTheme {
        LargeClockText(timeText = "14:27", agoText = "(-5)")
    }
}

@Preview(showBackground = true, widthDp = 220)
@Composable
private fun LargeClockNoAgoPreview() {
    MaterialTheme {
        LargeClockText(timeText = "14:27", agoText = "")
    }
}

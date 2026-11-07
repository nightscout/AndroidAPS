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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aaps.core.ui.compose.LocalDateUtil
import kotlinx.coroutines.delay

/**
 * @param showBgAge adds the age of the BG reading after the time, "21:33(-5)". Off where the BG
 *   circle next to the clock already shows the age.
 */
@Composable
fun LargeClock(
    bgTimestamp: Long?,
    modifier: Modifier = Modifier,
    maxFontSize: TextUnit = 56.sp,
    showBgAge: Boolean = true
) {
    val dateUtil = LocalDateUtil.current
    val bgAge = { if (showBgAge) dateUtil.minAgoShort(bgTimestamp) else "" }
    var timeText by remember { mutableStateOf(dateUtil.timeString()) }
    var agoText by remember(bgTimestamp, showBgAge) { mutableStateOf(bgAge()) }

    LaunchedEffect(bgTimestamp, showBgAge) {
        while (true) {
            val now = dateUtil.now()
            val msToNextMinute = 60_000L - (now % 60_000L)
            delay(msToNextMinute)
            timeText = dateUtil.timeString()
            agoText = bgAge()
        }
    }

    LargeClockText(timeText = timeText, agoText = agoText, maxFontSize = maxFontSize, modifier = modifier)
}

/**
 * @see LargeClockPreview
 * @see LargeClockNoAgoPreview
 */
@Composable
internal fun LargeClockText(
    timeText: String,
    agoText: String,
    maxFontSize: TextUnit = 56.sp,
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
            maxFontSize = maxFontSize,
            stepSize = 2.sp
        ),
        maxLines = 1,
        modifier = modifier.padding(horizontal = 8.dp)
    )
}

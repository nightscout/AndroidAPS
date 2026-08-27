package app.aaps.ui.compose.overview

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true, widthDp = 220)
@Composable
internal fun LargeClockPreview() {
    MaterialTheme {
        LargeClockText(timeText = "14:27", agoText = "(-5)")
    }
}

@Preview(showBackground = true, widthDp = 220)
@Composable
internal fun LargeClockNoAgoPreview() {
    MaterialTheme {
        LargeClockText(timeText = "14:27", agoText = "")
    }
}

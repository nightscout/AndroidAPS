package app.aaps.pump.dana.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true, name = "User Options")
@Composable
internal fun DanaUserOptionsPreview() {
    MaterialTheme {
        DanaUserOptionsContent(
            state = UserOptionsUiState(
                timeFormat24h = true,
                buttonScroll = false,
                beepOnPress = true,
                alarmMode = 1,
                screenTimeout = 15,
                backlight = 5,
                glucoseUnitMmol = false,
                shutdownHour = 0,
                lowReservoir = 20,
                minBacklight = 1
            )
        )
    }
}

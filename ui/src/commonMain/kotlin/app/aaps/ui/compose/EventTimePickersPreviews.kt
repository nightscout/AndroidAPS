package app.aaps.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

// A fixed instant: a preview must render the same every time, and there is no clock provided to it.
private const val PREVIEW_NOW = 1_780_000_000_000L

@Preview(showBackground = true)
@Composable
internal fun EventDatePickerPreview() {
    MaterialTheme {
        EventDatePicker(
            eventTimeMillis = PREVIEW_NOW,
            onEventTimeChanged = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun EventTimePickerPreview() {
    MaterialTheme {
        EventTimePicker(
            eventTimeMillis = PREVIEW_NOW,
            onEventTimeChanged = {},
            onDismiss = {}
        )
    }
}

package app.aaps.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun EventDatePickerPreview() {
    MaterialTheme {
        EventDatePicker(
            eventTimeMillis = System.currentTimeMillis(),
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
            eventTimeMillis = System.currentTimeMillis(),
            onEventTimeChanged = {},
            onDismiss = {}
        )
    }
}

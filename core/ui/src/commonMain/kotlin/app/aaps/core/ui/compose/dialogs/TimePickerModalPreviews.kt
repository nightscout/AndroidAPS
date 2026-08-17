package app.aaps.core.ui.compose.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun TimePickerModalPreview() {
    MaterialTheme {
        TimePickerModal(
            onTimeSelected = { _, _ -> },
            onDismiss = {},
            initialHour = 14,
            initialMinute = 30
        )
    }
}

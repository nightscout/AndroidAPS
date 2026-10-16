package app.aaps.core.ui.compose.pickers

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun WeekDaySelectorPreview() {
    MaterialTheme {
        WeekDaySelector(
            selectedDays = booleanArrayOf(true, false, true, false, true, false, true),
            onDayToggle = { _, _ -> }
        )
    }
}

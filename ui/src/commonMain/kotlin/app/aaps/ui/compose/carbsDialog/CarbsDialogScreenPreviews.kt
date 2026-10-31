package app.aaps.ui.compose.carbsDialog

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.ui.compose.overview.chips.CobUiState
import app.aaps.ui.compose.overview.chips.IobUiState
import app.aaps.ui.compose.overview.graphs.BgInfoUiState

@Preview(showBackground = true)
@Composable
internal fun CarbsDialogScreenPreview() {
    MaterialTheme {
        CarbsDialogContent(
            uiState = CarbsDialogUiState(
                carbs = 15,
                maxCarbs = 100,
                carbsButtonIncrement1 = 5,
                carbsButtonIncrement2 = 10,
                carbsButtonIncrement3 = 20,
                showNotesFromPreferences = true,
                showBolusReminder = true
            ),
            bgInfo = BgInfoUiState(bgInfo = null, timeAgoText = ""),
            iob = IobUiState(),
            cob = CobUiState(),
            dateString = "25/02/2026",
            timeString = "14:30",
            onHypoChange = {},
            onEatingSoonChange = {},
            onActivityChange = {},
            onCarbsChange = {},
            onAddCarbs = {},
            onTimeOffsetChange = {},
            onAlarmChange = {},
            onDurationChange = {},
            onBolusReminderChange = {},
            onNotesChange = {},
            onDateClick = {},
            onTimeClick = {},
            onSettingsClick = {},
            onNavigateBack = {},
            onConfirmClick = {}
        )
    }
}

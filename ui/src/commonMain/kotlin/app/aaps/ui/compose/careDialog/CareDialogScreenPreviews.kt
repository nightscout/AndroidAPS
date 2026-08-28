package app.aaps.ui.compose.careDialog

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.data.model.GlucoseUnit

@Preview(showBackground = true)
@Composable
internal fun CareDialogScreenPreview() {
    MaterialTheme {
        CareDialogContent(
            uiState = CareDialogUiState(
                eventType = CareportalEventType.BGCHECK,
                bgValue = 120.0,
                glucoseUnits = GlucoseUnit.MGDL,
                showNotesFromPreferences = true
            ),
            eventType = CareportalEventType.BGCHECK,
            dateString = "25/02/2026",
            timeString = "14:30",
            onMeterTypeChange = {},
            onBgValueChange = {},
            onDurationChange = {},
            onNotesChange = {},
            onNavigateBack = {},
            onConfirmClick = {},
            onDateClick = {},
            onTimeClick = {}
        )
    }
}

package app.aaps.ui.compose.extendedBolusDialog

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun ExtendedBolusDialogPreview() {
    MaterialTheme {
        ExtendedBolusDialogContent(
            uiState = ExtendedBolusDialogUiState(
                insulin = 1.0,
                durationMinutes = 30.0,
                maxInsulin = 10.0,
                minInsulin = 0.1,
                extendedStep = 0.1,
                extendedDurationStep = 30.0,
                extendedMaxDuration = 720.0,
            ),
            onInsulinChange = {},
            onDurationChange = {},
            onNavigateBack = {},
            onConfirmClick = {}
        )
    }
}

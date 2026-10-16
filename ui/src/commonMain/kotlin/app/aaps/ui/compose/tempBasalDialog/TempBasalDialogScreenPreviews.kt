package app.aaps.ui.compose.tempBasalDialog

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun TempBasalDialogPercentPreview() {
    MaterialTheme {
        TempBasalDialogContent(
            uiState = TempBasalDialogUiState(
                basalPercent = 100.0,
                durationMinutes = 60.0,
                isPercentPump = true,
                maxTempPercent = 200.0,
                tempPercentStep = 10.0,
                tempDurationStep = 60.0,
                tempMaxDuration = 720.0,
            ),
            onBasalPercentChange = {},
            onBasalAbsoluteChange = {},
            onDurationChange = {},
            onNavigateBack = {},
            onConfirmClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun TempBasalDialogAbsolutePreview() {
    MaterialTheme {
        TempBasalDialogContent(
            uiState = TempBasalDialogUiState(
                basalAbsolute = 0.85,
                durationMinutes = 60.0,
                isPercentPump = false,
                maxTempAbsolute = 10.0,
                tempAbsoluteStep = 0.05,
                tempDurationStep = 60.0,
                tempMaxDuration = 720.0,
            ),
            onBasalPercentChange = {},
            onBasalAbsoluteChange = {},
            onDurationChange = {},
            onNavigateBack = {},
            onConfirmClick = {}
        )
    }
}

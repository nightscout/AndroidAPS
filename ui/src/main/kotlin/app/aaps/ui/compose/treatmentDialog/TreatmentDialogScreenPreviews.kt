package app.aaps.ui.compose.treatmentDialog

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.ui.compose.overview.chips.CobUiState
import app.aaps.ui.compose.overview.chips.IobUiState
import app.aaps.ui.compose.overview.graphs.BgInfoUiState
import java.text.DecimalFormat

@Preview(showBackground = true)
@Composable
internal fun TreatmentDialogScreenPreview() {
    MaterialTheme {
        TreatmentDialogContent(
            uiState = TreatmentDialogUiState(
                insulin = 1.5,
                carbs = 20,
                maxInsulin = 10.0,
                maxCarbs = 100,
                bolusStep = 0.1
            ),
            bgInfo = BgInfoUiState(bgInfo = null, timeAgoText = ""),
            iob = IobUiState(),
            cob = CobUiState(),
            bolusFormat = DecimalFormat("0.0"),
            onInsulinChange = {},
            onCarbsChange = {},
            onNavigateBack = {},
            onConfirmClick = {}
        )
    }
}

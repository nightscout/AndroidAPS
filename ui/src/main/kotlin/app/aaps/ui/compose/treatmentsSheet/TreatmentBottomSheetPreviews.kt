package app.aaps.ui.compose.treatmentsSheet

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.ui.compose.main.QuickWizardItem

@Preview(showBackground = true)
@Composable
internal fun TreatmentBottomSheetPreview() {
    MaterialTheme {
        TreatmentSelectionContent(
            onDismiss = {},
            onNavigate = {},
            quickWizardItems = listOf(
                QuickWizardItem(guid = "1", buttonText = "Meal", detail = "36g / 2.5U", isEnabled = true),
                QuickWizardItem(guid = "2", buttonText = "Snack", detail = "12g / 0.8U", disabledReason = "No insulin required")
            ),
            showCgm = true,
            showCalibration = true,
            showTreatment = true,
            showInsulin = true,
            showAfrezza = true,
            showCarbs = true,
            showCalculator = true,
            isDexcomSource = false,
            showSettingsIcon = true,
            onSettingsClick = {}
        )
    }
}

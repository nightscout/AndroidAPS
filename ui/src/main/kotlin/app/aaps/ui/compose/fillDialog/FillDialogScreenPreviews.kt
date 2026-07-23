package app.aaps.ui.compose.fillDialog

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.data.model.ICfg
import app.aaps.core.ui.compose.insulin.SelectInsulin
import java.text.DecimalFormat

private val previewInsulins = listOf(
    ICfg("Fiasp U100", peak = 55, dia = 5.0, concentration = 1.0),
    ICfg("Lyumjev U200", peak = 45, dia = 5.0, concentration = 2.0),
    ICfg("NovoRapid U100", peak = 75, dia = 5.0, concentration = 1.0)
)

@Composable
private fun PreviewFillDialog(uiState: FillDialogUiState, dateString: String = "06/03/2026", timeString: String = "10:15") {
    MaterialTheme {
        FillDialogContent(
            uiState = uiState,
            dateString = dateString,
            timeString = timeString,
            bolusFormat = DecimalFormat("0.0"),
            onSiteChangeClick = {},
            onCartridgeChangeClick = {},
            onInsulinChange = {},
            onInsulinSelect = {},
            onNotesChange = {},
            onDateClick = {},
            onTimeClick = {},
            onSettingsClick = {},
            onNavigateBack = {},
            onConfirmClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Site Change")
@Composable
internal fun PreviewSiteChange() {
    PreviewFillDialog(
        uiState = FillDialogUiState(
            siteChange = true,
            maxInsulin = 10.0,
            presetButton1 = 0.3,
            presetButton2 = 0.5,
            presetButton3 = 1.0,
            showNotesFromPreferences = true
        )
    )
}

@Preview(showBackground = true, name = "Cartridge Change + Multiple Insulins")
@Composable
internal fun PreviewCartridgeChangeMultipleInsulins() {
    PreviewFillDialog(
        uiState = FillDialogUiState(
            insulin = 0.3,
            siteChange = true,
            insulinCartridgeChange = true,
            maxInsulin = 10.0,
            presetButton1 = 0.3,
            presetButton2 = 0.5,
            presetButton3 = 1.0,
            insulinAfterConstraints = 0.3,
            availableInsulins = previewInsulins,
            selectedInsulin = previewInsulins[0],
            activeInsulinLabel = "Fiasp U100"
        )
    )
}

@Preview(showBackground = true, name = "Pump Units Warning (non-U100)")
@Composable
internal fun PreviewPumpUnitsWarning() {
    PreviewFillDialog(
        uiState = FillDialogUiState(
            insulin = 0.5,
            insulinCartridgeChange = true,
            maxInsulin = 10.0,
            presetButton1 = 0.3,
            presetButton2 = 0.5,
            presetButton3 = 1.0,
            insulinAfterConstraints = 0.5,
            availableInsulins = previewInsulins,
            selectedInsulin = previewInsulins[1],
            activeInsulinLabel = "Lyumjev U200",
            pumpUnitsWarning = "Prime amount is in pump units (Insulin U200)"
        )
    )
}

@Preview(showBackground = true, name = "AAPS Client (No Bolus)")
@Composable
internal fun PreviewAapsClient() {
    PreviewFillDialog(
        uiState = FillDialogUiState(
            siteChange = true,
            insulinCartridgeChange = true,
            showBolus = false,
            availableInsulins = previewInsulins,
            selectedInsulin = previewInsulins[0],
            activeInsulinLabel = "Fiasp U100"
        )
    )
}

@Preview(showBackground = true, name = "Insulin Selection Expanded")
@Composable
internal fun PreviewInsulinSelectionExpanded() {
    MaterialTheme {
        SelectInsulin(
            availableInsulins = previewInsulins,
            selectedInsulin = previewInsulins[0],
            activeInsulinLabel = "Fiasp U100",
            onInsulinSelect = {},
            initialExpanded = true,
            concentrationDropDownEnabled = true
        )
    }
}

package app.aaps.pump.omnipod.common.ui.wizard.compose.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.data.model.ICfg
import app.aaps.core.ui.compose.insulin.SelectInsulin

private val previewInsulins = listOf(
    ICfg("Fiasp U100", peak = 55, dia = 5.0, concentration = 1.0),
    ICfg("Lyumjev U200", peak = 45, dia = 5.0, concentration = 2.0),
    ICfg("NovoRapid U100", peak = 75, dia = 5.0, concentration = 1.0)
)

@Preview(showBackground = true, name = "Select Insulin")
@Composable
internal fun PreviewSelectInsulin() {
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

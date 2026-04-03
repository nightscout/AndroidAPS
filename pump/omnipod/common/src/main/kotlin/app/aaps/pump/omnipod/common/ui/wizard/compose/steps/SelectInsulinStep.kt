package app.aaps.pump.omnipod.common.ui.wizard.compose.steps

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.ICfg
import app.aaps.core.ui.compose.insulin.SelectInsulin
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.omnipod.common.ui.wizard.compose.OmnipodWizardViewModel
import app.aaps.core.ui.R as CoreUiR

/**
 * Insulin selection step — shown when more than one insulin is configured.
 * Wraps the shared [SelectInsulin] composable from core/ui.
 */
@Composable
fun SelectInsulinStep(
    viewModel: OmnipodWizardViewModel,
    onCancel: () -> Unit
) {
    val availableInsulins by viewModel.availableInsulins.collectAsStateWithLifecycle()
    val selectedInsulin by viewModel.selectedInsulin.collectAsStateWithLifecycle()
    val activeInsulinLabel by viewModel.activeInsulinLabel.collectAsStateWithLifecycle()

    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(CoreUiR.string.next),
            onClick = { viewModel.moveToNext() },
            enabled = selectedInsulin != null
        ),
        secondaryButton = WizardButton(
            text = stringResource(CoreUiR.string.cancel),
            onClick = onCancel
        )
    ) {
        Text(
            text = stringResource(CoreUiR.string.select_insulin_description),
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(Modifier.height(16.dp))

        SelectInsulin(
            availableInsulins = availableInsulins,
            selectedInsulin = selectedInsulin,
            activeInsulinLabel = activeInsulinLabel,
            onInsulinSelect = viewModel::selectInsulin,
            concentrationDropDownEnabled = viewModel.concentrationEnabled
        )
    }
}

// region Previews

private val previewInsulins = listOf(
    ICfg("Fiasp U100", peak = 55, dia = 5.0, concentration = 1.0),
    ICfg("Lyumjev U200", peak = 45, dia = 5.0, concentration = 2.0),
    ICfg("NovoRapid U100", peak = 75, dia = 5.0, concentration = 1.0)
)

@Preview(showBackground = true, name = "Select Insulin")
@Composable
private fun PreviewSelectInsulin() {
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

// endregion

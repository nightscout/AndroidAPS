package app.aaps.pump.omnipod.common.ui.wizard.compose.steps

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.insulin.SelectInsulin
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.omnipod.common.ui.wizard.compose.OmnipodWizardViewModel
import app.aaps.core.ui.R as CoreUiR

/**
 * Insulin selection step — shown when more than one insulin is configured.
 * Wraps the shared [SelectInsulin] composable from core/ui.
 *
 * @see PreviewSelectInsulin
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

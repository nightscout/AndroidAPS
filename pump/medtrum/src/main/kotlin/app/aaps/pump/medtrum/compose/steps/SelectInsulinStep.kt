package app.aaps.pump.medtrum.compose.steps

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
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.insulin.SelectInsulin
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.medtrum.code.PatchStep
import app.aaps.pump.medtrum.compose.MedtrumPatchViewModel

/**
 * @see PreviewCollapsed
 * @see PreviewExpanded
 */
@Composable
fun SelectInsulinStep(
    viewModel: MedtrumPatchViewModel,
    onCancel: () -> Unit
) {
    val availableInsulins by viewModel.availableInsulins.collectAsStateWithLifecycle()
    val selectedInsulin by viewModel.selectedInsulin.collectAsStateWithLifecycle()
    val activeInsulinLabel by viewModel.activeInsulinLabel.collectAsStateWithLifecycle()

    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(R.string.next),
            onClick = { viewModel.moveStep(PatchStep.PRIME) }
        ),
        secondaryButton = WizardButton(
            text = stringResource(R.string.cancel),
            onClick = onCancel
        )
    ) {
        Text(
            text = stringResource(R.string.select_insulin_description),
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

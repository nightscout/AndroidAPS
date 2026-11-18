package app.aaps.pump.carelevo.compose.patchflow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.insulin.SelectInsulin
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.core.ui.compose.stringResource
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchConnectionFlowViewModel

/**
 * Wizard step letting the user pick which insulin the pump/loop should use. Shown only when more
 * than one insulin is configured (`showInsulinStep`). Mirrors the Medtrum/Equil insulin step and
 * reuses the shared [SelectInsulin] picker.
 */
@Composable
internal fun CarelevoSelectInsulinStep(
    viewModel: CarelevoPatchConnectionFlowViewModel
) {
    val availableInsulins by viewModel.availableInsulins.collectAsStateWithLifecycle()
    val selectedInsulin by viewModel.selectedInsulin.collectAsStateWithLifecycle()
    val activeInsulinLabel by viewModel.activeInsulinLabel.collectAsStateWithLifecycle()

    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(CoreUiStrings.next),
            onClick = { viewModel.advanceFromInsulin() },
            enabled = selectedInsulin != null
        ),
        secondaryButton = WizardButton(
            text = stringResource(CoreUiStrings.cancel),
            onClick = { viewModel.exitWizard() }
        )
    ) {
        SelectInsulin(
            availableInsulins = availableInsulins,
            selectedInsulin = selectedInsulin,
            activeInsulinLabel = activeInsulinLabel,
            onInsulinSelect = { viewModel.selectInsulin(it) },
            initialExpanded = true,
            concentrationDropDownEnabled = viewModel.concentrationEnabled
        )
    }
}

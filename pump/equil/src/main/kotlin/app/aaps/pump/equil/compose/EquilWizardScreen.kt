package app.aaps.pump.equil.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.pump.WizardScreen
import app.aaps.pump.equil.R
import app.aaps.pump.equil.compose.steps.AirStep
import app.aaps.pump.equil.compose.steps.AssembleStep
import app.aaps.pump.equil.compose.steps.AttachStep
import app.aaps.pump.equil.compose.steps.BleScanStepWrapper
import app.aaps.pump.equil.compose.steps.ChangeInsulinStep
import app.aaps.pump.equil.compose.steps.ConfirmStep
import app.aaps.pump.equil.compose.steps.FillStep
import app.aaps.pump.equil.compose.steps.PasswordStep
import app.aaps.pump.equil.compose.steps.SelectInsulinStep
import app.aaps.pump.equil.compose.steps.SiteLocationStep
import app.aaps.pump.equil.compose.steps.UnpairConfirmStep
import app.aaps.pump.equil.compose.steps.UnpairDetachStep

@Composable
internal fun EquilWizardScreen(
    viewModel: EquilWizardViewModel,
    setToolbarConfig: ((app.aaps.core.ui.compose.ToolbarConfig) -> Unit)? = null
) {
    val wizardStep by viewModel.wizardStep.collectAsStateWithLifecycle()
    val totalSteps by viewModel.totalSteps.collectAsStateWithLifecycle()
    val currentStepIndex by viewModel.currentStepIndex.collectAsStateWithLifecycle()
    val canGoBack by viewModel.canGoBack.collectAsStateWithLifecycle()

    val titleResId by viewModel.titleResId.collectAsStateWithLifecycle()

    WizardScreen(
        currentStep = wizardStep,
        totalSteps = totalSteps,
        currentStepIndex = currentStepIndex,
        canGoBack = canGoBack,
        onBack = { viewModel.handleCancel() },
        cancelDialogTitle = stringResource(R.string.equil_common_wizard_exit_confirmation_title),
        cancelDialogText = stringResource(R.string.equil_common_wizard_exit_confirmation_text),
        title = stringResource(titleResId),
        setToolbarConfig = setToolbarConfig,
        stepContent = { step, onCancel ->
            when (step) {
                EquilWizardStep.ASSEMBLE       -> AssembleStep(viewModel, onCancel)
                EquilWizardStep.BLE_SCAN       -> BleScanStepWrapper(viewModel, onCancel)
                EquilWizardStep.PASSWORD       -> PasswordStep(viewModel, onCancel)
                EquilWizardStep.FILL           -> FillStep(viewModel, onCancel)
                EquilWizardStep.ATTACH         -> AttachStep(viewModel, onCancel)
                EquilWizardStep.AIR            -> AirStep(viewModel, onCancel)
                EquilWizardStep.SELECT_INSULIN -> SelectInsulinStep(viewModel, onCancel)
                EquilWizardStep.SITE_LOCATION  -> SiteLocationStep(viewModel, onCancel)
                EquilWizardStep.CONFIRM        -> ConfirmStep(viewModel, onCancel)
                EquilWizardStep.CHANGE_INSULIN -> ChangeInsulinStep(viewModel, onCancel)
                EquilWizardStep.UNPAIR_DETACH  -> UnpairDetachStep(viewModel, onCancel)
                EquilWizardStep.UNPAIR_CONFIRM -> UnpairConfirmStep(viewModel, onCancel)

                EquilWizardStep.CANCEL         -> {} // Terminal, finish event emitted
            }
        }
    )
}

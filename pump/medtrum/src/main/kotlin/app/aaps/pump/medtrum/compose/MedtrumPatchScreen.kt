package app.aaps.pump.medtrum.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.pump.WizardScreen
import app.aaps.pump.medtrum.R
import app.aaps.pump.medtrum.code.PatchStep
import app.aaps.pump.medtrum.compose.steps.ActivateStep
import app.aaps.pump.medtrum.compose.steps.AttachStep
import app.aaps.pump.medtrum.compose.steps.CompleteStep
import app.aaps.pump.medtrum.compose.steps.ConfirmDeactivateStep
import app.aaps.pump.medtrum.compose.steps.DeactivateCompleteStep
import app.aaps.pump.medtrum.compose.steps.DeactivatingStep
import app.aaps.pump.medtrum.compose.steps.PrepareStep
import app.aaps.pump.medtrum.compose.steps.PrimeStep
import app.aaps.pump.medtrum.compose.steps.RetryActivationStep
import app.aaps.pump.medtrum.compose.steps.SelectInsulinStep
import app.aaps.pump.medtrum.compose.steps.SiteLocationStep

@Composable
fun MedtrumPatchScreen(
    viewModel: MedtrumPatchViewModel,
    setToolbarConfig: ((app.aaps.core.ui.compose.ToolbarConfig) -> Unit)? = null
) {
    val patchStep by viewModel.patchStep.collectAsStateWithLifecycle()
    val totalSteps by viewModel.totalSteps.collectAsStateWithLifecycle()
    val currentStepIndex by viewModel.currentStepIndex.collectAsStateWithLifecycle()
    val canGoBack by viewModel.canGoBack.collectAsStateWithLifecycle()

    val titleResId by viewModel.title.collectAsStateWithLifecycle()

    WizardScreen(
        currentStep = patchStep,
        totalSteps = totalSteps,
        currentStepIndex = currentStepIndex,
        canGoBack = canGoBack,
        onBack = { viewModel.handleBack() },
        cancelDialogTitle = stringResource(R.string.change_patch_label),
        cancelDialogText = stringResource(R.string.cancel_sure),
        title = stringResource(titleResId),
        setToolbarConfig = setToolbarConfig,
        stepContent = { step, onCancel ->
            when (step) {
                PatchStep.PREPARE_PATCH,
                PatchStep.PREPARE_PATCH_CONNECT -> PrepareStep(viewModel, onCancel)

                PatchStep.SELECT_INSULIN -> SelectInsulinStep(viewModel, onCancel)

                PatchStep.PRIME,
                PatchStep.PRIMING,
                PatchStep.PRIME_COMPLETE -> PrimeStep(viewModel, onCancel)

                PatchStep.ATTACH_PATCH -> AttachStep(viewModel, onCancel)

                PatchStep.ACTIVATE,
                PatchStep.ACTIVATE_COMPLETE -> ActivateStep(viewModel, onCancel)

                PatchStep.SITE_LOCATION -> SiteLocationStep(viewModel, onCancel)

                PatchStep.COMPLETE,
                PatchStep.CANCEL -> CompleteStep(viewModel)

                PatchStep.START_DEACTIVATION -> ConfirmDeactivateStep(viewModel, onCancel)

                PatchStep.DEACTIVATE,
                PatchStep.FORCE_DEACTIVATION -> DeactivatingStep(viewModel, onCancel)

                PatchStep.DEACTIVATION_COMPLETE -> DeactivateCompleteStep(viewModel)

                PatchStep.RETRY_ACTIVATION,
                PatchStep.RETRY_ACTIVATION_CONNECT -> RetryActivationStep(viewModel, onCancel)
            }
        }
    )
}

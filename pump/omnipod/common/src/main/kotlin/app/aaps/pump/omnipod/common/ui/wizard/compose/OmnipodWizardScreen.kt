package app.aaps.pump.omnipod.common.ui.wizard.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.pump.KeepScreenOnEffect
import app.aaps.core.ui.compose.pump.WizardScreen
import app.aaps.pump.omnipod.common.R
import app.aaps.pump.omnipod.common.ui.wizard.compose.steps.ActionStep
import app.aaps.pump.omnipod.common.ui.wizard.compose.steps.AttachPodStep
import app.aaps.pump.omnipod.common.ui.wizard.compose.steps.DeactivatePodStep
import app.aaps.pump.omnipod.common.ui.wizard.compose.steps.InfoStep
import app.aaps.pump.omnipod.common.ui.wizard.compose.steps.SelectInsulinStep
import app.aaps.pump.omnipod.common.ui.wizard.compose.steps.SiteLocationStep

/**
 * Top-level Compose screen for Omnipod activation/deactivation wizard.
 * Routes each [OmnipodWizardStep] to the appropriate step composable.
 *
 * Uses shared [WizardScreen] from core/ui for progress indicator,
 * animated transitions, back-press confirmation, and toolbar management.
 */
@Composable
fun OmnipodWizardScreen(
    viewModel: OmnipodWizardViewModel,
    onFinish: () -> Unit,
    setToolbarConfig: ((ToolbarConfig) -> Unit)? = null
) {
    val currentStep by viewModel.currentStep.collectAsStateWithLifecycle()
    val totalSteps by viewModel.totalSteps.collectAsStateWithLifecycle()
    val currentStepIndex by viewModel.currentStepIndex.collectAsStateWithLifecycle()
    val canGoBack by viewModel.canGoBack.collectAsStateWithLifecycle()

    KeepScreenOnEffect()

    // Collect finish events
    androidx.compose.runtime.LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is OmnipodWizardEvent.Finish -> onFinish()
            }
        }
    }

    // Resolve title for current step
    val title = currentStep?.let { stringResource(viewModel.getTitleForStep(it)) } ?: ""

    WizardScreen(
        currentStep = currentStep,
        totalSteps = totalSteps,
        currentStepIndex = currentStepIndex,
        canGoBack = canGoBack,
        onBack = onFinish,
        cancelDialogTitle = stringResource(R.string.omnipod_common_wizard_exit_confirmation_title),
        cancelDialogText = stringResource(R.string.omnipod_common_wizard_exit_confirmation_text),
        title = title,
        setToolbarConfig = setToolbarConfig
    ) { step, onCancel ->
        val textResId = viewModel.getTextForStep(step)

        when (step) {
            // Activation info steps
            OmnipodWizardStep.START_POD_ACTIVATION   -> InfoStep(
                textResId = textResId,
                isFinishStep = false,
                onNext = { viewModel.moveToNext() },
                onCancel = onCancel
            )

            OmnipodWizardStep.SELECT_INSULIN         -> SelectInsulinStep(
                viewModel = viewModel,
                onCancel = onCancel
            )

            // Activation action steps
            OmnipodWizardStep.INITIALIZE_POD         -> ActionStep(
                viewModel = viewModel,
                textResId = textResId,
                onCancel = onCancel,
                showDeactivateOnFailure = true,
                onDeactivatePod = onFinish
            )

            OmnipodWizardStep.ATTACH_POD             -> AttachPodStep(
                titleResId = viewModel.getTitleForStep(step),
                textResId = textResId,
                onNext = { viewModel.moveToNext() },
                onCancel = onCancel
            )

            OmnipodWizardStep.SITE_LOCATION          -> SiteLocationStep(
                viewModel = viewModel
            )

            OmnipodWizardStep.INSERT_CANNULA         -> ActionStep(
                viewModel = viewModel,
                textResId = textResId,
                onCancel = onCancel,
                showDeactivateOnFailure = true,
                onDeactivatePod = onFinish
            )

            OmnipodWizardStep.POD_ACTIVATED          -> InfoStep(
                textResId = textResId,
                isFinishStep = true,
                onNext = {
                    viewModel.executeInsulinProfileSwitch()
                    viewModel.saveSiteLocation()
                    viewModel.finish()
                },
                onCancel = null
            )

            // Deactivation info steps
            OmnipodWizardStep.START_POD_DEACTIVATION -> InfoStep(
                textResId = textResId,
                isFinishStep = false,
                onNext = { viewModel.moveToNext() },
                onCancel = onCancel
            )

            // Deactivation action step
            OmnipodWizardStep.DEACTIVATE_POD         -> DeactivatePodStep(
                viewModel = viewModel,
                textResId = textResId,
                onCancel = onCancel
            )

            OmnipodWizardStep.POD_DEACTIVATED        -> InfoStep(
                textResId = textResId,
                isFinishStep = true,
                onNext = { viewModel.finish() },
                onCancel = null
            )

            OmnipodWizardStep.POD_DISCARDED          -> InfoStep(
                textResId = textResId,
                isFinishStep = true,
                onNext = { viewModel.finish() },
                onCancel = null
            )
        }
    }
}

package app.aaps.pump.carelevo.compose.patchflow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.core.ui.compose.pump.ProfileGateWizardStep
import app.aaps.core.ui.compose.pump.WizardScreen
import app.aaps.core.ui.compose.siteRotation.SiteLocationWizardStep
import app.aaps.core.ui.compose.stringResource
import app.aaps.pump.carelevo.CarelevoStrings
import app.aaps.pump.carelevo.common.model.UiState
import app.aaps.pump.carelevo.compose.CarelevoBusyOverlay
import app.aaps.pump.carelevo.presentation.model.CarelevoConnectEvent
import app.aaps.pump.carelevo.presentation.type.CarelevoPatchStep
import app.aaps.pump.carelevo.presentation.type.CarelevoScreenType
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchConnectViewModel
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchConnectionFlowViewModel
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchNeedleInsertionViewModel
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchSafetyCheckViewModel

@Composable
internal fun CarelevoPatchFlowScreen(
    screenType: CarelevoScreenType,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    snackbarHostState: SnackbarHostState,
    onExitFlow: () -> Unit
) {
    CarelevoPatchConnectionFlowScreen(
        screenType = screenType,
        setToolbarConfig = setToolbarConfig,
        snackbarHostState = snackbarHostState,
        onExitFlow = onExitFlow
    )
}

@Composable
private fun CarelevoPatchConnectionFlowScreen(
    screenType: CarelevoScreenType,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    snackbarHostState: SnackbarHostState,
    onExitFlow: () -> Unit
) {
    val viewModel: CarelevoPatchConnectionFlowViewModel = metroViewModel()
    val connectViewModel: CarelevoPatchConnectViewModel = metroViewModel()
    val needleInsertionViewModel: CarelevoPatchNeedleInsertionViewModel = metroViewModel()
    val safetyCheckViewModel: CarelevoPatchSafetyCheckViewModel = metroViewModel()
    val page by viewModel.page.collectAsStateWithLifecycle()
    val totalSteps by viewModel.totalSteps.collectAsStateWithLifecycle()
    val currentStepIndex by viewModel.currentStepIndex.collectAsStateWithLifecycle()
    val sharedUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val connectUiState by connectViewModel.uiState.collectAsStateWithLifecycle()
    val needleInsertionUiState by needleInsertionViewModel.uiState.collectAsStateWithLifecycle()
    val safetyCheckUiState by safetyCheckViewModel.uiState.collectAsStateWithLifecycle()
    val primingPulse by safetyCheckViewModel.primingPulse.collectAsStateWithLifecycle()
    val activationDeadline by remember(viewModel) { viewModel.activationDeadline() }.collectAsStateWithLifecycle(null)
    val discardFailedMessage = stringResource(CarelevoStrings.carelevo_toast_msg_discard_failed)

    // Every exit path MUST drop the ViewModel's one-shot init latch: the VM outlives this screen
    // (it is scoped to the plugin's NavBackStackEntry), so without the reset a SECOND activation
    // started from the same plugin visit would reuse the previous run's workflowSteps and page —
    // i.e. the wizard could resume at NEEDLE_INSERTION against a brand-new, never-safety-checked
    // patch. The latch itself stays (it is what preserves mid-flow progress across rotation).
    val exitFlow: () -> Unit = remember(viewModel, onExitFlow) {
        {
            viewModel.setIsCreated(false)
            onExitFlow()
        }
    }

    LaunchedEffect(viewModel) {
        if (!viewModel.isCreated) {
            viewModel.initWorkflow(screenType)
            viewModel.setIsCreated(true)
        }
    }

    LaunchedEffect(page) {
        if (page == CarelevoPatchStep.PATCH_CONNECT) {
            connectViewModel.resetForEnterStep()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                CarelevoConnectEvent.DiscardComplete -> exitFlow()

                CarelevoConnectEvent.DiscardFailed   -> {
                    snackbarHostState.showSnackbar(discardFailedMessage)
                }

                CarelevoConnectEvent.ExitFlow        -> exitFlow()

                else                                 -> Unit
            }
        }
    }

    // Nothing is paired until the connect step succeeds, and basal only starts after needle
    // insertion, which ends the wizard - so no step here is stopping a running patch.
    val beforePatch = page in PRE_PATCH_STEPS

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Above the step, so it stays in place while the steps change.
            activationDeadline?.takeIf { !beforePatch }?.let { CarelevoActivationTimeLimit(deadline = it) }
            Box(modifier = Modifier.weight(1f)) {
                WizardScreen(
                    currentStep = page,
                    totalSteps = totalSteps,
                    currentStepIndex = currentStepIndex,
                    canGoBack = true,
                    onBack = { viewModel.startPatchDiscardProcess() },
                    cancelDialogTitle = stringResource(
                        if (beforePatch) CarelevoStrings.carelevo_dialog_patch_activation_cancel_title
                        else CarelevoStrings.carelevo_dialog_patch_discard_unused_title
                    ),
                    cancelDialogText = stringResource(
                        if (beforePatch) CarelevoStrings.carelevo_dialog_patch_activation_cancel_desc
                        else CarelevoStrings.carelevo_dialog_patch_discard_unused_desc
                    ),
                    title = patchStepTitle(page),
                    setToolbarConfig = setToolbarConfig,
                ) { step, _ ->
                    when (step) {
                        CarelevoPatchStep.PROFILE_GATE     -> {
                            ProfileGateWizardStep(host = viewModel)
                        }

                        CarelevoPatchStep.SELECT_INSULIN   -> {
                            CarelevoSelectInsulinStep(viewModel = viewModel)
                        }

                        CarelevoPatchStep.PATCH_START      -> {
                            CarelevoPatchFlowStep01Start(
                                viewModel = viewModel,
                                onExitFlow = exitFlow
                            )
                        }

                        CarelevoPatchStep.SET_AMOUNT       -> {
                            CarelevoSetAmountStep(viewModel = viewModel)
                        }

                        CarelevoPatchStep.PATCH_CONNECT    -> {
                            CarelevoPatchFlowStep02Connect(
                                viewModel = connectViewModel,
                                sharedViewModel = viewModel,
                                onExitFlow = exitFlow
                            )
                        }

                        CarelevoPatchStep.SAFETY_CHECK     -> {
                            CarelevoPatchFlowStep03SafetyCheck(
                                viewModel = safetyCheckViewModel,
                                sharedViewModel = viewModel,
                                onExitFlow = exitFlow
                            )
                        }

                        CarelevoPatchStep.SITE_LOCATION    -> {
                            SiteLocationWizardStep(host = viewModel)
                        }

                        CarelevoPatchStep.PATCH_ATTACH     -> {
                            CarelevoPatchFlowStep04Attach(
                                viewModel = viewModel
                            )
                        }

                        CarelevoPatchStep.NEEDLE_INSERTION -> {
                            CarelevoPatchFlowStep05NeedleInsertion(
                                viewModel = needleInsertionViewModel,
                                onExitFlow = exitFlow
                            )
                        }
                    }
                }
            }
        }

        val activeUiState = when (page) {
            CarelevoPatchStep.PATCH_CONNECT    -> connectUiState
            CarelevoPatchStep.NEEDLE_INSERTION -> needleInsertionUiState
            CarelevoPatchStep.SAFETY_CHECK     -> safetyCheckUiState
            else                               -> sharedUiState
        }

        if (activeUiState is UiState.Loading) {
            // A Retry waits about 30 s for each of its pulses, so it says which one it is at.
            val pulse = primingPulse.takeIf { page == CarelevoPatchStep.SAFETY_CHECK }
            CarelevoBusyOverlay(
                text = if (pulse != null) stringResource(CarelevoStrings.carelevo_priming_pulse_progress, pulse, safetyCheckViewModel.primingPulseCount)
                else stringResource(CoreUiStrings.loading)
            )
        }
    }
}

internal val PRE_PATCH_STEPS = setOf(
    CarelevoPatchStep.PROFILE_GATE,
    CarelevoPatchStep.SELECT_INSULIN,
    CarelevoPatchStep.PATCH_START,
    CarelevoPatchStep.SET_AMOUNT,
    CarelevoPatchStep.PATCH_CONNECT
)

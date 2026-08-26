package app.aaps.pump.carelevo.compose.patchflow

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.R as CoreUiR
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.pump.ProfileGateWizardStep
import app.aaps.core.ui.compose.pump.WizardScreen
import app.aaps.core.ui.compose.siteRotation.SiteLocationWizardStep
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.common.model.UiState
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
    val viewModel: CarelevoPatchConnectionFlowViewModel = hiltViewModel()
    val connectViewModel: CarelevoPatchConnectViewModel = hiltViewModel()
    val needleInsertionViewModel: CarelevoPatchNeedleInsertionViewModel = hiltViewModel()
    val safetyCheckViewModel: CarelevoPatchSafetyCheckViewModel = hiltViewModel()
    val page by viewModel.page.collectAsStateWithLifecycle()
    val totalSteps by viewModel.totalSteps.collectAsStateWithLifecycle()
    val currentStepIndex by viewModel.currentStepIndex.collectAsStateWithLifecycle()
    val sharedUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val connectUiState by connectViewModel.uiState.collectAsStateWithLifecycle()
    val needleInsertionUiState by needleInsertionViewModel.uiState.collectAsStateWithLifecycle()
    val safetyCheckUiState by safetyCheckViewModel.uiState.collectAsStateWithLifecycle()
    val discardFailedMessage = stringResource(R.string.carelevo_toast_msg_discard_failed)

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

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        WizardScreen(
            currentStep = page,
            totalSteps = totalSteps,
            currentStepIndex = currentStepIndex,
            canGoBack = true,
            onBack = { viewModel.startPatchDiscardProcess() },
            cancelDialogTitle = stringResource(R.string.carelevo_dialog_patch_discard_message_title),
            cancelDialogText = stringResource(R.string.carelevo_dialog_patch_discard_message_desc),
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

        val activeUiState = when (page) {
            CarelevoPatchStep.PATCH_CONNECT    -> connectUiState
            CarelevoPatchStep.NEEDLE_INSERTION -> needleInsertionUiState
            CarelevoPatchStep.SAFETY_CHECK     -> safetyCheckUiState
            else                               -> sharedUiState
        }

        if (activeUiState is UiState.Loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    // Consume all taps: without this the scrim is decoration only and a
                    // double-tap can still reach (and re-fire) the buttons underneath while
                    // the first BLE command is in flight.
                    .pointerInput(Unit) { detectTapGestures { } },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AapsSpacing.extraLarge)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = stringResource(CoreUiR.string.loading),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

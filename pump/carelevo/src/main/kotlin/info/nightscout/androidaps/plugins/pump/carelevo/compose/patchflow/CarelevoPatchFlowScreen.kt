package info.nightscout.androidaps.plugins.pump.carelevo.compose.patchflow
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.LocalSnackbarHostState
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.pump.WizardScreen
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.UiState
import info.nightscout.androidaps.plugins.pump.carelevo.R
import info.nightscout.androidaps.plugins.pump.carelevo.presentation.model.CarelevoConnectEvent
import info.nightscout.androidaps.plugins.pump.carelevo.presentation.type.CarelevoPatchStep
import info.nightscout.androidaps.plugins.pump.carelevo.presentation.type.CarelevoScreenType
import info.nightscout.androidaps.plugins.pump.carelevo.presentation.viewmodel.CarelevoPatchConnectViewModel
import info.nightscout.androidaps.plugins.pump.carelevo.presentation.viewmodel.CarelevoPatchConnectionFlowViewModel
import info.nightscout.androidaps.plugins.pump.carelevo.presentation.viewmodel.CarelevoPatchNeedleInsertionViewModel
import info.nightscout.androidaps.plugins.pump.carelevo.presentation.viewmodel.CarelevoPatchSafetyCheckViewModel

@Composable
fun CarelevoPatchFlowScreen(
    screenType: CarelevoScreenType,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onExitFlow: () -> Unit
) {
    CarelevoPatchConnectionFlowScreen(
        screenType = screenType,
        setToolbarConfig = setToolbarConfig,
        onExitFlow = onExitFlow
    )
}

@Composable
private fun CarelevoPatchConnectionFlowScreen(
    screenType: CarelevoScreenType,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onExitFlow: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: CarelevoPatchConnectionFlowViewModel = hiltViewModel()
    val connectViewModel: CarelevoPatchConnectViewModel = hiltViewModel()
    val needleInsertionViewModel: CarelevoPatchNeedleInsertionViewModel = hiltViewModel()
    val safetyCheckViewModel: CarelevoPatchSafetyCheckViewModel = hiltViewModel()
    val page by viewModel.page.collectAsStateWithLifecycle()
    val sharedUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val connectUiState by connectViewModel.uiState.collectAsStateWithLifecycle()
    val needleInsertionUiState by needleInsertionViewModel.uiState.collectAsStateWithLifecycle()
    val safetyCheckUiState by safetyCheckViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current
    val discardCompleteMessage = stringResource(R.string.carelevo_toast_msg_discard_complete)
    val discardFailedMessage = stringResource(R.string.carelevo_toast_msg_discard_failed)

    LaunchedEffect(viewModel) {
        if (!viewModel.isCreated) {
            viewModel.observePatchEvent()
            viewModel.setIsCreated(true)
        }
        if (screenType == CarelevoScreenType.CONNECTION_FLOW_START) {
            viewModel.setPage(CarelevoPatchStep.PATCH_START)
        }
        if (screenType == CarelevoScreenType.SAFETY_CHECK) {
             viewModel.setPage(CarelevoPatchStep.SAFETY_CHECK)
        }
        if (screenType == CarelevoScreenType.NEEDLE_INSERTION) {
            viewModel.setPage(CarelevoPatchStep.PATCH_ATTACH)
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
                CarelevoConnectEvent.DiscardComplete -> {
                    Toast.makeText(context, discardCompleteMessage, Toast.LENGTH_SHORT).show()
                    onExitFlow()
                }

                CarelevoConnectEvent.DiscardFailed   -> {
                    snackbarHostState.showSnackbar(discardFailedMessage)
                }

                else                                 -> Unit
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        WizardScreen(
            currentStep = page,
            totalSteps = CarelevoPatchStep.entries.size,
            currentStepIndex = page.ordinal,
            canGoBack = true,
            onBack = { viewModel.startPatchDiscardProcess() },
            cancelDialogTitle = stringResource(R.string.carelevo_dialog_patch_discard_message_title),
            cancelDialogText = stringResource(R.string.carelevo_dialog_patch_discard_message_desc),
            title = patchStepTitle(page),
            setToolbarConfig = setToolbarConfig,
        ) { step, _ ->
            when (step) {
                CarelevoPatchStep.PATCH_START      -> {
                    CarelevoPatchFlowStep01Start(
                        viewModel = viewModel,
                        onExitFlow = onExitFlow
                    )
                }

                CarelevoPatchStep.PATCH_CONNECT    -> {
                    CarelevoPatchFlowStep02Connect(
                        viewModel = connectViewModel,
                        sharedViewModel = viewModel,
                        onExitFlow = onExitFlow
                    )
                }

                CarelevoPatchStep.SAFETY_CHECK     -> {
                    CarelevoPatchFlowStep03SafetyCheck(
                        viewModel = safetyCheckViewModel,
                        sharedViewModel = viewModel,
                        onExitFlow = onExitFlow
                    )
                }

                CarelevoPatchStep.PATCH_ATTACH     -> {
                    CarelevoPatchFlowStep04Attach(
                        viewModel = viewModel
                    )
                }

                CarelevoPatchStep.NEEDLE_INSERTION -> {
                    CarelevoPatchFlowStep05NeedleInsertion(
                        viewModel = needleInsertionViewModel,
                        onExitFlow = onExitFlow
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
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = stringResource(app.aaps.core.ui.R.string.loading),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

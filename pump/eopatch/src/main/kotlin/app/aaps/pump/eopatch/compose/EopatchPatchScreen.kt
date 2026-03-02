package app.aaps.pump.eopatch.compose

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.pump.StepProgressIndicator
import app.aaps.pump.eopatch.R
import app.aaps.pump.eopatch.code.PatchStep
import app.aaps.pump.eopatch.compose.steps.BasalScheduleStep
import app.aaps.pump.eopatch.compose.steps.ConnectStep
import app.aaps.pump.eopatch.compose.steps.RemoveNeedleCapStep
import app.aaps.pump.eopatch.compose.steps.RemoveProtectionTapeStep
import app.aaps.pump.eopatch.compose.steps.RemoveStep
import app.aaps.pump.eopatch.compose.steps.RotateKnobStep
import app.aaps.pump.eopatch.compose.steps.SafeDeactivationStep
import app.aaps.pump.eopatch.compose.steps.SafetyCheckStep
import app.aaps.pump.eopatch.compose.steps.TurningOffAlarmStep
import app.aaps.pump.eopatch.compose.steps.WakeUpStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EopatchPatchScreen(
    viewModel: EopatchPatchViewModel
) {
    val patchStep by viewModel.patchStep.collectAsStateWithLifecycle()
    val titleResId by viewModel.title.collectAsStateWithLifecycle()

    // Dialog states
    var showProgressDialog by remember { mutableStateOf(false) }
    var progressTitle by remember { mutableStateOf("") }
    var showCommErrorDialog by remember { mutableStateOf(false) }
    var isForcedDiscardError by remember { mutableStateOf(false) }
    var showBondedDialog by remember { mutableStateOf(false) }
    var showChangePatchDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    // Handle events
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PatchEvent.Finish                -> { /* handled by ComposeContent */
                }

                is PatchEvent.ShowCommProgress      -> {
                    progressTitle = viewModel.rh.gs(event.titleResId)
                    showProgressDialog = true
                }

                is PatchEvent.DismissCommProgress   -> {
                    showProgressDialog = false
                }

                is PatchEvent.ShowCommError         -> {
                    isForcedDiscardError = event.isForcedDiscard
                    showCommErrorDialog = true
                }

                is PatchEvent.ShowBondedDialog      -> showBondedDialog = true
                is PatchEvent.ShowChangePatchDialog -> showChangePatchDialog = true
                is PatchEvent.ShowDiscardDialog     -> showDiscardDialog = true
            }
        }
    }

    // Back handler
    BackHandler(enabled = true) {
        viewModel.handleBack()
    }

    val canGoBack = patchStep in listOf(
        PatchStep.WAKE_UP,
        PatchStep.SAFE_DEACTIVATION,
        PatchStep.DISCARDED,
        PatchStep.DISCARDED_FOR_CHANGE,
        PatchStep.DISCARDED_FROM_ALARM,
        PatchStep.MANUALLY_TURNING_OFF_ALARM,
        PatchStep.BASAL_SCHEDULE
    )

    // ── Dialogs ─────────────────────────────────────────────────────────

    // Progress dialog
    if (showProgressDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(progressTitle) },
            text = { androidx.compose.material3.CircularProgressIndicator() },
            confirmButton = { }
        )
    }

    // Communication error dialog
    if (showCommErrorDialog) {
        val commCheckCancelLabel = viewModel.commCheckCancelLabel
        if (isForcedDiscardError) {
            val message = "${stringResource(R.string.patch_comm_error_during_discard_desc_2)}\n${stringResource(R.string.patch_communication_check_helper_2)}"
            AlertDialog(
                onDismissRequest = { },
                title = { Text(stringResource(R.string.patch_communication_failed)) },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = {
                        showCommErrorDialog = false
                        viewModel.discardPatch()
                    }) { Text(stringResource(R.string.discard)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showCommErrorDialog = false
                        viewModel.cancelPatchCommCheck()
                    }) { Text(commCheckCancelLabel) }
                }
            )
        } else {
            val message = "${stringResource(R.string.patch_communication_check_helper_1)}\n${stringResource(R.string.patch_communication_check_helper_2)}"
            AlertDialog(
                onDismissRequest = { },
                title = { Text(stringResource(R.string.patch_communication_failed)) },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = {
                        showCommErrorDialog = false
                        viewModel.retryCheckCommunication()
                    }) { Text(stringResource(R.string.retry)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showCommErrorDialog = false
                        viewModel.cancelPatchCommCheck()
                    }) { Text(commCheckCancelLabel) }
                }
            )
        }
    }

    // Bonded dialog
    if (showBondedDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.patch_communication_succeed)) },
            text = { Text(stringResource(R.string.patch_communication_succeed_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showBondedDialog = false
                    viewModel.dismissPatchCommCheckDialogInternal(true)
                }) { Text(stringResource(R.string.confirm)) }
            }
        )
    }

    // Change patch dialog
    if (showChangePatchDialog) {
        val message = when {
            viewModel.patchState.isBolusActive && viewModel.patchState.isTempBasalActive ->
                stringResource(R.string.patch_change_confirm_bolus_and_temp_basal_are_active_desc)

            viewModel.patchState.isBolusActive                                           -> stringResource(R.string.patch_change_confirm_bolus_is_active_desc)
            viewModel.patchState.isTempBasalActive                                       -> stringResource(R.string.patch_change_confirm_temp_basal_is_active_desc)
            else                                                                         -> stringResource(R.string.patch_change_confirm_desc)
        }
        AlertDialog(
            onDismissRequest = { showChangePatchDialog = false },
            title = { Text(stringResource(R.string.string_discard_patch)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    showChangePatchDialog = false
                    viewModel.deactivatePatch()
                }) { Text(stringResource(R.string.string_discard_patch)) }
            },
            dismissButton = {
                TextButton(onClick = { showChangePatchDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Discard dialog
    if (showDiscardDialog) {
        val cancelLabel = if (viewModel.isInAlarmHandling) null else stringResource(R.string.cancel)
        val message = if (viewModel.isBolusActive)
            stringResource(R.string.patch_change_confirm_bolus_is_active_desc)
        else
            stringResource(R.string.string_are_you_sure_to_discard_current_patch)
        AlertDialog(
            onDismissRequest = { if (!viewModel.isInAlarmHandling) showDiscardDialog = false },
            title = { Text(stringResource(R.string.string_discard_patch)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    viewModel.deactivate(true) {
                        viewModel.dismissPatchCommCheckDialogInternal()
                        try {
                            viewModel.moveStep(
                                if (viewModel.isConnected) PatchStep.DISCARDED
                                else PatchStep.MANUALLY_TURNING_OFF_ALARM
                            )
                        } catch (_: IllegalStateException) {
                            viewModel.handleComplete()
                        }
                    }
                }) { Text(stringResource(R.string.discard)) }
            },
            dismissButton = {
                cancelLabel?.let { label ->
                    TextButton(onClick = {
                        showDiscardDialog = false
                        viewModel.updateIncompletePatchActivationReminder()
                    }) { Text(label) }
                }
            }
        )
    }

    // ── Screen ──────────────────────────────────────────────────────────

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleResId)) },
                navigationIcon = {
                    if (canGoBack) {
                        IconButton(onClick = { viewModel.handleBack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(app.aaps.core.ui.R.string.back)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        viewModel.updateIncompletePatchActivationReminder()
                        tryAwaitRelease()
                    })
                }
        ) {
            // Step progress indicator
            if (patchStep != null) {
                StepProgressIndicator(
                    totalSteps = viewModel.totalSteps,
                    currentStep = viewModel.currentStepIndex
                )
            }

            // Step content with transitions
            AnimatedContent(
                targetState = patchStep,
                transitionSpec = {
                    (fadeIn() + slideInHorizontally { it / 4 })
                        .togetherWith(fadeOut() + slideOutHorizontally { -it / 4 })
                },
                label = "patchStepTransition"
            ) { step ->
                when (step) {
                    PatchStep.WAKE_UP                            -> WakeUpStep(viewModel)
                    PatchStep.CONNECT_NEW                        -> ConnectStep(viewModel)
                    PatchStep.REMOVE_NEEDLE_CAP                  -> RemoveNeedleCapStep(viewModel)
                    PatchStep.REMOVE_PROTECTION_TAPE             -> RemoveProtectionTapeStep(viewModel)
                    PatchStep.SAFETY_CHECK                       -> SafetyCheckStep(viewModel)
                    PatchStep.ROTATE_KNOB,
                    PatchStep.ROTATE_KNOB_NEEDLE_INSERTION_ERROR -> RotateKnobStep(viewModel)

                    PatchStep.BASAL_SCHEDULE                     -> BasalScheduleStep(viewModel)
                    PatchStep.SAFE_DEACTIVATION                  -> SafeDeactivationStep(viewModel)
                    PatchStep.MANUALLY_TURNING_OFF_ALARM         -> TurningOffAlarmStep(viewModel)
                    PatchStep.DISCARDED,
                    PatchStep.DISCARDED_FOR_CHANGE,
                    PatchStep.DISCARDED_FROM_ALARM               -> RemoveStep(viewModel)

                    // CHECK_CONNECTION is handled by EopatchActivity via checkCommunication before reaching here
                    PatchStep.CHECK_CONNECTION                   -> { /* Handled via comm check flow */
                    }

                    // Terminal steps are handled via events → Finish
                    PatchStep.COMPLETE,
                    PatchStep.FINISH,
                    PatchStep.BACK_TO_HOME,
                    PatchStep.CANCEL                             -> { /* Already emitted Finish */
                    }

                    else                                         -> { /* null/unknown - initial state */
                    }
                }
            }
        }
    }
}

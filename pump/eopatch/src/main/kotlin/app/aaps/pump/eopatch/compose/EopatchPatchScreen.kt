package app.aaps.pump.eopatch.compose

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import app.aaps.core.ui.compose.pump.WizardScreen
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
import app.aaps.pump.eopatch.compose.steps.SelectInsulinStep
import app.aaps.pump.eopatch.compose.steps.SiteLocationStep
import app.aaps.pump.eopatch.compose.steps.TurningOffAlarmStep
import app.aaps.pump.eopatch.compose.steps.WakeUpStep

@Composable
fun EopatchPatchScreen(
    viewModel: EopatchPatchViewModel,
    setToolbarConfig: ((app.aaps.core.ui.compose.ToolbarConfig) -> Unit)? = null
) {
    val patchStep by viewModel.patchStep.collectAsStateWithLifecycle()

    // Dialog states
    var showCommErrorDialog by remember { mutableStateOf(false) }
    var isForcedDiscardError by remember { mutableStateOf(false) }
    var showBondedDialog by remember { mutableStateOf(false) }
    var showChangePatchDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showForceResetDialog by remember { mutableStateOf(false) }

    // Handle events
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PatchEvent.Finish                -> { /* handled by ComposeContent */
                }

                is PatchEvent.ShowCommError         -> {
                    isForcedDiscardError = event.isForcedDiscard
                    showCommErrorDialog = true
                }

                is PatchEvent.ShowBondedDialog      -> showBondedDialog = true
                is PatchEvent.ShowChangePatchDialog -> showChangePatchDialog = true
                is PatchEvent.ShowDiscardDialog     -> showDiscardDialog = true
                is PatchEvent.ShowForceResetDialog  -> showForceResetDialog = true
            }
        }
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

    CommErrorDialog(showCommErrorDialog, isForcedDiscardError, viewModel) { showCommErrorDialog = false }
    BondedDialog(showBondedDialog, viewModel) { showBondedDialog = false }
    ChangePatchDialog(showChangePatchDialog, viewModel) { showChangePatchDialog = false }
    DiscardDialog(showDiscardDialog, viewModel) { showDiscardDialog = false }
    ForceResetDialog(showForceResetDialog, viewModel) { showForceResetDialog = false }

    // ── Screen ──────────────────────────────────────────────────────────

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    viewModel.updateIncompletePatchActivationReminder()
                    tryAwaitRelease()
                })
            }
    ) {
        WizardScreen(
            currentStep = patchStep,
            totalSteps = viewModel.totalSteps,
            currentStepIndex = viewModel.currentStepIndex,
            canGoBack = canGoBack,
            onBack = { viewModel.handleBack() },
            cancelDialogTitle = stringResource(app.aaps.core.ui.R.string.confirm),
            cancelDialogText = stringResource(
                if (patchStep in listOf(
                        PatchStep.SAFE_DEACTIVATION, PatchStep.DISCARDED, PatchStep.DISCARDED_FOR_CHANGE,
                        PatchStep.DISCARDED_FROM_ALARM, PatchStep.MANUALLY_TURNING_OFF_ALARM
                    )
                ) R.string.wizard_exit_deactivation_warning
                else R.string.wizard_exit_activation_warning
            ),
            title = stringResource(viewModel.title.collectAsStateWithLifecycle().value),
            setToolbarConfig = setToolbarConfig
        ) { step, onCancel ->
            when (step) {
                PatchStep.WAKE_UP                            -> WakeUpStep(viewModel)
                PatchStep.CONNECT_NEW                        -> ConnectStep(viewModel)
                PatchStep.SELECT_INSULIN                     -> SelectInsulinStep(viewModel)
                PatchStep.REMOVE_NEEDLE_CAP                  -> RemoveNeedleCapStep(viewModel)
                PatchStep.SITE_LOCATION                      -> SiteLocationStep(viewModel)
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

                PatchStep.CHECK_CONNECTION                   -> { /* Handled via comm check flow */
                }

                PatchStep.COMPLETE,
                PatchStep.FINISH,
                PatchStep.BACK_TO_HOME,
                PatchStep.CANCEL                             -> { /* Already emitted Finish */
                }

                else                                         -> { /* null/unknown */
                }
            }
        }
    }
}

// ── Dialog Composables ──────────────────────────────────────────────────

@Composable
private fun CommErrorDialog(
    visible: Boolean,
    isForcedDiscard: Boolean,
    viewModel: EopatchPatchViewModel,
    onDismiss: () -> Unit
) {
    if (!visible) return
    val commCheckCancelLabel = viewModel.commCheckCancelLabel
    if (isForcedDiscard) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.patch_communication_failed)) },
            text = { Text("${stringResource(R.string.patch_comm_error_during_discard_desc_2)}\n${stringResource(R.string.patch_communication_check_helper_2)}") },
            confirmButton = {
                TextButton(onClick = { onDismiss(); viewModel.discardPatch() }) { Text(stringResource(app.aaps.core.ui.R.string.discard)) }
            },
            dismissButton = {
                TextButton(onClick = { onDismiss(); viewModel.cancelPatchCommCheck() }) { Text(commCheckCancelLabel) }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.patch_communication_failed)) },
            text = { Text("${stringResource(R.string.patch_communication_check_helper_1)}\n${stringResource(R.string.patch_communication_check_helper_2)}") },
            confirmButton = {
                TextButton(onClick = { onDismiss(); viewModel.retryCheckCommunication() }) { Text(stringResource(app.aaps.core.ui.R.string.retry)) }
            },
            dismissButton = {
                TextButton(onClick = { onDismiss(); viewModel.cancelPatchCommCheck() }) { Text(commCheckCancelLabel) }
            }
        )
    }
}

@Composable
private fun BondedDialog(visible: Boolean, viewModel: EopatchPatchViewModel, onDismiss: () -> Unit) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = { },
        title = { Text(stringResource(R.string.patch_communication_succeed)) },
        text = { Text(stringResource(R.string.patch_communication_succeed_message)) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); viewModel.dismissPatchCommCheckDialogInternal(true) }) {
                Text(stringResource(app.aaps.core.ui.R.string.confirm))
            }
        }
    )
}

@Composable
private fun ChangePatchDialog(visible: Boolean, viewModel: EopatchPatchViewModel, onDismiss: () -> Unit) {
    if (!visible) return
    val message = when {
        viewModel.patchState.isBolusActive && viewModel.patchState.isTempBasalActive ->
            stringResource(R.string.patch_change_confirm_bolus_and_temp_basal_are_active_desc)

        viewModel.patchState.isBolusActive                                           -> stringResource(R.string.patch_change_confirm_bolus_is_active_desc)
        viewModel.patchState.isTempBasalActive                                       -> stringResource(R.string.patch_change_confirm_temp_basal_is_active_desc)
        else                                                                         -> stringResource(R.string.patch_change_confirm_desc)
    }
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text(stringResource(R.string.string_discard_patch)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); viewModel.deactivatePatch() }) { Text(stringResource(R.string.string_discard_patch)) }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) { Text(stringResource(app.aaps.core.ui.R.string.cancel)) }
        }
    )
}

@Composable
private fun DiscardDialog(visible: Boolean, viewModel: EopatchPatchViewModel, onDismiss: () -> Unit) {
    if (!visible) return
    val message = if (viewModel.isBolusActive)
        stringResource(R.string.patch_change_confirm_bolus_is_active_desc)
    else
        stringResource(R.string.string_are_you_sure_to_discard_current_patch)
    AlertDialog(
        onDismissRequest = { if (!viewModel.isInAlarmHandling) onDismiss() },
        title = { Text(stringResource(R.string.string_discard_patch)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
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
            }) { Text(stringResource(app.aaps.core.ui.R.string.discard)) }
        },
        dismissButton = {
            if (!viewModel.isInAlarmHandling) {
                TextButton(onClick = {
                    onDismiss()
                    viewModel.updateIncompletePatchActivationReminder()
                }) { Text(stringResource(app.aaps.core.ui.R.string.cancel)) }
            }
        }
    )
}

@Composable
private fun ForceResetDialog(visible: Boolean, viewModel: EopatchPatchViewModel, onDismiss: () -> Unit) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text(stringResource(R.string.patch_force_reset)) },
        text = { Text(stringResource(R.string.patch_force_reset_confirm)) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); viewModel.executeForceReset() }) {
                Text(stringResource(R.string.patch_force_reset))
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) { Text(stringResource(app.aaps.core.ui.R.string.cancel)) }
        }
    )
}

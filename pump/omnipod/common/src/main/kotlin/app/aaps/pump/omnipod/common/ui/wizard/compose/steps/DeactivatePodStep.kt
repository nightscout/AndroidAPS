package app.aaps.pump.omnipod.common.ui.wizard.compose.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardErrorBanner
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.omnipod.common.R
import app.aaps.pump.omnipod.common.ui.wizard.compose.ActionState
import app.aaps.core.ui.R as CoreUiR
import app.aaps.pump.omnipod.common.ui.wizard.compose.OmnipodWizardStep
import app.aaps.pump.omnipod.common.ui.wizard.compose.OmnipodWizardViewModel

/**
 * DeactivatePod action step with discard-pod branching on failure.
 *
 * On success: navigates to PodDeactivated step.
 * On failure: shows error + "Discard Pod" button (with confirmation dialog).
 * Discard calls discardPod() then navigates to PodDiscarded step.
 */
@Composable
fun DeactivatePodStep(
    viewModel: OmnipodWizardViewModel,
    textResId: Int,
    onCancel: () -> Unit
) {
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    var showDiscardDialog by remember { mutableStateOf(false) }

    // Auto-execute on first composition
    LaunchedEffect(Unit) {
        if (actionState is ActionState.Idle) {
            viewModel.executeAction()
        }
    }

    if (showDiscardDialog) {
        OkCancelDialog(
            title = stringResource(R.string.omnipod_common_pod_deactivation_wizard_discard_pod),
            message = stringResource(R.string.omnipod_common_pod_deactivation_wizard_discard_pod_confirmation),
            onConfirm = {
                showDiscardDialog = false
                viewModel.discardPod()
                viewModel.moveToStep(OmnipodWizardStep.POD_DISCARDED)
            },
            onDismiss = { showDiscardDialog = false }
        )
    }

    DeactivatePodStepContent(
        actionState = actionState,
        text = stringResource(textResId),
        onNext = { viewModel.moveToNext() },
        onDiscard = { showDiscardDialog = true },
        onCancel = onCancel
    )
}

@Composable
internal fun DeactivatePodStepContent(
    actionState: ActionState,
    text: String,
    onNext: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit
) {
    WizardStepLayout(
        primaryButton = when (actionState) {
            is ActionState.Idle,
            is ActionState.Executing -> WizardButton(
                text = stringResource(CoreUiR.string.next),
                onClick = {},
                loading = true
            )

            is ActionState.Success   -> WizardButton(
                text = stringResource(CoreUiR.string.next),
                onClick = onNext
            )

            is ActionState.Error     -> WizardButton(
                text = stringResource(R.string.omnipod_common_pod_deactivation_wizard_discard_pod),
                onClick = onDiscard
            )
        },
        secondaryButton = when (actionState) {
            is ActionState.Success -> null
            else                   -> WizardButton(
                text = stringResource(CoreUiR.string.cancel),
                onClick = onCancel,
                enabled = actionState !is ActionState.Executing
            )
        }
    ) {
        when (actionState) {
            is ActionState.Idle,
            is ActionState.Executing -> {
                Text(text = text, style = MaterialTheme.typography.bodyLarge)
            }

            is ActionState.Success   -> {
                Text(text = text, style = MaterialTheme.typography.bodyLarge)
            }

            is ActionState.Error     -> {
                WizardErrorBanner(message = (actionState as ActionState.Error).message)
            }
        }
    }
}

@Preview(showBackground = true, name = "Deactivate - In Progress")
@Composable
private fun PreviewDeactivating() {
    MaterialTheme {
        DeactivatePodStepContent(
            actionState = ActionState.Executing,
            text = "Deactivating pod...",
            onNext = {}, onDiscard = {}, onCancel = {}
        )
    }
}

@Preview(showBackground = true, name = "Deactivate - Success")
@Composable
private fun PreviewDeactivateSuccess() {
    MaterialTheme {
        DeactivatePodStepContent(
            actionState = ActionState.Idle, // Idle used as stand-in for Success in preview
            text = "Pod deactivated successfully.",
            onNext = {}, onDiscard = {}, onCancel = {}
        )
    }
}

@Preview(showBackground = true, name = "Deactivate - Error with Discard")
@Composable
private fun PreviewDeactivateError() {
    MaterialTheme {
        DeactivatePodStepContent(
            actionState = ActionState.Error("Communication failed. Pod may need to be discarded."),
            text = "Deactivating pod...",
            onNext = {}, onDiscard = {}, onCancel = {}
        )
    }
}

package app.aaps.pump.omnipod.common.ui.wizard.compose.steps

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardErrorBanner
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.omnipod.common.R
import app.aaps.pump.omnipod.common.ui.wizard.compose.ActionState
import app.aaps.core.ui.R as CoreUiR
import app.aaps.pump.omnipod.common.ui.wizard.compose.OmnipodWizardViewModel

/**
 * Shared action step composable for InitializePod and InsertCannula steps.
 *
 * Auto-executes the action on first composition, shows progress/success/error states.
 * On activation failure, can show a "Deactivate Pod" button if the pod is in alarm state.
 *
 * @param viewModel The wizard ViewModel
 * @param textResId Body text string resource (shown during execution)
 * @param onCancel Called when Cancel is pressed
 * @param showDeactivateOnFailure Whether to show deactivate button on failure (activation steps only)
 * @param onDeactivatePod Called when "Deactivate Pod" button is pressed
 */
@Composable
fun ActionStep(
    viewModel: OmnipodWizardViewModel,
    textResId: Int,
    onCancel: () -> Unit,
    showDeactivateOnFailure: Boolean = false,
    onDeactivatePod: () -> Unit = {}
) {
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()

    // Auto-execute on first composition
    LaunchedEffect(Unit) {
        if (actionState is ActionState.Idle) {
            viewModel.executeAction()
        }
    }

    val showDeactivateButton = showDeactivateOnFailure
        && actionState is ActionState.Error
        && viewModel.isPodDeactivatable()
        && (viewModel.isPodInAlarm() || viewModel.isPodActivationTimeExceeded())

    ActionStepContent(
        actionState = actionState,
        text = stringResource(textResId),
        showDeactivateButton = showDeactivateButton,
        onNext = { viewModel.moveToNext() },
        onRetry = { viewModel.executeAction() },
        onCancel = onCancel,
        onDeactivatePod = onDeactivatePod
    )
}

@Composable
internal fun ActionStepContent(
    actionState: ActionState,
    text: String,
    showDeactivateButton: Boolean = false,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDeactivatePod: () -> Unit = {},
    extraErrorContent: @Composable ColumnScope.() -> Unit = {}
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

            is ActionState.Error     -> if (showDeactivateButton) {
                WizardButton(
                    text = stringResource(R.string.omnipod_common_wizard_button_deactivate_pod),
                    onClick = onDeactivatePod
                )
            } else {
                WizardButton(
                    text = stringResource(CoreUiR.string.retry),
                    onClick = onRetry
                )
            }
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
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            is ActionState.Success   -> {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            is ActionState.Error     -> {
                WizardErrorBanner(message = actionState.message)
                extraErrorContent()
            }
        }
    }
}

@Preview(showBackground = true, name = "Action - Executing")
@Composable
private fun PreviewExecuting() {
    MaterialTheme {
        ActionStepContent(
            actionState = ActionState.Executing,
            text = "Initializing pod...",
            onNext = {}, onRetry = {}, onCancel = {}
        )
    }
}

@Preview(showBackground = true, name = "Action - Success")
@Composable
private fun PreviewSuccess() {
    MaterialTheme {
        ActionStepContent(
            actionState = ActionState.Idle, // Idle used as stand-in for Success in preview
            text = "Pod initialized successfully.",
            onNext = {}, onRetry = {}, onCancel = {}
        )
    }
}

@Preview(showBackground = true, name = "Action - Error")
@Composable
private fun PreviewError() {
    MaterialTheme {
        ActionStepContent(
            actionState = ActionState.Error("Communication error. Please retry."),
            text = "Initializing pod...",
            onNext = {}, onRetry = {}, onCancel = {}
        )
    }
}

@Preview(showBackground = true, name = "Action - Error with Deactivate")
@Composable
private fun PreviewErrorDeactivate() {
    MaterialTheme {
        ActionStepContent(
            actionState = ActionState.Error("Pod alarm triggered."),
            text = "Initializing pod...",
            showDeactivateButton = true,
            onNext = {}, onRetry = {}, onCancel = {}, onDeactivatePod = {}
        )
    }
}

package app.aaps.pump.medtrum.compose.steps

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.medtrum.R
import app.aaps.pump.medtrum.code.PatchStep
import app.aaps.pump.medtrum.compose.MedtrumPatchViewModel

@Composable
fun RetryActivationStep(
    viewModel: MedtrumPatchViewModel,
    onCancel: () -> Unit
) {
    val patchStep by viewModel.patchStep.collectAsStateWithLifecycle()
    val setupStep by viewModel.setupStep.collectAsStateWithLifecycle()

    val isRetry = patchStep == PatchStep.RETRY_ACTIVATION
    val isConnecting = patchStep == PatchStep.RETRY_ACTIVATION_CONNECT
    var showDiscardDialog by remember { mutableStateOf(false) }

    // Trigger preparePatch to disconnect on RETRY_ACTIVATION
    LaunchedEffect(patchStep) {
        if (patchStep == PatchStep.RETRY_ACTIVATION) {
            viewModel.preparePatch()
        }
    }

    // Trigger connect on RETRY_ACTIVATION_CONNECT
    LaunchedEffect(patchStep) {
        if (patchStep == PatchStep.RETRY_ACTIVATION_CONNECT) {
            viewModel.retryActivationConnect()
        }
    }

    // Auto-route based on setupStep when connecting
    LaunchedEffect(setupStep) {
        if (patchStep == PatchStep.RETRY_ACTIVATION_CONNECT) {
            when (setupStep) {
                MedtrumPatchViewModel.SetupStep.FILLED    -> viewModel.forceMoveStep(PatchStep.PRIME)
                MedtrumPatchViewModel.SetupStep.PRIMING   -> viewModel.forceMoveStep(PatchStep.PRIMING)
                MedtrumPatchViewModel.SetupStep.PRIMED    -> viewModel.forceMoveStep(PatchStep.PRIME_COMPLETE)
                MedtrumPatchViewModel.SetupStep.ACTIVATED -> viewModel.forceMoveStep(PatchStep.ACTIVATE_COMPLETE)

                else                                      -> { /* wait */
                }
            }
        }
    }

    RetryActivationStepContent(
        isRetry = isRetry,
        isConnecting = isConnecting,
        showDiscardDialog = showDiscardDialog,
        onShowDiscard = { showDiscardDialog = true },
        onConfirmDiscard = {
            showDiscardDialog = false
            viewModel.moveStep(PatchStep.FORCE_DEACTIVATION)
        },
        onDismissDiscard = { showDiscardDialog = false },
        onRetryConnect = { viewModel.moveStep(PatchStep.RETRY_ACTIVATION_CONNECT) },
        onCancel = onCancel
    )
}

@Composable
private fun RetryActivationStepContent(
    isRetry: Boolean,
    isConnecting: Boolean,
    showDiscardDialog: Boolean,
    onShowDiscard: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onDismissDiscard: () -> Unit,
    onRetryConnect: () -> Unit,
    onCancel: () -> Unit
) {
    if (showDiscardDialog) {
        OkCancelDialog(
            title = stringResource(R.string.step_retry_activation),
            message = stringResource(R.string.medtrum_deactivate_pump_confirm),
            onConfirm = onConfirmDiscard,
            onDismiss = onDismissDiscard
        )
    }

    WizardStepLayout(
        primaryButton = when {
            isRetry      -> WizardButton(
                text = stringResource(R.string.next),
                onClick = onRetryConnect
            )

            isConnecting -> WizardButton(
                text = stringResource(R.string.next),
                onClick = {},
                loading = true
            )

            else         -> null
        },
        secondaryButton = when {
            isRetry      -> WizardButton(
                text = stringResource(R.string.discard),
                onClick = onShowDiscard
            )

            isConnecting -> WizardButton(
                text = stringResource(app.aaps.core.ui.R.string.cancel),
                onClick = onCancel
            )

            else         -> null
        }
    ) {
        when {
            isRetry      -> {
                Text(
                    text = stringResource(R.string.activation_in_progress),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.press_retry_or_discard).stripHtml(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            isConnecting -> {
                Text(
                    text = stringResource(R.string.reading_activation_status),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RetryActivationStepRetryPreview() {
    RetryActivationStepContent(
        isRetry = true,
        isConnecting = false,
        showDiscardDialog = false,
        onShowDiscard = {},
        onConfirmDiscard = {},
        onDismissDiscard = {},
        onRetryConnect = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun RetryActivationStepConnectingPreview() {
    RetryActivationStepContent(
        isRetry = false,
        isConnecting = true,
        showDiscardDialog = false,
        onShowDiscard = {},
        onConfirmDiscard = {},
        onDismissDiscard = {},
        onRetryConnect = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun RetryActivationStepDiscardDialogPreview() {
    RetryActivationStepContent(
        isRetry = true,
        isConnecting = false,
        showDiscardDialog = true,
        onShowDiscard = {},
        onConfirmDiscard = {},
        onDismissDiscard = {},
        onRetryConnect = {},
        onCancel = {}
    )
}

private fun String.stripHtml(): String = this.replace(Regex("<[^>]*>"), "")

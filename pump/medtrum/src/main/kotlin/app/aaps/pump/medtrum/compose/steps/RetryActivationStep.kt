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
import app.aaps.core.ui.compose.dialogs.OkDialog
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

    val isConnecting = patchStep == PatchStep.RETRY_ACTIVATION_CONNECT
    var showDiscardDialog by remember { mutableStateOf(false) }

    LaunchedEffect(patchStep) {
        if (patchStep == PatchStep.RETRY_ACTIVATION) {
            viewModel.preparePatch()
        }
    }

    LaunchedEffect(patchStep) {
        if (patchStep == PatchStep.RETRY_ACTIVATION_CONNECT) {
            viewModel.retryActivationConnect()
        }
    }

    var showFilledErrorDialog by remember { mutableStateOf(false) }

    LaunchedEffect(setupStep) {
        if (patchStep == PatchStep.RETRY_ACTIVATION_CONNECT) {
            when (setupStep) {
                MedtrumPatchViewModel.SetupStep.FILLED    -> showFilledErrorDialog = true
                MedtrumPatchViewModel.SetupStep.PRIMING   -> viewModel.forceMoveStep(PatchStep.PRIMING)
                MedtrumPatchViewModel.SetupStep.PRIMED    -> viewModel.forceMoveStep(PatchStep.PRIME_COMPLETE)
                MedtrumPatchViewModel.SetupStep.ACTIVATED -> viewModel.forceMoveStep(PatchStep.ACTIVATE_COMPLETE)

                else                                      -> {}
            }
        }
    }

    if (showFilledErrorDialog) {
        OkDialog(
            title = stringResource(app.aaps.core.ui.R.string.error),
            message = stringResource(R.string.retry_activation_filled_error),
            onDismiss = {
                showFilledErrorDialog = false
                viewModel.moveStep(PatchStep.FORCE_DEACTIVATION)
            }
        )
    }

    if (showDiscardDialog) {
        OkCancelDialog(
            title = stringResource(R.string.step_retry_activation),
            message = stringResource(R.string.medtrum_deactivate_pump_confirm),
            onConfirm = {
                showDiscardDialog = false
                viewModel.moveStep(PatchStep.FORCE_DEACTIVATION)
            },
            onDismiss = { showDiscardDialog = false }
        )
    }

    RetryActivationContent(
        isConnecting = isConnecting,
        onRetry = { viewModel.moveStep(PatchStep.RETRY_ACTIVATION_CONNECT) },
        onDiscard = { showDiscardDialog = true },
        onCancel = onCancel
    )
}

@Composable
internal fun RetryActivationContent(
    isConnecting: Boolean,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit
) {
    WizardStepLayout(
        primaryButton = if (isConnecting) {
            WizardButton(text = stringResource(app.aaps.core.ui.R.string.next), onClick = {}, loading = true)
        } else {
            WizardButton(text = stringResource(app.aaps.core.ui.R.string.next), onClick = onRetry)
        },
        secondaryButton = if (isConnecting) {
            WizardButton(text = stringResource(app.aaps.core.ui.R.string.cancel), onClick = onCancel)
        } else {
            WizardButton(text = stringResource(app.aaps.core.ui.R.string.discard), onClick = onDiscard)
        }
    ) {
        if (isConnecting) {
            Text(
                text = stringResource(R.string.reading_activation_status),
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
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
    }
}

@Preview(showBackground = true, name = "Retry - Prompt")
@Composable
private fun PreviewRetryPrompt() {
    MaterialTheme {
        RetryActivationContent(isConnecting = false, onRetry = {}, onDiscard = {}, onCancel = {})
    }
}

@Preview(showBackground = true, name = "Retry - Connecting")
@Composable
private fun PreviewRetryConnecting() {
    MaterialTheme {
        RetryActivationContent(isConnecting = true, onRetry = {}, onDiscard = {}, onCancel = {})
    }
}

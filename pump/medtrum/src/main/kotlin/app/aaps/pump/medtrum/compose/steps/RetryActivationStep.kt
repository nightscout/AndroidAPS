package app.aaps.pump.medtrum.compose.steps

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
    var unexpectedStateMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (patchStep == PatchStep.RETRY_ACTIVATION) {
            viewModel.preparePatch()
        }
        else if (patchStep == PatchStep.RETRY_ACTIVATION_CONNECT) {
            viewModel.retryActivationConnect()
        }
    }

    LaunchedEffect(setupStep) {
        if (patchStep == PatchStep.RETRY_ACTIVATION_CONNECT) {
            when (setupStep) {
                MedtrumPatchViewModel.SetupStep.INITIAL   -> Unit
                MedtrumPatchViewModel.SetupStep.FILLED    -> viewModel.forceMoveStep(PatchStep.SELECT_INSULIN)
                MedtrumPatchViewModel.SetupStep.PRIMING   -> viewModel.forceMoveStep(PatchStep.PRIMING)
                MedtrumPatchViewModel.SetupStep.PRIMED    -> viewModel.forceMoveStep(PatchStep.PRIME_COMPLETE)
                MedtrumPatchViewModel.SetupStep.ACTIVATED -> viewModel.forceMoveStep(PatchStep.ACTIVATE_COMPLETE)

                else                                      -> unexpectedStateMessage = setupStep.toString()
            }
        }
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

    unexpectedStateMessage?.let { msg ->
        OkDialog(
            title = stringResource(app.aaps.core.ui.R.string.error),
            message = stringResource(R.string.unexpected_state, msg),
            onDismiss = {
                unexpectedStateMessage = null
                viewModel.moveStep(PatchStep.CANCEL)
            }
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
            null
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
            Spacer(Modifier.height(48.dp))
            CircularProgressIndicator(
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.CenterHorizontally)
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

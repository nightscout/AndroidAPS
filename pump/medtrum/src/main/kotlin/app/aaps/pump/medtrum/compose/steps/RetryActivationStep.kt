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

    WizardStepLayout(
        primaryButton = when {
            patchStep == PatchStep.RETRY_ACTIVATION -> WizardButton(
                text = stringResource(R.string.next),
                onClick = { viewModel.moveStep(PatchStep.RETRY_ACTIVATION_CONNECT) }
            )

            isConnecting                            -> WizardButton(
                text = stringResource(R.string.next),
                onClick = {},
                loading = true
            )

            else                                    -> null
        },
        secondaryButton = when {
            patchStep == PatchStep.RETRY_ACTIVATION -> WizardButton(
                text = stringResource(R.string.discard),
                onClick = { showDiscardDialog = true }
            )

            isConnecting                            -> WizardButton(
                text = stringResource(app.aaps.core.ui.R.string.cancel),
                onClick = onCancel
            )

            else                                    -> null
        }
    ) {
        when (patchStep) {
            PatchStep.RETRY_ACTIVATION         -> {
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

            PatchStep.RETRY_ACTIVATION_CONNECT -> {
                Text(
                    text = stringResource(R.string.reading_activation_status),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            else                               -> {}
        }
    }
}

private fun String.stripHtml(): String = this.replace(Regex("<[^>]*>"), "")

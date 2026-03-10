package app.aaps.pump.medtrum.compose.steps

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardErrorBanner
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.medtrum.R
import app.aaps.pump.medtrum.code.PatchStep
import app.aaps.pump.medtrum.compose.MedtrumPatchViewModel

@Composable
fun PrepareStep(
    viewModel: MedtrumPatchViewModel,
    onCancel: () -> Unit
) {
    val patchStep by viewModel.patchStep.collectAsStateWithLifecycle()
    val setupStep by viewModel.setupStep.collectAsStateWithLifecycle()
    val reservoirLevel by viewModel.medtrumPump.reservoirFlow.collectAsStateWithLifecycle()

    val isConnecting = patchStep == PatchStep.PREPARE_PATCH_CONNECT
    val isFilled = setupStep == MedtrumPatchViewModel.SetupStep.FILLED
    val isError = setupStep == MedtrumPatchViewModel.SetupStep.ERROR

    // Trigger preparePatch on initial display
    LaunchedEffect(Unit) {
        viewModel.preparePatch()
    }

    // When step becomes PREPARE_PATCH_CONNECT, trigger connect
    LaunchedEffect(patchStep) {
        if (patchStep == PatchStep.PREPARE_PATCH_CONNECT) {
            viewModel.preparePatchConnect()
        }
    }

    PrepareStepContent(
        isPrepare = patchStep == PatchStep.PREPARE_PATCH,
        isConnecting = isConnecting,
        isFilled = isFilled,
        isError = isError,
        pumpSN = viewModel.medtrumPump.pumpSN,
        reservoirLevel = reservoirLevel,
        pumpState = viewModel.medtrumPump.pumpState.toString(),
        onStartConnect = { viewModel.moveStep(PatchStep.PREPARE_PATCH_CONNECT) },
        onNext = { viewModel.moveStep(PatchStep.PRIME) },
        onRetry = {
            viewModel.updateSetupStep(MedtrumPatchViewModel.SetupStep.INITIAL)
            viewModel.moveStep(PatchStep.PREPARE_PATCH_CONNECT)
        },
        onCancel = onCancel
    )
}

@Composable
private fun PrepareStepContent(
    isPrepare: Boolean,
    isConnecting: Boolean,
    isFilled: Boolean,
    isError: Boolean,
    pumpSN: Long,
    reservoirLevel: Double,
    pumpState: String,
    onStartConnect: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    WizardStepLayout(
        primaryButton = when {
            isPrepare                              -> WizardButton(
                text = stringResource(R.string.next),
                onClick = onStartConnect
            )

            isConnecting && !isFilled && !isError  -> WizardButton(
                text = stringResource(R.string.next),
                onClick = {},
                loading = true
            )

            isFilled                               -> WizardButton(
                text = stringResource(R.string.next),
                onClick = onNext
            )

            isError                                -> WizardButton(
                text = stringResource(R.string.retry),
                onClick = onRetry
            )

            else                                   -> null
        },
        secondaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.cancel),
            onClick = onCancel
        )
    ) {
        if (isPrepare) {
            Text(
                text = stringResource(R.string.base_serial, pumpSN),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.patch_begin_activation).stripHtml(),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.patch_not_active_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Text(
                text = stringResource(R.string.connect_pump_base).stripHtml(),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.note_min_70_units),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (reservoirLevel > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.reservoir_text_and_level, reservoirLevel),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.do_not_attach_to_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            if (isError) {
                Spacer(Modifier.height(8.dp))
                WizardErrorBanner(message = stringResource(R.string.unexpected_state, pumpState))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PrepareStepInitialPreview() {
    PrepareStepContent(
        isPrepare = true,
        isConnecting = false,
        isFilled = false,
        isError = false,
        pumpSN = 123456789L,
        reservoirLevel = 0.0,
        pumpState = "",
        onStartConnect = {},
        onNext = {},
        onRetry = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun PrepareStepConnectingPreview() {
    PrepareStepContent(
        isPrepare = false,
        isConnecting = true,
        isFilled = false,
        isError = false,
        pumpSN = 123456789L,
        reservoirLevel = 0.0,
        pumpState = "",
        onStartConnect = {},
        onNext = {},
        onRetry = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun PrepareStepFilledPreview() {
    PrepareStepContent(
        isPrepare = false,
        isConnecting = true,
        isFilled = true,
        isError = false,
        pumpSN = 123456789L,
        reservoirLevel = 215.0,
        pumpState = "",
        onStartConnect = {},
        onNext = {},
        onRetry = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun PrepareStepErrorPreview() {
    PrepareStepContent(
        isPrepare = false,
        isConnecting = true,
        isFilled = false,
        isError = true,
        pumpSN = 123456789L,
        reservoirLevel = 0.0,
        pumpState = "STOPPED",
        onStartConnect = {},
        onNext = {},
        onRetry = {},
        onCancel = {}
    )
}

// Simple HTML tag stripper for bold-annotated resource strings
private fun String.stripHtml(): String = this.replace(Regex("<[^>]*>"), "")

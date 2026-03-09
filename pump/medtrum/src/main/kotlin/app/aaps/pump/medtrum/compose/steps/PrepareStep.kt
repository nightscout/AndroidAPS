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

    // Trigger preparePatch and load insulins on initial display
    LaunchedEffect(Unit) {
        viewModel.preparePatch()
        viewModel.loadInsulins()
    }

    // When step becomes PREPARE_PATCH_CONNECT, trigger connect
    LaunchedEffect(patchStep) {
        if (patchStep == PatchStep.PREPARE_PATCH_CONNECT) {
            viewModel.preparePatchConnect()
        }
    }

    val state = when {
        patchStep == PatchStep.PREPARE_PATCH -> PrepareState.INITIAL
        isFilled                             -> PrepareState.FILLED
        isError                              -> PrepareState.ERROR
        isConnecting                         -> PrepareState.CONNECTING
        else                                 -> PrepareState.INITIAL
    }

    PrepareStepContent(
        state = state,
        pumpSN = viewModel.medtrumPump.pumpSN,
        reservoirLevel = reservoirLevel,
        pumpState = viewModel.medtrumPump.pumpState.toString(),
        onNext = { viewModel.moveStep(PatchStep.PREPARE_PATCH_CONNECT) },
        onFilled = {
            if (viewModel.showInsulinStep) viewModel.moveStep(PatchStep.SELECT_INSULIN)
            else viewModel.moveStep(PatchStep.PRIME)
        },
        onRetry = {
            viewModel.updateSetupStep(MedtrumPatchViewModel.SetupStep.INITIAL)
            viewModel.moveStep(PatchStep.PREPARE_PATCH_CONNECT)
        },
        onCancel = onCancel
    )
}

internal enum class PrepareState { INITIAL, CONNECTING, FILLED, ERROR }

@Composable
internal fun PrepareStepContent(
    state: PrepareState,
    pumpSN: Long = 0L,
    reservoirLevel: Double = 0.0,
    pumpState: String = "",
    onNext: () -> Unit,
    onFilled: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    WizardStepLayout(
        primaryButton = when (state) {
            PrepareState.INITIAL    -> WizardButton(text = stringResource(R.string.next), onClick = onNext)
            PrepareState.CONNECTING -> WizardButton(text = stringResource(R.string.next), onClick = {}, loading = true)
            PrepareState.FILLED     -> WizardButton(text = stringResource(R.string.next), onClick = onFilled)
            PrepareState.ERROR      -> WizardButton(text = stringResource(R.string.retry), onClick = onRetry)
        },
        secondaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.cancel),
            onClick = onCancel
        )
    ) {
        when (state) {
            PrepareState.INITIAL -> {
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
            }

            PrepareState.CONNECTING,
            PrepareState.FILLED,
            PrepareState.ERROR   -> {
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
                if (state == PrepareState.ERROR) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.unexpected_state, pumpState),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Prepare - Initial")
@Composable
private fun PreviewInitial() {
    MaterialTheme {
        PrepareStepContent(state = PrepareState.INITIAL, pumpSN = 12345678L, onNext = {}, onFilled = {}, onRetry = {}, onCancel = {})
    }
}

@Preview(showBackground = true, name = "Prepare - Filled")
@Composable
private fun PreviewFilled() {
    MaterialTheme {
        PrepareStepContent(state = PrepareState.FILLED, reservoirLevel = 185.0, onNext = {}, onFilled = {}, onRetry = {}, onCancel = {})
    }
}

@Preview(showBackground = true, name = "Prepare - Error")
@Composable
private fun PreviewError() {
    MaterialTheme {
        PrepareStepContent(state = PrepareState.ERROR, pumpState = "STOPPED", onNext = {}, onFilled = {}, onRetry = {}, onCancel = {})
    }
}

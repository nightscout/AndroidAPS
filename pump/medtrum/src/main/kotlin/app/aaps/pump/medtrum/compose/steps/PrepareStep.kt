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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.pump.medtrum.R
import app.aaps.pump.medtrum.code.PatchStep
import app.aaps.pump.medtrum.compose.MedtrumPatchViewModel
import app.aaps.pump.medtrum.compose.WizardButton
import app.aaps.pump.medtrum.compose.WizardStepLayout

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

    WizardStepLayout(
        primaryButton = when {
            patchStep == PatchStep.PREPARE_PATCH  -> WizardButton(
                text = stringResource(R.string.next),
                onClick = { viewModel.moveStep(PatchStep.PREPARE_PATCH_CONNECT) }
            )

            isConnecting && !isFilled && !isError -> WizardButton(
                text = stringResource(R.string.next),
                onClick = {},
                loading = true
            )

            isFilled                              -> WizardButton(
                text = stringResource(R.string.next),
                onClick = { viewModel.moveStep(PatchStep.PRIME) }
            )

            isError                               -> WizardButton(
                text = stringResource(R.string.retry),
                onClick = {
                    viewModel.updateSetupStep(MedtrumPatchViewModel.SetupStep.INITIAL)
                    viewModel.moveStep(PatchStep.PREPARE_PATCH_CONNECT)
                }
            )

            else                                  -> null
        },
        secondaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.cancel),
            onClick = onCancel
        )
    ) {
        when (patchStep) {
            PatchStep.PREPARE_PATCH               -> {
                Text(
                    text = stringResource(R.string.base_serial, viewModel.medtrumPump.pumpSN),
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

            PatchStep.PREPARE_PATCH_CONNECT, null -> {
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
                    Text(
                        text = stringResource(R.string.unexpected_state, viewModel.medtrumPump.pumpState.toString()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            else                                  -> {}
        }
    }
}

// Simple HTML tag stripper for bold-annotated resource strings
private fun String.stripHtml(): String = this.replace(Regex("<[^>]*>"), "")

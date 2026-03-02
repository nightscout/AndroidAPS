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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.pump.medtrum.R
import app.aaps.pump.medtrum.code.PatchStep
import app.aaps.pump.medtrum.compose.MedtrumPatchViewModel
import app.aaps.pump.medtrum.compose.WizardButton
import app.aaps.pump.medtrum.compose.WizardStepLayout

@Composable
fun ActivateStep(
    viewModel: MedtrumPatchViewModel,
    onCancel: () -> Unit
) {
    val patchStep by viewModel.patchStep.collectAsStateWithLifecycle()
    val setupStep by viewModel.setupStep.collectAsStateWithLifecycle()

    val isActivating = patchStep == PatchStep.ACTIVATE
    val isComplete = patchStep == PatchStep.ACTIVATE_COMPLETE
    val isError = setupStep == MedtrumPatchViewModel.SetupStep.ERROR

    // Trigger activation when entering ACTIVATE step
    LaunchedEffect(patchStep) {
        if (patchStep == PatchStep.ACTIVATE) {
            viewModel.startActivate()
        }
    }

    // Auto-navigate on activated
    LaunchedEffect(setupStep) {
        if (setupStep == MedtrumPatchViewModel.SetupStep.ACTIVATED && patchStep == PatchStep.ACTIVATE) {
            viewModel.moveStep(PatchStep.ACTIVATE_COMPLETE)
        }
    }

    WizardStepLayout(
        primaryButton = when {
            isActivating && !isError -> WizardButton(
                text = stringResource(R.string.next),
                onClick = {},
                loading = true
            )

            isError                  -> WizardButton(
                text = stringResource(R.string.retry),
                onClick = {
                    viewModel.updateSetupStep(MedtrumPatchViewModel.SetupStep.PRIMED)
                    viewModel.moveStep(PatchStep.ACTIVATE)
                }
            )

            isComplete               -> WizardButton(
                text = stringResource(app.aaps.core.ui.R.string.ok),
                onClick = { viewModel.moveStep(PatchStep.COMPLETE) }
            )

            else                     -> null
        },
        secondaryButton = if (!isComplete) WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.cancel),
            onClick = onCancel
        ) else null
    ) {
        when {
            isActivating && !isError -> {
                Text(
                    text = stringResource(R.string.activating_pump),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            isError                  -> {
                Text(
                    text = stringResource(R.string.activating_error).stripHtml(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }

            isComplete               -> {
                Text(
                    text = stringResource(R.string.activating_complete, viewModel.medtrumPump.reservoir),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.can_export_settings),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.press_OK_to_return).stripHtml(),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun String.stripHtml(): String = this.replace(Regex("<[^>]*>"), "")

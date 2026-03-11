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

    ActivateStepContent(
        isActivating = isActivating,
        isComplete = isComplete,
        isError = isError,
        reservoirLevel = viewModel.medtrumPump.reservoir,
        onRetry = {
            viewModel.updateSetupStep(MedtrumPatchViewModel.SetupStep.PRIMED)
            viewModel.moveStep(PatchStep.ACTIVATE)
        },
        onComplete = { viewModel.moveStep(PatchStep.COMPLETE) },
        onCancel = onCancel
    )
}

@Composable
private fun ActivateStepContent(
    isActivating: Boolean,
    isComplete: Boolean,
    isError: Boolean,
    reservoirLevel: Double,
    onRetry: () -> Unit,
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    WizardStepLayout(
        primaryButton = when {
            isActivating && !isError -> WizardButton(
                text = stringResource(R.string.next),
                onClick = {},
                loading = true
            )

            isError                  -> WizardButton(
                text = stringResource(R.string.retry),
                onClick = onRetry
            )

            isComplete               -> WizardButton(
                text = stringResource(app.aaps.core.ui.R.string.ok),
                onClick = onComplete
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
                WizardErrorBanner(message = stringResource(R.string.activating_error).stripHtml())
            }

            isComplete               -> {
                Text(
                    text = stringResource(R.string.activating_complete, reservoirLevel),
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

@Preview(showBackground = true)
@Composable
private fun ActivateStepActivatingPreview() {
    ActivateStepContent(
        isActivating = true,
        isComplete = false,
        isError = false,
        reservoirLevel = 200.0,
        onRetry = {},
        onComplete = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun ActivateStepErrorPreview() {
    ActivateStepContent(
        isActivating = true,
        isComplete = false,
        isError = true,
        reservoirLevel = 200.0,
        onRetry = {},
        onComplete = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun ActivateStepCompletePreview() {
    ActivateStepContent(
        isActivating = false,
        isComplete = true,
        isError = false,
        reservoirLevel = 185.0,
        onRetry = {},
        onComplete = {},
        onCancel = {}
    )
}

private fun String.stripHtml(): String = this.replace(Regex("<[^>]*>"), "")

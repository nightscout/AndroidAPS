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
import app.aaps.core.ui.compose.dialogs.OkDialog
import app.aaps.core.ui.compose.pump.WizardButton
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

    var unexpectedStateMessage by remember { mutableStateOf<String?>(null) }

    // Trigger activation when entering ACTIVATE step
    // Note: use Unit as key to only trigger once when entering the step, not on every patchStep change
    // To avoid sending repeated commands and triggering errors and other unintended consequences
    LaunchedEffect(Unit) {
        if (patchStep == PatchStep.ACTIVATE) {
            viewModel.startActivate()
        }
    }

    // Auto-navigate on activated and handle unexpected states
    LaunchedEffect(setupStep) {
        if (!isActivating) return@LaunchedEffect
        when (setupStep) {
            MedtrumPatchViewModel.SetupStep.ACTIVATED -> {
                viewModel.executeInsulinProfileSwitch()
                viewModel.moveStep(PatchStep.ACTIVATE_COMPLETE)
            }

            MedtrumPatchViewModel.SetupStep.INITIAL,
            MedtrumPatchViewModel.SetupStep.PRIMED    -> Unit
            else                                      -> unexpectedStateMessage = setupStep.toString()
        }
    }

    val state = when {
        isActivating -> ActivateState.ACTIVATING
        isComplete   -> ActivateState.COMPLETE
        else         -> ActivateState.ACTIVATING
    }

    ActivateStepContent(
        state = state,
        reservoirLevel = viewModel.medtrumPump.reservoir,
        onComplete = { viewModel.moveToPostActivationStep() },
        onCancel = onCancel
    )

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
}

internal enum class ActivateState { ACTIVATING, COMPLETE }

@Composable
internal fun ActivateStepContent(
    state: ActivateState,
    reservoirLevel: Double = 0.0,
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    WizardStepLayout(
        primaryButton = when (state) {
            ActivateState.ACTIVATING -> WizardButton(text = stringResource(app.aaps.core.ui.R.string.next), onClick = {}, loading = true)
            ActivateState.COMPLETE   -> WizardButton(text = stringResource(app.aaps.core.ui.R.string.ok), onClick = onComplete)
        },
        secondaryButton = if (state != ActivateState.COMPLETE) WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.cancel),
            onClick = onCancel
        ) else null
    ) {
        when (state) {
            ActivateState.ACTIVATING -> {
                Text(
                    text = stringResource(R.string.activating_pump),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            ActivateState.COMPLETE   -> {
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

@Preview(showBackground = true, name = "Activate - Activating")
@Composable
private fun PreviewActivating() {
    MaterialTheme {
        ActivateStepContent(state = ActivateState.ACTIVATING, onComplete = {}, onCancel = {})
    }
}

@Preview(showBackground = true, name = "Activate - Complete")
@Composable
private fun PreviewComplete() {
    MaterialTheme {
        ActivateStepContent(state = ActivateState.COMPLETE, reservoirLevel = 200.0, onComplete = {}, onCancel = {})
    }
}
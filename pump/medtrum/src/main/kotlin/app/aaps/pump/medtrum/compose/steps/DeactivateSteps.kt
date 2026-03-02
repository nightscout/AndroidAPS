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
fun ConfirmDeactivateStep(
    viewModel: MedtrumPatchViewModel,
    onCancel: () -> Unit
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.updateSetupStep(MedtrumPatchViewModel.SetupStep.START_DEACTIVATION)
    }

    if (showConfirmDialog) {
        OkCancelDialog(
            title = stringResource(R.string.step_deactivate),
            message = stringResource(R.string.medtrum_deactivate_pump_confirm),
            onConfirm = {
                showConfirmDialog = false
                viewModel.moveStep(PatchStep.DEACTIVATE)
            },
            onDismiss = { showConfirmDialog = false }
        )
    }

    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(R.string.next),
            onClick = { showConfirmDialog = true }
        ),
        secondaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.cancel),
            onClick = onCancel
        )
    ) {
        Text(
            text = stringResource(R.string.deactivate_sure),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.press_next_or_cancel).stripHtml(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DeactivatingStep(
    viewModel: MedtrumPatchViewModel,
    onCancel: () -> Unit
) {
    val setupStep by viewModel.setupStep.collectAsStateWithLifecycle()

    val isError = setupStep == MedtrumPatchViewModel.SetupStep.ERROR

    // Trigger deactivation
    LaunchedEffect(Unit) {
        viewModel.deactivatePatch()
    }

    // Auto-navigate on stopped
    LaunchedEffect(setupStep) {
        if (setupStep == MedtrumPatchViewModel.SetupStep.STOPPED) {
            viewModel.moveStep(PatchStep.DEACTIVATION_COMPLETE)
        }
    }

    WizardStepLayout(
        primaryButton = if (isError) WizardButton(
            text = stringResource(R.string.discard),
            onClick = { viewModel.moveStep(PatchStep.FORCE_DEACTIVATION) }
        ) else WizardButton(
            text = stringResource(R.string.next),
            onClick = {},
            loading = true
        ),
        secondaryButton = if (isError) WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.cancel),
            onClick = onCancel
        ) else null
    ) {
        if (isError) {
            Text(
                text = stringResource(R.string.deactivating_error).stripHtml(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Text(
                text = stringResource(R.string.deactivating_pump),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun DeactivateCompleteStep(
    viewModel: MedtrumPatchViewModel
) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(R.string.next),
            onClick = { viewModel.moveStep(PatchStep.PREPARE_PATCH) }
        ),
        secondaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.ok),
            onClick = { viewModel.moveStep(PatchStep.COMPLETE) }
        )
    ) {
        Text(
            text = stringResource(R.string.retract_needle),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.remove_base_discard_patch),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.press_next_or_OK).stripHtml(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun String.stripHtml(): String = this.replace(Regex("<[^>]*>"), "")

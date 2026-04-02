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
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardErrorBanner
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

    ConfirmDeactivateStepContent(
        showConfirmDialog = showConfirmDialog,
        onShowDialog = { showConfirmDialog = true },
        onConfirm = {
            showConfirmDialog = false
            viewModel.moveStep(PatchStep.DEACTIVATE)
        },
        onDismissDialog = { showConfirmDialog = false },
        onCancel = onCancel
    )
}

@Composable
private fun ConfirmDeactivateStepContent(
    showConfirmDialog: Boolean,
    onShowDialog: () -> Unit,
    onConfirm: () -> Unit,
    onDismissDialog: () -> Unit,
    onCancel: () -> Unit
) {
    if (showConfirmDialog) {
        OkCancelDialog(
            title = stringResource(R.string.step_deactivate),
            message = stringResource(R.string.medtrum_deactivate_pump_confirm),
            onConfirm = onConfirm,
            onDismiss = onDismissDialog
        )
    }

    ConfirmDeactivateContent(
        onNext = onShowDialog,
        onCancel = onCancel
    )
}

@Composable
internal fun ConfirmDeactivateContent(
    onNext: () -> Unit,
    onCancel: () -> Unit
) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.next),
            onClick = onNext
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

    LaunchedEffect(Unit) {
        viewModel.deactivatePatch()
    }

    LaunchedEffect(setupStep) {
        if (setupStep == MedtrumPatchViewModel.SetupStep.STOPPED) {
            viewModel.moveStep(PatchStep.DEACTIVATION_COMPLETE)
        }
    }

    DeactivatingContent(
        isError = isError,
        onDiscard = { viewModel.moveStep(PatchStep.FORCE_DEACTIVATION) },
        onCancel = onCancel
    )
}

@Composable
internal fun DeactivatingContent(
    isError: Boolean,
    onDiscard: () -> Unit,
    onCancel: () -> Unit
) {
    WizardStepLayout(
        primaryButton = if (isError) WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.discard),
            onClick = onDiscard
        ) else WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.next),
            onClick = {},
            loading = true
        ),
        secondaryButton = if (isError) WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.cancel),
            onClick = onCancel
        ) else null
    ) {
        if (isError) {
            WizardErrorBanner(message = stringResource(R.string.deactivating_error).stripHtml())
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
    DeactivateCompleteContent(
        onNewPatch = { viewModel.moveStep(PatchStep.PREPARE_PATCH) },
        onDone = { viewModel.moveStep(PatchStep.COMPLETE) }
    )
}

@Composable
internal fun DeactivateCompleteContent(
    onNewPatch: () -> Unit,
    onDone: () -> Unit
) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.next),
            onClick = onNewPatch
        ),
        secondaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.ok),
            onClick = onDone
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

// region Previews

@Preview(showBackground = true, name = "Confirm Deactivate")
@Composable
private fun PreviewConfirmDeactivate() {
    MaterialTheme {
        ConfirmDeactivateContent(onNext = {}, onCancel = {})
    }
}

@Preview(showBackground = true, name = "Deactivating - In Progress")
@Composable
private fun PreviewDeactivating() {
    MaterialTheme {
        DeactivatingContent(isError = false, onDiscard = {}, onCancel = {})
    }
}

@Preview(showBackground = true, name = "Deactivating - Error")
@Composable
private fun PreviewDeactivatingError() {
    MaterialTheme {
        DeactivatingContent(isError = true, onDiscard = {}, onCancel = {})
    }
}

@Preview(showBackground = true, name = "Deactivate Complete")
@Composable
private fun PreviewDeactivateComplete() {
    MaterialTheme {
        DeactivateCompleteContent(onNewPatch = {}, onDone = {})
    }
}

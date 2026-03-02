package app.aaps.pump.eopatch.compose.steps

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.eopatch.R
import app.aaps.pump.eopatch.code.PatchStep
import app.aaps.pump.eopatch.compose.EopatchPatchViewModel
import app.aaps.pump.eopatch.compose.EopatchPatchViewModel.SetupStep

@Composable
fun RotateKnobStep(viewModel: EopatchPatchViewModel) {
    val patchStep by viewModel.patchStep.collectAsStateWithLifecycle()
    val setupStep by viewModel.setupStep.collectAsStateWithLifecycle()
    var showActivationFailedDialog by remember { mutableStateOf(false) }

    val isNeedleInsertionError = patchStep == PatchStep.ROTATE_KNOB_NEEDLE_INSERTION_ERROR

    LaunchedEffect(Unit) {
        viewModel.initPatchStep()
    }

    // Handle setup step changes
    LaunchedEffect(setupStep) {
        when (setupStep) {
            // Needle sensing failed → wrap retry in checkCommunication like old fragment
            SetupStep.NEEDLE_SENSING_FAILED -> viewModel.checkCommunication(
                onSuccessListener = { viewModel.startNeedleSensing() },
                onCancelListener = { viewModel.handleCancel() }
            )

            // Activation failed → show dedicated error dialog
            SetupStep.ACTIVATION_FAILED     -> showActivationFailedDialog = true
            else                            -> Unit
        }
    }

    // Activation failed dialog — different from comm check dialog
    if (showActivationFailedDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.needle_insertion_error_1)) },
            text = { Text(stringResource(R.string.needle_insertion_error_3)) },
            confirmButton = {
                TextButton(onClick = {
                    showActivationFailedDialog = false
                    viewModel.startNeedleSensing()
                }) { Text(stringResource(R.string.retry)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showActivationFailedDialog = false
                    viewModel.handleCancel()
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    WizardStepLayout(
        primaryButton = WizardButton(
            text = if (isNeedleInsertionError) stringResource(R.string.retry) else stringResource(R.string.next),
            onClick = { viewModel.startNeedleSensing() }
        )
    ) {
        Text(
            text = stringResource(R.string.patch_rotate_knob),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(R.string.patch_rotate_knob_desc_1),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = if (isNeedleInsertionError)
                stringResource(R.string.patch_rotate_knob_desc_2_needle_insertion_error)
            else
                stringResource(R.string.patch_rotate_knob_desc_2),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

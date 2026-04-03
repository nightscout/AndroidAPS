package app.aaps.pump.eopatch.compose.steps

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.eopatch.R
import app.aaps.pump.eopatch.compose.EopatchPatchViewModel
import app.aaps.pump.eopatch.compose.EopatchPatchViewModel.SetupStep

@Composable
fun SafetyCheckStep(viewModel: EopatchPatchViewModel) {
    val setupStep by viewModel.setupStep.collectAsStateWithLifecycle()
    val progress by viewModel.safetyCheckProgress.collectAsStateWithLifecycle()
    var showRetryDialog by remember { mutableStateOf(false) }
    var hasStarted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasStarted) {
            hasStarted = true
            viewModel.initPatchStep()
            viewModel.startSafetyCheck()
        }
    }

    LaunchedEffect(setupStep) {
        if (setupStep == SetupStep.SAFETY_CHECK_FAILED) {
            showRetryDialog = true
        }
    }

    if (showRetryDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.patch_communication_failed)) },
            text = { Text(stringResource(R.string.patch_safety_check_failed_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showRetryDialog = false
                    viewModel.retrySafetyCheck()
                }) { Text(stringResource(app.aaps.core.ui.R.string.retry)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRetryDialog = false
                    viewModel.handleCancel()
                }) { Text(stringResource(app.aaps.core.ui.R.string.cancel)) }
            }
        )
    }

    WizardStepLayout {
        Text(
            text = stringResource(R.string.patch_safety_check),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(R.string.patch_safety_check_desc_1),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.patch_safety_check_desc_2),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

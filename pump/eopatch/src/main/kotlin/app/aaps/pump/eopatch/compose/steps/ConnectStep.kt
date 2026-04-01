package app.aaps.pump.eopatch.compose.steps

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
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
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.eopatch.R
import app.aaps.pump.eopatch.code.PatchStep
import app.aaps.pump.eopatch.compose.EopatchPatchViewModel
import app.aaps.pump.eopatch.compose.EopatchPatchViewModel.SetupStep

@Composable
fun ConnectStep(viewModel: EopatchPatchViewModel) {
    val setupStep by viewModel.setupStep.collectAsStateWithLifecycle()

    // Start scan on composition
    LaunchedEffect(Unit) {
        viewModel.startScan()
    }

    // Handle setup step changes — wrap retries in checkCommunication like old fragment
    LaunchedEffect(setupStep) {
        when (setupStep) {
            SetupStep.SCAN_FAILED,
            SetupStep.BONDING_FAILED -> viewModel.checkCommunication(
                onSuccessListener = { viewModel.retryScan() },
                onCancelListener = { viewModel.moveStep(PatchStep.WAKE_UP) }
            )

            SetupStep.GET_PATCH_INFO_FAILED -> viewModel.checkCommunication(
                onSuccessListener = { viewModel.getPatchInfo() },
                onCancelListener = { viewModel.moveStep(PatchStep.WAKE_UP) }
            )

            SetupStep.SELF_TEST_FAILED -> viewModel.checkCommunication(
                onSuccessListener = { viewModel.selfTest() },
                onCancelListener = { viewModel.moveStep(PatchStep.WAKE_UP) }
            )

            else -> Unit
        }
    }

    val isScanning = setupStep in listOf(
        SetupStep.SCAN_STARTED, SetupStep.BONDING_STARTED,
        SetupStep.GET_PATCH_INFO_STARTED, SetupStep.SELF_TEST_STARTED
    )

    WizardStepLayout {
        Text(
            text = stringResource(R.string.patch_connect_new),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(R.string.patch_connect_new_desc),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(R.string.patch_wake_up_pairing_info),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (isScanning) {
            CircularProgressIndicator()
        }
    }
}

@Preview(showBackground = true, name = "Connect - Scanning")
@Composable
private fun ConnectStepScanningPreview() {
    MaterialTheme {
        WizardStepLayout {
            Text(text = stringResource(R.string.patch_connect_new), style = MaterialTheme.typography.titleLarge)
            Text(text = stringResource(R.string.patch_connect_new_desc), style = MaterialTheme.typography.bodyMedium)
            Text(text = stringResource(R.string.patch_wake_up_pairing_info), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
        }
    }
}

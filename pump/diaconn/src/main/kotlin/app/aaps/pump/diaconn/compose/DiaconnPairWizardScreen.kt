package app.aaps.pump.diaconn.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.pump.BleScanStep
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardScreen
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.diaconn.R

@Composable
fun DiaconnPairWizardScreen(
    viewModel: DiaconnPairWizardViewModel,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    WizardScreen(
        currentStep = state.step,
        totalSteps = DiaconnPairStep.entries.size,
        currentStepIndex = state.step.ordinal,
        canGoBack = true,
        onBack = onCancel,
        cancelDialogTitle = stringResource(R.string.diaconn_pairing),
        cancelDialogText = stringResource(app.aaps.core.ui.R.string.cancel)
    ) { step, wizardCancel ->
        when (step) {
            DiaconnPairStep.BLE_SCAN -> {
                BleScanStep(
                    devices = state.devices,
                    onSelectDevice = viewModel::selectDevice,
                    onStartScan = viewModel::startScan,
                    onStopScan = viewModel::stopScan,
                    onCancel = wizardCancel
                )
            }

            DiaconnPairStep.COMPLETE -> {
                PairingCompleteStep(
                    onFinish = {
                        viewModel.finish()
                        onFinish()
                    }
                )
            }
        }
    }
}

@Composable
private fun PairingCompleteStep(
    onFinish: () -> Unit
) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.ok),
            onClick = onFinish
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.diaconn_pairing_successful),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.diaconn_pairing_successful_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

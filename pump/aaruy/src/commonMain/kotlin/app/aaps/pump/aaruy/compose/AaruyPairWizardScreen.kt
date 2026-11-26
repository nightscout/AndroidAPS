package app.aaps.pump.aaruy.compose

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
import app.aaps.pump.aaruy.R

@Composable
fun AaruyPairWizardScreen(
    viewModel: AaruyPairWizardViewModel,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val devices by viewModel.scannedDevices.collectAsStateWithLifecycle()

    MyLoadingContainer(
        show = viewModel.showLoading.value,
    ) {
        WizardScreen(
            currentStep = state.step,
            totalSteps = AaruyPairWizardStep.entries.size,
            currentStepIndex = state.step.ordinal,
            canGoBack = true,
            onBack = onCancel,
            cancelDialogTitle = stringResource(R.string.aaruy_pairing),
            cancelDialogText = stringResource(app.aaps.core.ui.R.string.cancel)
        ) { step, wizardCancel ->
            when (step) {
                AaruyPairWizardStep.BLE_SCAN -> {
                    BleScanStep(
                        devices = devices,
                        onSelectDevice = viewModel::onDeviceSelected,
                        onStartScan = viewModel::startDeviceScan,
                        onStopScan = viewModel::stopDeviceScan,
                        onCancel = wizardCancel
                    )
                }

                AaruyPairWizardStep.COMPLETE -> {
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
                text = stringResource(R.string.bind_ok),
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}
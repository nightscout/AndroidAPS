package app.aaps.pump.common.compose

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
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.pump.BleScanStep
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardScreen
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.common.hw.rileylink.R

@Composable
fun RileyLinkPairWizardScreen(
    viewModel: RileyLinkPairWizardViewModel,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    WizardScreen(
        currentStep = state.step,
        totalSteps = RileyLinkPairStep.entries.size,
        currentStepIndex = state.step.ordinal,
        canGoBack = true,
        onBack = {
            viewModel.cancel()
            onCancel()
        },
        cancelDialogTitle = stringResource(R.string.rileylink_pair),
        cancelDialogText = stringResource(app.aaps.core.ui.R.string.cancel)
    ) { step, wizardCancel ->
        when (step) {
            RileyLinkPairStep.BLE_SCAN -> {
                BleScanStep(
                    devices = state.devices,
                    onSelectDevice = viewModel::selectDevice,
                    onStartScan = viewModel::startScan,
                    onStopScan = viewModel::stopScan,
                    onCancel = wizardCancel,
                    title = stringResource(R.string.rileylink_pair),
                    subtitle = stringResource(R.string.riley_link_ble_config_scan_scanning)
                )
            }

            RileyLinkPairStep.COMPLETE -> {
                WizardStepLayout(
                    primaryButton = WizardButton(
                        text = stringResource(app.aaps.core.ui.R.string.ok),
                        onClick = {
                            viewModel.finish()
                            onFinish()
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AapsSpacing.extraLarge)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.rileylink_pair_success),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        state.selectedDevice?.let { device ->
                            Text(
                                text = "${device.name}\n${device.address}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

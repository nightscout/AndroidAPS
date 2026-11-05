package app.aaps.pump.aaruy.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.pump.BleScanStep
import app.aaps.core.ui.compose.pump.ProfileGateWizardStep
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardScreen
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.aaruy.R
import app.aaps.core.ui.R as CoreUiR

@Composable
fun AaruyPairWizardScreen(
    viewModel: AaruyPairWizardViewModel,
    title: String,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val steps by viewModel.steps.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) { viewModel.cancelled.collect { onCancel() } }

    // Not known yet whether the profile step comes first.
    val current = step ?: return

    WizardScreen(
        currentStep = current,
        totalSteps = steps.size,
        currentStepIndex = steps.indexOf(current).coerceAtLeast(0),
        canGoBack = current != AaruyPairStep.COMPLETE,
        onBack = {
            viewModel.cancel()
            onCancel()
        },
        cancelDialogTitle = stringResource(R.string.aaruy_pairing),
        cancelDialogText = stringResource(CoreUiR.string.cancel),
        title = title,
        setToolbarConfig = setToolbarConfig
    ) { shown, wizardCancel ->
        when (shown) {
            AaruyPairStep.PROFILE_GATE -> ProfileGateWizardStep(host = viewModel)

            AaruyPairStep.BLE_SCAN     -> BleScanStep(
                devices = devices,
                onSelectDevice = viewModel::selectDevice,
                onStartScan = viewModel::startScan,
                onStopScan = viewModel::stopScan,
                onCancel = wizardCancel
            )

            AaruyPairStep.PRESS_BUTTON -> WizardStepLayout(
                secondaryButton = WizardButton(text = stringResource(CoreUiR.string.cancel), onClick = wizardCancel)
            ) {
                PairMessage(
                    icon = { Icon(Icons.Filled.TouchApp, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary) },
                    title = stringResource(R.string.aaruy_press_pump_button),
                    text = stringResource(R.string.aaruy_press_pump_button_detail)
                )
            }

            AaruyPairStep.COMPLETE     -> WizardStepLayout(
                primaryButton = WizardButton(text = stringResource(CoreUiR.string.ok), onClick = onFinish)
            ) {
                PairMessage(
                    icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary) },
                    title = stringResource(R.string.aaruy_pairing_successful),
                    text = stringResource(R.string.aaruy_pairing_successful_detail)
                )
            }
        }
    }
}

@Composable
private fun PairMessage(icon: @Composable () -> Unit, title: String, text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        icon()
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

package app.aaps.pump.danar.compose

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardErrorBanner
import app.aaps.core.ui.compose.pump.WizardScreen
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.dana.R

@Composable
fun DanaRPairWizardScreen(
    viewModel: DanaRPairWizardViewModel,
    onCancel: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val stepIndex = when (state.step) {
        PairWizardStep.CONFIGURE  -> 0
        PairWizardStep.CONNECTING -> 1
        PairWizardStep.COMPLETE   -> 2
        PairWizardStep.ERROR      -> 1
    }

    WizardScreen(
        currentStep = state.step,
        totalSteps = 3,
        currentStepIndex = stepIndex,
        canGoBack = state.step == PairWizardStep.CONFIGURE,
        onBack = onCancel,
        cancelDialogTitle = stringResource(R.string.cancel_pairing_title),
        cancelDialogText = stringResource(R.string.cancel_pairing_message)
    ) { step, onCancelDialog ->
        when (step) {
            PairWizardStep.CONFIGURE  -> ConfigureStep(
                state = state,
                onPasswordChange = viewModel::updatePassword,
                onRefreshDevices = viewModel::refreshBondedDevices,
                onSelectDevice = viewModel::onDeviceSelected,
                onPair = viewModel::pair,
                onCancel = onCancelDialog
            )

            PairWizardStep.CONNECTING -> ConnectingStep(
                state = state,
                onCancel = onCancel
            )

            PairWizardStep.COMPLETE   -> CompleteStep(
                onDone = viewModel::finish
            )

            PairWizardStep.ERROR      -> ErrorStep(
                onGoBack = viewModel::goBack,
                onCancel = onCancel
            )
        }
    }
}

@Composable
private fun ConfigureStep(
    state: DanaRPairWizardUiState,
    onPasswordChange: (String) -> Unit,
    onRefreshDevices: () -> Unit,
    onSelectDevice: (BondedDevice) -> Unit,
    onPair: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    WizardStepLayout(
        scrollable = false,
        primaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.pairing),
            onClick = onPair,
            enabled = state.password.isNotEmpty() && state.selectedDevice != null
        ),
        secondaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.cancel),
            onClick = onCancel
        )
    ) {
        // Password input
        Text(
            text = stringResource(R.string.enter_pump_password),
            style = MaterialTheme.typography.bodyLarge
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.danar_password_title)) },
            placeholder = { Text(stringResource(R.string.password_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Open Bluetooth settings button
        OutlinedButton(
            onClick = {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Filled.Bluetooth,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(stringResource(R.string.open_bluetooth_settings))
        }

        HorizontalDivider()

        // Device list header with refresh
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.select_paired_device),
                style = MaterialTheme.typography.bodyLarge
            )
            IconButton(onClick = onRefreshDevices) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(app.aaps.core.ui.R.string.refresh))
            }
        }

        // Device list
        if (state.bondedDevices.isEmpty()) {
            Text(
                text = stringResource(R.string.no_paired_devices),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(state.bondedDevices, key = { it.address }) { device ->
                    DeviceItem(
                        device = device,
                        selected = state.selectedDevice == device,
                        onClick = { onSelectDevice(device) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun DeviceItem(
    device: BondedDevice,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Bluetooth,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConnectingStep(
    state: DanaRPairWizardUiState,
    onCancel: () -> Unit
) {
    WizardStepLayout(
        secondaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.cancel),
            onClick = onCancel
        )
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        CircularProgressIndicator(
            modifier = Modifier
                .size(64.dp)
                .align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.connecting_verifying),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        state.selectedDevice?.let {
            Text(
                text = it.name,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CompleteStep(
    onDone: () -> Unit
) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(R.string.done),
            onClick = onDone
        )
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.password_ok),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ErrorStep(
    onGoBack: () -> Unit,
    onCancel: () -> Unit
) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.back),
            onClick = onGoBack
        ),
        secondaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.cancel),
            onClick = onCancel
        )
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        WizardErrorBanner(message = stringResource(R.string.password_wrong))
    }
}

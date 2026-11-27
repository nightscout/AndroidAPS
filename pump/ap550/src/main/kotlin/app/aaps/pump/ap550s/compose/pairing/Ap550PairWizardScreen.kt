package app.aaps.pump.ap550s.compose.pairing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.R as CoreUiR
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.pump.BluetoothPermissionsHost
import app.aaps.core.ui.compose.pump.KeepScreenOnEffect
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.ap550s.R

/**
 * Pairs with a pump over Bluetooth.
 *
 * The wizard shows one step at a time: look for pumps, connect, type the code shown on the pump,
 * done. A pump can also be found by reading the QR code on it, which is handed to the host through
 * [Ap550PairEvent.StartQrScan] so this screen stays free of camera code.
 */
@Composable
fun Ap550PairWizardScreen(
    viewModel: Ap550PairWizardViewModel,
    onFinish: () -> Unit,
    onStartQrScan: () -> Unit
) {
    KeepScreenOnEffect()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                Ap550PairEvent.Finish -> onFinish()
                Ap550PairEvent.StartQrScan -> onStartQrScan()
            }
        }
    }

    BluetoothPermissionsHost(
        deniedContent = { requestAgain ->
            PermissionsDeniedSection(onRetry = requestAgain, onGoBack = viewModel::cancel)
        },
        content = {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            when (state.step) {
                Ap550PairStep.BLE_SCAN -> ScanSection(
                    state = state,
                    onStartScan = viewModel::startScan,
                    onStopScan = viewModel::stopScan,
                    onSelectDevice = viewModel::onDeviceSelected,
                    onStartQrScan = onStartQrScan,
                    onCancel = viewModel::cancel
                )

                Ap550PairStep.CONNECTING -> ConnectingSection(
                    device = state.selectedDevice,
                    onCancel = viewModel::cancel
                )

                Ap550PairStep.PAIR_CODE -> PairCodeSection(
                    state = state,
                    onCodeChanged = viewModel::onPairingCodeChanged,
                    onSubmitCode = viewModel::submitPairingCode,
                    onCancel = viewModel::cancel
                )

                Ap550PairStep.COMPLETE -> CompleteSection(onOk = viewModel::finish)
            }
        }
    )
}

/** Step one: a scan of nearby pumps, with the QR code as the other way in. */
@Composable
private fun ScanSection(
    state: Ap550PairUiState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onSelectDevice: (Ap550ScannedDevice) -> Unit,
    onStartQrScan: () -> Unit,
    onCancel: () -> Unit
) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(
                if (state.isScanning) R.string.ap550_ble_config_scan_stop else R.string.ap550_ble_config_button_scan_start
            ),
            onClick = if (state.isScanning) onStopScan else onStartScan
        ),
        secondaryButton = WizardButton(
            text = stringResource(CoreUiR.string.cancel),
            onClick = onCancel
        )
    ) {
        // Title row with the QR entry at the right, so the two ways to connect read apart at a
        // glance and the QR button does not sit between the scan status and the device list.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.ap550_ble_config_scan_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onStartQrScan) {
                Icon(imageVector = Icons.Filled.QrCodeScanner, contentDescription = null)
                Text(text = stringResource(R.string.ap550_ble_config_qr_scan_title))
            }
        }

        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                // Pure red instead of the muted Material error tone. The pairing code error also
                // lands here when a second wrong code drops the link and the wizard goes back
                // to the scan step.
                color = if (state.isError) AapsTheme.generalColors.statusCritical else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // The scan state belongs to the "Nearby devices" title: one line reads
        // "Nearby devices (Scanning)" and the progress bar takes the rest of the row.
        val statusText = stringResource(
            if (state.isScanning) R.string.ap550_ble_config_scan_scanning
            else R.string.ap550_ble_config_scan_finished
        )
        val statusColor = if (state.isScanning) {
            MaterialTheme.colorScheme.primary
        } else {
            AapsTheme.generalColors.statusNormal
        }
        val titleText = stringResource(R.string.ap550_ble_config_nearby_devices_with_status, statusText)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            Text(
                text = buildAnnotatedString {
                    append(titleText)
                    // Colour only the scan state inside the brackets: accent while scanning,
                    // green once the scan has finished.
                    val start = titleText.lastIndexOf(statusText)
                    if (start >= 0) addStyle(SpanStyle(color = statusColor), start, start + statusText.length)
                },
                style = MaterialTheme.typography.titleSmall
            )
            if (state.isScanning) {
                LinearProgressIndicator(modifier = Modifier.weight(1f))
            }
        }
        if (state.devices.isEmpty()) {
            Text(
                text = stringResource(R.string.ap550_ble_config_no_ap550_selected),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // A plain column, not a lazy list: the pump list is short and the step already scrolls.
        Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
            state.devices.forEach { device ->
                DeviceRow(
                    device = device,
                    selected = device == state.selectedDevice,
                    onClick = { onSelectDevice(device) }
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: Ap550ScannedDevice,
    selected: Boolean,
    onClick: () -> Unit
) {
    AapsCard(
        selected = selected,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        // Bluetooth icon at the left and an arrow at the right say "this row opens something",
        // which a bare text card did not.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AapsSpacing.large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            Icon(
                imageVector = Icons.Filled.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = device.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = device.serialNumber ?: stringResource(CoreUiR.string.unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Step two: the link is coming up and the pump is asked to start pairing. */
@Composable
private fun ConnectingSection(
    device: Ap550ScannedDevice?,
    onCancel: () -> Unit
) {
    WizardStepLayout(
        secondaryButton = WizardButton(
            text = stringResource(R.string.ap550_ble_device_cancel_pair),
            onClick = onCancel
        )
    ) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
            text = stringResource(R.string.ap550_ble_is_connecting),
            style = MaterialTheme.typography.bodyMedium
        )
        device?.let {
            Text(text = it.name, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Step three: the code shown on the pump. */
@Composable
private fun PairCodeSection(
    state: Ap550PairUiState,
    onCodeChanged: (String) -> Unit,
    onSubmitCode: () -> Unit,
    onCancel: () -> Unit
) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(R.string.ap550_ble_device_pair),
            enabled = state.pairingCode.length == Ap550PairWizardViewModel.PAIRING_CODE_LENGTH,
            onClick = onSubmitCode
        ),
        secondaryButton = WizardButton(
            text = stringResource(R.string.ap550_ble_device_cancel_pair),
            onClick = onCancel
        )
    ) {
        Text(text = stringResource(R.string.ap550_ble_device_paired_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.ap550_ble_connection_authentication),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = state.pairingCode,
            onValueChange = onCodeChanged,
            label = { Text(text = stringResource(R.string.ap550_ble_device_input_pairing_code)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                // Pure red instead of the muted Material error tone, so a wrong code is hard to miss.
                color = if (state.isError) AapsTheme.generalColors.statusCritical else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Last step: the pump accepted the code. */
@Composable
private fun CompleteSection(onOk: () -> Unit) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(CoreUiR.string.ok),
            onClick = onOk
        )
    ) {
        Text(
            text = stringResource(R.string.ap550s_pair_success),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** Shown instead of the wizard when the Bluetooth permissions were refused. */
@Composable
private fun PermissionsDeniedSection(
    onRetry: () -> Unit,
    onGoBack: () -> Unit
) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(CoreUiR.string.retry),
            onClick = onRetry
        ),
        secondaryButton = WizardButton(
            text = stringResource(CoreUiR.string.cancel),
            onClick = onGoBack
        )
    ) {
        Text(
            text = stringResource(CoreUiR.string.ble_permissions_missing),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
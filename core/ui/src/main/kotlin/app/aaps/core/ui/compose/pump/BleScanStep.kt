package app.aaps.core.ui.compose.pump

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.core.ui.R

/**
 * Shared BLE device scan step for pump pairing wizards.
 *
 * Displays a list of discovered BLE devices. Automatically starts scanning
 * when mounted. User taps a device to select it.
 *
 * @param devices list of discovered devices (collected from BleScanner flow by the caller)
 * @param onSelectDevice called when user taps a device
 * @param onStartScan called to start BLE scanning (triggered automatically on mount)
 * @param onStopScan called to stop BLE scanning (triggered on dispose)
 * @param onCancel called when user cancels
 * @param deviceNameFilter optional regex to filter devices by name — only matching devices are shown
 * @param title optional title text override
 * @param subtitle optional subtitle text override
 */
@Composable
fun BleScanStep(
    devices: List<ScannedDevice>,
    onSelectDevice: (ScannedDevice) -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit = {},
    onCancel: () -> Unit,
    deviceNameFilter: Regex? = null,
    title: String = stringResource(R.string.ble_scan_select_pump),
    subtitle: String = stringResource(R.string.ble_scan_scanning)
) {
    DisposableEffect(Unit) {
        onStartScan()
        onDispose { onStopScan() }
    }

    val filteredDevices = if (deviceNameFilter != null) {
        devices.filter { deviceNameFilter.containsMatchIn(it.name) }
    } else {
        devices
    }

    WizardStepLayout(
        scrollable = false,
        secondaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.cancel),
            onClick = onCancel
        )
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filteredDevices, key = { it.address }) { device ->
                DeviceItem(device = device, onClick = { onSelectDevice(device) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun DeviceItem(
    device: ScannedDevice,
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
            tint = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

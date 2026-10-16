package app.aaps.pump.equil.compose.steps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.pump.BleScanStep
import app.aaps.pump.equil.compose.EquilWizardViewModel

/**
 * Equil-specific wrapper around the shared [BleScanStep].
 * Filters for devices with names starting with "Equil".
 */
@Composable
internal fun BleScanStepWrapper(
    viewModel: EquilWizardViewModel,
    onCancel: () -> Unit
) {
    val devices by viewModel.scannedDevices.collectAsStateWithLifecycle()

    BleScanStep(
        devices = devices,
        onSelectDevice = { device -> viewModel.onDeviceSelected(device) },
        onStartScan = { viewModel.startDeviceScan() },
        onStopScan = { viewModel.stopDeviceScan() },
        onCancel = onCancel,
        deviceNameFilter = Regex("^Equil")
    )
}

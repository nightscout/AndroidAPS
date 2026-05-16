package app.aaps.pump.medtrum.compose.steps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.pump.BleScanStep
import app.aaps.pump.medtrum.compose.MedtrumPatchViewModel

@Composable
internal fun BleScanStepWrapper(
    viewModel: MedtrumPatchViewModel,
    onCancel: () -> Unit
) {
    val devices by viewModel.scannedDevices.collectAsStateWithLifecycle()

    BleScanStep(
        devices = devices,
        onSelectDevice = { device -> viewModel.onDeviceSelected(device) },
        onStartScan = { viewModel.startDeviceScan() },
        onStopScan = { viewModel.stopDeviceScan() },
        onCancel = onCancel,
        deviceNameFilter = Regex("^MT")
    )
}

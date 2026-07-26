package app.aaps.pump.common.compose

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.common.hw.rileylink.RileyLinkConst
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import app.aaps.pump.common.hw.rileylink.ble.data.GattAttributes
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkPumpDevice
import app.aaps.pump.common.hw.rileylink.keys.RileyLinkStringKey
import app.aaps.pump.common.hw.rileylink.keys.RileyLinkStringPreferenceKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class RileyLinkPairStep {
    BLE_SCAN,
    COMPLETE
}

data class RileyLinkPairWizardUiState(
    val step: RileyLinkPairStep = RileyLinkPairStep.BLE_SCAN,
    val devices: List<ScannedDevice> = emptyList(),
    val selectedDevice: ScannedDevice? = null
)

sealed class RileyLinkPairWizardEvent {
    data object Finish : RileyLinkPairWizardEvent()
}

@HiltViewModel
@Stable
class RileyLinkPairWizardViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val activePlugin: ActivePlugin,
    private val rileyLinkUtil: RileyLinkUtil,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(RileyLinkPairWizardUiState())
    val uiState: StateFlow<RileyLinkPairWizardUiState> = _uiState

    private val _events = MutableSharedFlow<RileyLinkPairWizardEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<RileyLinkPairWizardEvent> = _events

    private val bluetoothAdapter get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    private val bluetoothLeScanner get() = bluetoothAdapter?.bluetoothLeScanner

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = device.name ?: "RileyLink"
            val scanned = ScannedDevice(name = name, address = device.address)
            _uiState.update { current ->
                if (current.devices.any { it.address == scanned.address }) current
                else current.copy(devices = current.devices + scanned)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        aapsLogger.debug(LTag.PUMPBTCOMM, "RileyLinkPairWizard: startScan")
        // Disconnect current RL so it becomes discoverable
        rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkDisconnect)
        _uiState.update { it.copy(devices = emptyList()) }
        try {
            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid.fromString(GattAttributes.SERVICE_RADIO))
                .build()
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            bluetoothLeScanner?.startScan(listOf(filter), settings, scanCallback)
        } catch (_: IllegalStateException) {
            // BT not on
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        aapsLogger.debug(LTag.PUMPBTCOMM, "RileyLinkPairWizard: stopScan")
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: IllegalStateException) {
            // BT not on
        }
    }

    @SuppressLint("MissingPermission")
    fun selectDevice(device: ScannedDevice) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "RileyLinkPairWizard: selected ${device.name} (${device.address})")
        stopScan()

        preferences.put(RileyLinkStringPreferenceKey.MacAddress, device.address)
        preferences.put(RileyLinkStringKey.Name, device.name)

        // Force RL reconnection with new address
        val rileyLinkPump = activePlugin.activePumpInternal as? RileyLinkPumpDevice
        rileyLinkPump?.rileyLinkService?.verifyConfiguration(true)
        rileyLinkPump?.triggerPumpConfigurationChangedEvent()

        _uiState.update { it.copy(step = RileyLinkPairStep.COMPLETE, selectedDevice = device) }
    }

    fun finish() {
        _events.tryEmit(RileyLinkPairWizardEvent.Finish)
    }

    fun cancel() {
        stopScan()
        // Reconnect current RL
        rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkNewAddressSet)
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
    }
}

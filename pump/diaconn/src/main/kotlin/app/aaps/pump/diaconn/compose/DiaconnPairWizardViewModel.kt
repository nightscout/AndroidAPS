package app.aaps.pump.diaconn.compose

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
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.diaconn.events.EventDiaconnG8DeviceChange
import app.aaps.pump.diaconn.keys.DiaconnStringNonKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject

enum class DiaconnPairStep {
    BLE_SCAN,
    COMPLETE
}

data class DiaconnPairWizardUiState(
    val step: DiaconnPairStep = DiaconnPairStep.BLE_SCAN,
    val devices: List<ScannedDevice> = emptyList()
)

sealed class DiaconnPairWizardEvent {
    data object Finish : DiaconnPairWizardEvent()
}

@HiltViewModel
@Stable
class DiaconnPairWizardViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val rxBus: RxBus,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiaconnPairWizardUiState())
    val uiState: StateFlow<DiaconnPairWizardUiState> = _uiState

    private val _events = MutableSharedFlow<DiaconnPairWizardEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<DiaconnPairWizardEvent> = _events

    private val serviceUUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    private val bluetoothAdapter get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    private val bluetoothLeScanner get() = bluetoothAdapter?.bluetoothLeScanner

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = device.name ?: return
            if (name.isEmpty()) return

            val scanned = ScannedDevice(name = name, address = device.address)
            _uiState.update { current ->
                if (current.devices.any { it.address == scanned.address }) current
                else current.copy(devices = current.devices + scanned)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        aapsLogger.debug(LTag.PUMP, "DiaconnPairWizard: startScan")
        try {
            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(serviceUUID))
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
        aapsLogger.debug(LTag.PUMP, "DiaconnPairWizard: stopScan")
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: IllegalStateException) {
            // BT not on
        }
    }

    @SuppressLint("MissingPermission")
    fun selectDevice(device: ScannedDevice) {
        aapsLogger.debug(LTag.PUMP, "DiaconnPairWizard: selected ${device.name} (${device.address})")
        stopScan()

        preferences.put(DiaconnStringNonKey.Address, device.address)
        preferences.put(DiaconnStringNonKey.Name, device.name)

        // Create bond
        bluetoothAdapter?.getRemoteDevice(device.address)?.createBond()

        rxBus.send(EventDiaconnG8DeviceChange())
        _uiState.update { it.copy(step = DiaconnPairStep.COMPLETE) }
    }

    fun finish() {
        _events.tryEmit(DiaconnPairWizardEvent.Finish)
    }

    fun cancel() {
        stopScan()
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
    }
}

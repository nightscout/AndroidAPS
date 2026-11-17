package app.aaps.pump.diaconn.compose

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.diaconn.di.DiaconnBle
import app.aaps.pump.diaconn.events.EventDiaconnG8DeviceChange
import app.aaps.pump.diaconn.keys.DiaconnStringNonKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class DiaconnPairWizardViewModel(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val rxBus: RxBus,
    @param:DiaconnBle private val bleTransport: BleTransport
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiaconnPairWizardUiState())
    val uiState: StateFlow<DiaconnPairWizardUiState> = _uiState

    private val _events = MutableSharedFlow<DiaconnPairWizardEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<DiaconnPairWizardEvent> = _events

    init {
        // The transport finds only Diaconn pumps, or the emulated one.
        viewModelScope.launch {
            bleTransport.scanner.scannedDevices.collect { scanned ->
                _uiState.update { current ->
                    if (current.devices.any { it.address == scanned.address }) current
                    else current.copy(devices = current.devices + scanned)
                }
            }
        }
    }

    fun startScan() {
        aapsLogger.debug(LTag.PUMP, "DiaconnPairWizard: startScan")
        bleTransport.scanner.startScan()
    }

    fun stopScan() {
        aapsLogger.debug(LTag.PUMP, "DiaconnPairWizard: stopScan")
        bleTransport.scanner.stopScan()
    }

    fun selectDevice(device: ScannedDevice) {
        aapsLogger.debug(LTag.PUMP, "DiaconnPairWizard: selected ${device.name} (${device.address})")
        stopScan()

        preferences.put(DiaconnStringNonKey.Address, device.address)
        preferences.put(DiaconnStringNonKey.Name, device.name)

        // Create bond
        bleTransport.adapter.createBond(device.address)

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

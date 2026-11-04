package app.aaps.pump.aaruy.compose

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.code.ConnectionState
import app.aaps.pump.aaruy.services.AaruyBLE
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class AaruyPairStep {
    BLE_SCAN,

    /** A real pump does not pair until its button is pressed. Android gives up after 30 s, and the driver tries again. */
    PRESS_BUTTON,
    COMPLETE
}

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class AaruyPairWizardViewModel(
    private val aaruyBle: AaruyBLE,
    private val aaruyPump: AaruyPump
) : ViewModel() {

    private val _step = MutableStateFlow(AaruyPairStep.BLE_SCAN)
    val step: StateFlow<AaruyPairStep> = _step
    val devices: StateFlow<List<ScannedDevice>> = aaruyBle.scannedDevices

    fun startScan() = aaruyBle.startScan()
    fun stopScan() = aaruyBle.stopScan()

    fun selectDevice(device: ScannedDevice) {
        stopScan()
        _step.value = AaruyPairStep.PRESS_BUTTON
        aaruyBle.pair(device)
        viewModelScope.launch {
            aaruyPump.connectionStateFlow.first { it == ConnectionState.CONNECTED }
            _step.value = AaruyPairStep.COMPLETE
        }
    }

    /** Leaving before the pump paired: no half paired pump stays behind. */
    fun cancel() {
        stopScan()
        if (_step.value == AaruyPairStep.PRESS_BUTTON) aaruyBle.unbindDevice()
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
    }
}

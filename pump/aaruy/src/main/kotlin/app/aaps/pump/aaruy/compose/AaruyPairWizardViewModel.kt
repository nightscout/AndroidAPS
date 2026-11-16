package app.aaps.pump.aaruy.compose

import android.os.CountDownTimer
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.code.ConnectionState
import app.aaps.pump.aaruy.event.EventAaruyDeviceChange
import app.aaps.pump.aaruy.keys.AaruyStringKey
import app.aaps.pump.aaruy.services.AaruyBLE
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

enum class AaruyPairWizardStep {
    BLE_SCAN,
    COMPLETE
}

data class AaruyPairWizardUiState(
    val step: AaruyPairWizardStep = AaruyPairWizardStep.BLE_SCAN,
    val devices: List<ScannedDevice> = emptyList()
)

sealed class AaruyPairWizardEvent {
    data object PairFinish : AaruyPairWizardEvent()
}

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class AaruyPairWizardViewModel(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val rxBus: RxBus,
    private val aaruyPump: AaruyPump,
    private val aaruyBle: AaruyBLE
) : ViewModel() {

    val showLoading = mutableStateOf(false)
    private val _uiState = MutableStateFlow(AaruyPairWizardUiState())
    val uiState: StateFlow<AaruyPairWizardUiState> = _uiState

    private val _events = MutableSharedFlow<AaruyPairWizardEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<AaruyPairWizardEvent> = _events

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()
    private var scanJob: Job? = null

    lateinit var countDownTimer: CountDownTimer
    private var clickToConnect = false

    init {
        viewModelScope.launch {
            aaruyPump.connectionStateFlow.collect { state ->
                aapsLogger.debug(LTag.PUMP, "AaruyViewModel connectionStateFlow: $state")
                when (state) {
                    ConnectionState.CONNECTING    -> {

                    }

                    ConnectionState.CONNECTED     -> {
                        if (clickToConnect) {
                            clickToConnect = false
                            aaruyBle.requestHistoryData(aaruyPump.lastHistoryTime)
                            cancelCountDown()
                            showLoading.value = true
                            delay(5000.milliseconds)
                            showLoading.value = false
                            rxBus.send(EventAaruyDeviceChange())
                            _uiState.update { it.copy(step = AaruyPairWizardStep.COMPLETE) }
                        }
                    }

                    ConnectionState.DISCONNECTED  -> {

                    }

                    ConnectionState.DISCONNECTING -> {

                    }
                }

            }
        }
    }

    fun startDeviceScan() {
        _scannedDevices.value = emptyList()
        aaruyBle.scanDevice()
        scanJob = viewModelScope.launch {
            aaruyBle.scannedDevices.collect { device ->
                _scannedDevices.value = device
            }
        }
    }

    fun stopDeviceScan() {
        aaruyBle.stopScan()
        scanJob?.cancel()
        scanJob = null
    }

    fun countDown(
        millisInFuture: Long = 5000,
        countDownInterval: Long = 1000,
        onTick: (millisUntilFinished: Long) -> Unit,
        onFinish: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.Main) {
            if (::countDownTimer.isInitialized) {
                countDownTimer.cancel()
            }
            countDownTimer = object : CountDownTimer(millisInFuture, countDownInterval) {
                override fun onTick(millisUntilFinished: Long) {
                    onTick(millisUntilFinished)
                }

                override fun onFinish() {
                    onFinish.invoke()

                }
            }
            countDownTimer.start()
        }
    }

    fun cancelCountDown() {
        if (::countDownTimer.isInitialized) {
            countDownTimer.cancel()
        }
    }

    fun onDeviceSelected(device: ScannedDevice) {
        clickToConnect = true
        showLoading.value = true

        aaruyPump.canSaveHistory = false
        // aaruyPump.lastHistoryTime = 0
        aaruyPump.lastHistoryDailyTime = System.currentTimeMillis()

        stopDeviceScan()
        preferences.put(AaruyStringKey.MacAddress, device.address)
        preferences.put(AaruyStringKey.PumpName, device.name)
        aaruyBle.connectDevice(device)
        startDeviceScan()

        cancelCountDown()
        countDown(millisInFuture = 15000, onTick = {

        }) {
            showLoading.value = false
        }
    }

    fun finish() {
        clickToConnect = false
        _events.tryEmit(AaruyPairWizardEvent.PairFinish)
    }

}
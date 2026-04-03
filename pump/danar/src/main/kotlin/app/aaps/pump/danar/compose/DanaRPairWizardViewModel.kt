package app.aaps.pump.danar.compose

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.rfcomm.RfcommTransport
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.events.EventDanaRNewStatus
import app.aaps.pump.dana.keys.DanaIntNonKey
import app.aaps.pump.dana.keys.DanaStringNonKey
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class BondedDevice(val name: String, val address: String)

enum class PairWizardStep { CONFIGURE, CONNECTING, COMPLETE, ERROR }

data class DanaRPairWizardUiState(
    val step: PairWizardStep = PairWizardStep.CONFIGURE,
    val password: String = "",
    val bondedDevices: List<BondedDevice> = emptyList(),
    val selectedDevice: BondedDevice? = null,
    val isConnecting: Boolean = false,
    val passwordVerified: Boolean? = null // null = unknown, true = ok, false = wrong
)

sealed class DanaRPairWizardEvent {
    data object Finish : DanaRPairWizardEvent()
}

@HiltViewModel
@Stable
class DanaRPairWizardViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val preferences: Preferences,
    private val danaPump: DanaPump,
    private val commandQueue: CommandQueue,
    private val pumpSync: PumpSync,
    private val rxBus: RxBus,
    private val aapsSchedulers: AapsSchedulers,
    private val rfcommTransport: RfcommTransport
) : ViewModel() {

    private val _uiState = MutableStateFlow(DanaRPairWizardUiState())
    val uiState: StateFlow<DanaRPairWizardUiState> = _uiState

    private val _events = MutableSharedFlow<DanaRPairWizardEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<DanaRPairWizardEvent> = _events

    private val disposable = CompositeDisposable()

    /** Dana device name pattern: 3 letters + 5 digits + 2 letters */
    private val danaNamePattern = Regex("^[a-zA-Z]{3}[0-9]{5}[a-zA-Z]{2}(_[a-zA-Z])?$")

    init {
        reset()

        // Listen for pump status updates to detect password verification
        val onPumpStatusUpdate = {
            if (_uiState.value.step == PairWizardStep.CONNECTING) {
                _uiState.update {
                    it.copy(
                        step = if (danaPump.isPasswordOK) PairWizardStep.COMPLETE else PairWizardStep.ERROR,
                        isConnecting = false,
                        passwordVerified = danaPump.isPasswordOK
                    )
                }
            }
        }
        disposable += rxBus
            .toObservable(EventDanaRNewStatus::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ onPumpStatusUpdate() }, { aapsLogger.error(LTag.PUMP, "Error", it) })
        disposable += rxBus
            .toObservable(EventInitializationChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ onPumpStatusUpdate() }, { aapsLogger.error(LTag.PUMP, "Error", it) })
    }

    fun reset() {
        val existingPassword = preferences.get(DanaIntNonKey.Password)
        val existingName = preferences.get(DanaStringNonKey.RName)
        _uiState.value = DanaRPairWizardUiState(
            password = if (existingPassword != 0) existingPassword.toString() else ""
        )
        refreshBondedDevices()
        if (existingName.isNotEmpty()) {
            _uiState.value.bondedDevices.find { it.name == existingName }?.let { device ->
                _uiState.update { it.copy(selectedDevice = device) }
            }
        }
    }

    fun updatePassword(password: String) {
        // Only allow digits, max 4 characters
        val filtered = password.filter { it.isDigit() }.take(4)
        _uiState.update { it.copy(password = filtered) }
    }

    fun refreshBondedDevices() {
        val devices = rfcommTransport.getBondedDevices()
            .filter { danaNamePattern.matches(it.name) }
            .map { BondedDevice(name = it.name, address = it.address) }

        _uiState.update { it.copy(bondedDevices = devices) }
    }

    fun onDeviceSelected(device: BondedDevice) {
        _uiState.update { it.copy(selectedDevice = device) }
    }

    fun pair() {
        val state = _uiState.value
        val device = state.selectedDevice ?: return
        val password = state.password
        if (password.isEmpty()) return
        val passwordInt = password.toIntOrNull() ?: return

        // Store to preferences
        preferences.put(DanaStringNonKey.RName, device.name)
        preferences.put(DanaIntNonKey.Password, passwordInt)

        _uiState.update {
            it.copy(
                step = PairWizardStep.CONNECTING,
                isConnecting = true,
                passwordVerified = null
            )
        }

        // Trigger connection
        pumpSync.connectNewPump(true)
        commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.device_changed), null)
    }

    fun goBack() {
        _uiState.update {
            it.copy(
                step = PairWizardStep.CONFIGURE,
                isConnecting = false,
                passwordVerified = null
            )
        }
    }

    fun finish() {
        _events.tryEmit(DanaRPairWizardEvent.Finish)
    }

    override fun onCleared() {
        super.onCleared()
        disposable.clear()
    }
}

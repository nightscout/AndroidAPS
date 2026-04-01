package app.aaps.pump.danars.compose

import android.util.Base64
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.core.interfaces.pump.ble.PairingState
import app.aaps.core.interfaces.pump.ble.PairingStep
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.hexStringToByteArray
import app.aaps.pump.dana.keys.DanaStringComposedKey
import app.aaps.pump.dana.keys.DanaStringNonKey
import app.aaps.pump.danars.DanaRSPlugin
import app.aaps.pump.danars.services.BLEComm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.regex.Pattern
import javax.inject.Inject
import kotlin.experimental.xor

enum class WizardStep {
    BLE_SCAN,
    PAIRING_PROGRESS,
    ENTER_PASSWORD,
    ENTER_PIN,
    COMPLETE,
    ERROR
}

data class PairWizardUiState(
    val step: WizardStep = WizardStep.BLE_SCAN,
    val devices: List<ScannedDevice> = emptyList(),
    val selectedDevice: ScannedDevice? = null,
    val errorMessage: String? = null,
    val password: String = "",
    val pin1: String = "",
    val pin2: String = ""
)

sealed class PairWizardEvent {
    data object Finish : PairWizardEvent()
}

@HiltViewModel
@Stable
class DanaRSPairWizardViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val bleTransport: BleTransport,
    private val bleComm: BLEComm,
    private val danaRSPlugin: DanaRSPlugin,
    private val preferences: Preferences,
    private val config: Config,
    private val pumpSync: PumpSync,
    private val commandQueue: CommandQueue
) : ViewModel() {

    private val _uiState = MutableStateFlow(PairWizardUiState())
    val uiState: StateFlow<PairWizardUiState> = _uiState

    private val _events = MutableSharedFlow<PairWizardEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<PairWizardEvent> = _events

    companion object {

        private const val PAIRING_TIMEOUT_MS = 20_000L
        private const val BOND_WAIT_MS = 10_000L
    }

    private val snPattern = Pattern.compile("^([a-zA-Z]{3})([0-9]{5})([a-zA-Z]{2})$")
    private var pairingTimeoutJob: Job? = null

    init {
        // Observe pairing state from BLEComm → update wizard step
        viewModelScope.launch {
            bleTransport.pairingState.collect { state ->
                onPairingStateChanged(state)
            }
        }

        // Collect scanned devices
        viewModelScope.launch {
            aapsLogger.debug(LTag.PUMP, "PairWizard: scannedDevices collector started")
            bleTransport.scanner.scannedDevices.collect { device ->
                aapsLogger.debug(LTag.PUMP, "PairWizard: scanned device=${device.name} addr=${device.address} matches=${snPattern.matcher(device.name).matches()}")
                if (device.name.isNotEmpty() && snPattern.matcher(device.name).matches()) {
                    _uiState.update { current ->
                        if (current.devices.any { it.address == device.address }) current
                        else current.copy(devices = current.devices + device)
                    }
                }
            }
        }
    }

    fun reset() {
        aapsLogger.debug(LTag.PUMP, "PairWizard: reset()")
        _uiState.value = PairWizardUiState()
        bleTransport.updatePairingState(PairingState(step = PairingStep.IDLE))
    }

    fun startScan() {
        aapsLogger.debug(LTag.PUMP, "PairWizard: startScan()")
        _uiState.update { it.copy(devices = emptyList(), step = WizardStep.BLE_SCAN) }
        bleTransport.scanner.startScan()
    }

    fun stopScan() {
        bleTransport.scanner.stopScan()
    }

    fun selectDevice(device: ScannedDevice) {
        stopScan()
        _uiState.update { it.copy(selectedDevice = device, step = WizardStep.PAIRING_PROGRESS) }

        // Store name for encryption key derivation (needed during handshake)
        // but do NOT store MAC to preferences yet (prevents queue interference)
        danaRSPlugin.mDeviceName = device.name

        // Ensure no stale connection/queue interferes with pairing
        commandQueue.clear()

        // Connect directly via BLEComm, bypassing the command queue.
        // connect() is non-blocking; if bonding is needed it returns false
        // and we retry after a delay to allow Android bonding to complete.
        connectWithBondRetry(device.address, "PairWizard")
    }

    private fun connectWithBondRetry(address: String, from: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!bleComm.connect(from, address)) {
                // Bond was requested but not yet complete — wait and retry once
                aapsLogger.debug(LTag.PUMP, "PairWizard: connect returned false, waiting for bond...")
                delay(BOND_WAIT_MS)
                if (_uiState.value.step == WizardStep.PAIRING_PROGRESS) {
                    aapsLogger.debug(LTag.PUMP, "PairWizard: retrying connect after bond wait")
                    bleComm.connect("${from}BondRetry", address)
                }
            }
        }
    }

    fun updatePassword(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    fun submitPassword() {
        val password = _uiState.value.password
        if (password.length != 4) return
        if (!password.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return

        // Store password and retry connection
        preferences.put(DanaStringNonKey.Password, password.uppercase())
        _uiState.update { it.copy(step = WizardStep.PAIRING_PROGRESS, errorMessage = null) }

        val device = _uiState.value.selectedDevice ?: return
        viewModelScope.launch(Dispatchers.IO) {
            bleComm.connect("PairWizardPasswordRetry", device.address)
        }
    }

    fun updatePin1(value: String) {
        _uiState.update { it.copy(pin1 = value) }
    }

    fun updatePin2(value: String) {
        _uiState.update { it.copy(pin2 = value) }
    }

    fun submitPin() {
        val state = _uiState.value
        val pin1 = state.pin1
        val pin2 = state.pin2

        // Validate format
        if (pin1.length != 12 || pin2.length != 8) return
        if (!pin1.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return
        if (!pin2.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return

        // Checksum validation + store keys
        val pairingKey = pin1.hexStringToByteArray()
        val randomPairingKey = pin2.substring(0, 6).hexStringToByteArray()
        val checksum = pin2.substring(6, 8).hexStringToByteArray()

        if (!checkPairingCheckSum(pairingKey, randomPairingKey, checksum)) {
            _uiState.update { it.copy(errorMessage = rh.gs(app.aaps.core.ui.R.string.invalid_input)) }
            return
        }

        _uiState.update { it.copy(step = WizardStep.PAIRING_PROGRESS, errorMessage = null) }
        bleComm.finishV3Pairing()
    }

    fun finishWizard() {
        val device = _uiState.value.selectedDevice ?: return

        // NOW store MAC + name to preferences (pairing succeeded)
        preferences.put(DanaStringNonKey.MacAddress, device.address)
        preferences.put(DanaStringNonKey.RsName, device.name)

        // Bond if not already
        bleTransport.adapter.createBond(device.address)

        // Register new pump for PumpSync data storage
        pumpSync.connectNewPump()

        // Trigger normal pump connection flow
        danaRSPlugin.changePump()

        _events.tryEmit(PairWizardEvent.Finish)
    }

    fun cancel() {
        cancelPairingTimeout()
        stopScan()
        viewModelScope.launch(Dispatchers.IO) {
            bleComm.disconnect("PairWizardCancelled")
        }
        bleTransport.updatePairingState(PairingState(step = PairingStep.IDLE))
        _events.tryEmit(PairWizardEvent.Finish)
    }

    fun retry() {
        val device = _uiState.value.selectedDevice
        if (device != null) {
            _uiState.update { it.copy(step = WizardStep.PAIRING_PROGRESS, errorMessage = null) }
            connectWithBondRetry(device.address, "PairWizardRetry")
        } else {
            _uiState.update { it.copy(step = WizardStep.BLE_SCAN, errorMessage = null) }
            startScan()
        }
    }

    val isEmulating: Boolean
        get() = config.isEnabled(ExternalOptions.EMULATE_DANA_RS_V1) || config.isEnabled(ExternalOptions.EMULATE_DANA_RS_V3) || config.isEnabled(ExternalOptions.EMULATE_DANA_BLE5)

    override fun onCleared() {
        super.onCleared()
        cancelPairingTimeout()
        stopScan()
    }

    private fun onPairingStateChanged(state: PairingState) {
        when (state.step) {
            PairingStep.IDLE                        -> {
                // If we were in pairing progress, treat IDLE as a disconnect/failure
                if (_uiState.value.step == WizardStep.PAIRING_PROGRESS) {
                    cancelPairingTimeout()
                    _uiState.update { it.copy(step = WizardStep.ERROR, errorMessage = rh.gs(app.aaps.core.ui.R.string.connection_error)) }
                }
            }

            PairingStep.CONNECTING                  -> {
                _uiState.update { it.copy(step = WizardStep.PAIRING_PROGRESS) }
                startPairingTimeout()
            }

            PairingStep.HANDSHAKE_IN_PROGRESS       -> _uiState.update { it.copy(step = WizardStep.PAIRING_PROGRESS) }
            PairingStep.WAITING_FOR_PAIRING_CONFIRM -> _uiState.update { it.copy(step = WizardStep.PAIRING_PROGRESS) }

            PairingStep.WAITING_FOR_PASSWORD        -> {
                cancelPairingTimeout()
                _uiState.update { it.copy(step = WizardStep.ENTER_PASSWORD) }
            }

            PairingStep.WAITING_FOR_PIN             -> {
                cancelPairingTimeout()
                _uiState.update { it.copy(step = WizardStep.ENTER_PIN) }
            }

            PairingStep.CONNECTED                   -> {
                cancelPairingTimeout()
                _uiState.update { it.copy(step = WizardStep.COMPLETE) }
            }

            PairingStep.ERROR                       -> {
                cancelPairingTimeout()
                _uiState.update { it.copy(step = WizardStep.ERROR, errorMessage = state.errorMessage) }
            }
        }
    }

    private fun startPairingTimeout() {
        cancelPairingTimeout()
        pairingTimeoutJob = viewModelScope.launch {
            delay(PAIRING_TIMEOUT_MS)
            aapsLogger.warn(LTag.PUMP, "PairWizard: pairing timeout after ${PAIRING_TIMEOUT_MS}ms")
            _uiState.update {
                it.copy(step = WizardStep.ERROR, errorMessage = rh.gs(app.aaps.pump.dana.R.string.danars_pairingtimedout))
            }
        }
    }

    private fun cancelPairingTimeout() {
        pairingTimeoutJob?.cancel()
        pairingTimeoutJob = null
    }

    private fun checkPairingCheckSum(pairingKey: ByteArray, randomPairingKey: ByteArray, checksum: ByteArray): Boolean {
        var pairingKeyCheckSum: Byte = 0
        for (i in pairingKey.indices)
            pairingKeyCheckSum = pairingKeyCheckSum xor pairingKey[i]

        preferences.put(DanaStringComposedKey.V3ParingKey, danaRSPlugin.mDeviceName, value = Base64.encodeToString(pairingKey, Base64.DEFAULT))

        for (i in randomPairingKey.indices)
            pairingKeyCheckSum = pairingKeyCheckSum xor randomPairingKey[i]

        preferences.put(DanaStringComposedKey.V3RandomParingKey, danaRSPlugin.mDeviceName, value = Base64.encodeToString(randomPairingKey, Base64.DEFAULT))

        return checksum[0] == pairingKeyCheckSum
    }
}

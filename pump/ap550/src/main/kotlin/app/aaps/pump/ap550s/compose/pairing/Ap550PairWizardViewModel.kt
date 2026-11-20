package app.aaps.pump.ap550s.compose.pairing

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.ap550s.AP550Pump
import app.aaps.pump.ap550s.AP550PumpPlugin
import app.aaps.pump.ap550s.R
import app.aaps.pump.ap550s.defs.ConnectStatus
import app.aaps.pump.ap550s.events.EventBlePair
import app.aaps.pump.ap550s.keys.Ap550StringNonKey
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Steps of the pairing wizard. */
enum class Ap550PairStep {
    BLE_SCAN,
    CONNECTING,
    PAIR_CODE,
    COMPLETE
}

data class Ap550PairUiState(
    val step: Ap550PairStep = Ap550PairStep.BLE_SCAN,
    val devices: List<Ap550ScannedDevice> = emptyList(),
    val selectedDevice: Ap550ScannedDevice? = null,
    val isScanning: Boolean = false,
    val pairingCode: String = "",
    /** Shown under the list or the code field. */
    val message: String? = null,
    /** True when [message] describes a failure, so the screen can colour it. */
    val isError: Boolean = false
)

/** One-time requests the wizard sends to its host. */
sealed class Ap550PairEvent {
    /** Pairing is done, or was given up. The host goes back to the overview. */
    data object Finish : Ap550PairEvent()

    /** The user wants to pair by reading the QR code on the pump. */
    data object StartQrScan : Ap550PairEvent()
}

/**
 * Drives the pairing wizard: scan, pick a pump, type the pairing code shown on it.
 *
 * A pump is remembered by its Bluetooth address and the serial number read from its advertisement.
 * The serial number is also what a QR code is matched against, so the QR path and the scan path end
 * in the same place.
 */
// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class Ap550PairWizardViewModel(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val rxBus: RxBus,
    private val ap550Pump: AP550Pump,
    private val ap550PumpPlugin: AP550PumpPlugin,
    private val scanner: Ap550BleScanner,
    private val preferences: Preferences,
    private val pumpSync: PumpSync
) : ViewModel() {

    private val _uiState = MutableStateFlow(Ap550PairUiState())
    val uiState: StateFlow<Ap550PairUiState> = _uiState

    private val _events = MutableSharedFlow<Ap550PairEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<Ap550PairEvent> = _events

    /** Serial number from a QR code, waiting for that pump to show up in the scan. */
    private var pendingSerial: String? = null

    private var scanTimeoutJob: Job? = null

    init {
        viewModelScope.launch {
            scanner.devices.collect { devices ->
                _uiState.update { it.copy(devices = devices) }
                connectPendingPump(devices)
            }
        }
        viewModelScope.launch {
            scanner.scanError.collect { errorCode ->
                if (errorCode != null) {
                    stopScan()
                    showMessage(rh.gs(R.string.ap550_ble_config_scan_error, errorCode), isError = true)
                }
            }
        }
    }

    /** Called when the wizard is opened, so a previous run is not shown again. */
    fun reset() {
        stopScan()
        pendingSerial = null
        _uiState.value = Ap550PairUiState()
    }

    fun startScan() {
        pendingSerial = null
        _uiState.update { it.copy(isScanning = true, message = null, isError = false) }
        scanner.start()
        scanTimeoutJob?.cancel()
        scanTimeoutJob = viewModelScope.launch {
            delay(SCAN_PERIOD_MS)
            onScanTimeout()
        }
    }

    fun stopScan() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        scanner.stop()
        _uiState.update { it.copy(isScanning = false) }
    }

    /** A pump was tapped in the list: connect to it and ask it to start pairing. */
    fun onDeviceSelected(device: Ap550ScannedDevice) {
        stopScan()
        pendingSerial = null
        _uiState.update {
            it.copy(step = Ap550PairStep.CONNECTING, selectedDevice = device, pairingCode = "", message = null, isError = false)
        }
        viewModelScope.launch(Dispatchers.IO) { connectAndStartPairing(device) }
    }

    fun onPairingCodeChanged(code: String) {
        // The pump takes digits only, so anything else never reaches the field.
        if (code.length > PAIRING_CODE_LENGTH || code.any { !it.isDigit() }) return
        _uiState.update { it.copy(pairingCode = code, message = null, isError = false) }
    }

    /**
     * Sends the code to the pump.
     *
     * One wrong code can be corrected. A second wrong code drops the link, because the pump refuses
     * further attempts on the same connection.
     */
    fun submitPairingCode() {
        val device = _uiState.value.selectedDevice ?: return
        val code = _uiState.value.pairingCode
        if (code.length != PAIRING_CODE_LENGTH) {
            showMessage(rh.gs(R.string.ap550_ble_device_pairing_code_input_error), isError = true)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            ap550Pump.scanStatusRecord(ConnectStatus.Pairing)
            if (ap550PumpPlugin.sendPairingCode(code)) {
                preferences.put(Ap550StringNonKey.PairingCode, code)
                storePairedPump(device)
                // A different pump is now active. Forget the old pump registration, so history
                // records of the new pump are accepted (same as the Dana wizard does).
                pumpSync.connectNewPump(true)
                ap550Pump.setIsFirstConnect(true)
                aapsLogger.debug(LTag.PUMPBTCOMM, "Pairing finished, serial=${device.serialNumber ?: ap550Pump.sn_for_pair}")
                // The driver reads the pump status again once the link is authenticated.
                rxBus.send(EventBlePair(true))
                _uiState.update { it.copy(step = Ap550PairStep.COMPLETE, message = null, isError = false) }
                return@launch
            }
            ap550Pump.scanStatusRecord(ConnectStatus.PairCodeError)
            val attempts = ap550Pump.pairCodeResult
            if (attempts == FIRST_ATTEMPT) {
                _uiState.update {
                    it.copy(pairingCode = "", message = rh.gs(R.string.ap550_ble_device_pairing_code_input_error), isError = true)
                }
            } else {
                ap550PumpPlugin.disconnect("pairing failed")
                forgetPairing()
                _uiState.update {
                    it.copy(
                        step = Ap550PairStep.BLE_SCAN,
                        selectedDevice = null,
                        pairingCode = "",
                        message = rh.gs(R.string.ap550_ble_device_pairing_code_input_error),
                        isError = true
                    )
                }
            }
        }
    }

    /** The user is done: leave the wizard. The overview shows the new state on its own. */
    fun finish() {
        stopScan()
        _events.tryEmit(Ap550PairEvent.Finish)
    }

    /**
     * Leaves the wizard through the toolbar back arrow.
     *
     * A pairing that already finished is kept, anything still in progress is dropped. The screen
     * decides how to show the two cases, so the host does not have to watch the wizard state.
     */
    fun leaveWizard() {
        if (_uiState.value.step == Ap550PairStep.COMPLETE) finish() else cancel()
    }

    /** Leave without pairing, and drop whatever the attempt had stored. */
    fun cancel() {
        stopScan()
        pendingSerial = null
        _uiState.update { it.copy(devices = emptyList()) }
        viewModelScope.launch(Dispatchers.IO) {
            ap550Pump.scanStatusRecord(ConnectStatus.CancelPair)
            if (ap550PumpPlugin.isConnected()) ap550PumpPlugin.disconnect("pairing cancelled")
            forgetPairing()
        }
        _events.tryEmit(Ap550PairEvent.Finish)
    }

    /** A pump QR code was read: pair with that pump, or look for it if the scan did not see it yet. */
    fun onQrScanned(serialNumber: String) {
        // The handshake reports the serial number of the pump it talked to, so keep it for the case
        // where the advertisement does not carry one.
        ap550Pump.serialNumber = serialNumber
        val known = _uiState.value.devices.firstOrNull { it.serialNumber == serialNumber }
        if (known != null) {
            onDeviceSelected(known)
            return
        }
        pendingSerial = serialNumber
        _uiState.update {
            it.copy(step = Ap550PairStep.BLE_SCAN, message = rh.gs(R.string.ap550_searching_devices), isError = false)
        }
        // The scan may already have stopped, so start a fresh one.
        stopScan()
        startScan()
        pendingSerial = serialNumber
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
    }

    private fun onScanTimeout() {
        scanner.stop()
        _uiState.update { it.copy(isScanning = false) }
        if (pendingSerial != null) {
            pendingSerial = null
            showMessage(rh.gs(R.string.ap550_no_nearby_devices), isError = true)
        }
    }

    private fun connectPendingPump(devices: List<Ap550ScannedDevice>) {
        val pending = pendingSerial ?: return
        val match = devices.firstOrNull { it.serialNumber == pending } ?: return
        pendingSerial = null
        onDeviceSelected(match)
    }

    /**
     * Connects and asks the pump for the pairing handshake.
     *
     * `connect` returns before the link is up - the waits inside the connect attempt run on a
     * background thread, so the command queue is not blocked. [AP550PumpPlugin.joinConnectAttempt]
     * waits for the attempt to finish, like the old blocking connect did. Everything below runs on
     * [Dispatchers.IO], so that wait is allowed here.
     */
    private suspend fun connectAndStartPairing(device: Ap550ScannedDevice) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Pairing with ${device.address} (${device.name})")
        rememberPairing(device)

        if (ap550PumpPlugin.isConnected()) ap550PumpPlugin.disconnect("scan disconnect")
        ap550PumpPlugin.connect("scan connect")
        ap550PumpPlugin.joinConnectAttempt()
        if (!ap550PumpPlugin.isConnected()) {
            forgetPairing()
            failWith(R.string.ap550_ble_connect_fail)
            return
        }
        delay(POST_CONNECT_SETTLE_MS)
        if (!ap550PumpPlugin.initPairing()) {
            ap550PumpPlugin.disconnect("pairing refused")
            forgetPairing()
            failWith(R.string.ap550_ble_bluetooth_is_disconnected)
            return
        }
        _uiState.update { it.copy(step = Ap550PairStep.PAIR_CODE, message = null, isError = false) }
    }

    /** Puts the candidate pump where the driver reads it, without writing it to storage yet. */
    private fun rememberPairing(device: Ap550ScannedDevice) {
        ap550Pump.mac_address_for_pair = device.address
        ap550Pump.device_name_for_pair = device.name
        ap550Pump.sn_for_pair = device.serialNumber.orEmpty()
        device.serialNumber?.let { ap550Pump.serialNumber = it }
    }

    /** Writes the paired pump to storage. Only called once the pump accepted the code. */
    private fun storePairedPump(device: Ap550ScannedDevice) {
        rememberPairing(device)
        preferences.put(Ap550StringNonKey.MacAddress, device.address)
        preferences.put(Ap550StringNonKey.BtDeviceName, device.name)
        preferences.put(Ap550StringNonKey.SerialNumber, ap550Pump.sn_for_pair)
    }

    /** Drops the candidate pump identity, without touching a pump that is already paired. */
    private fun forgetPairing() {
        ap550Pump.mac_address_for_pair = ""
        ap550Pump.device_name_for_pair = ""
        ap550Pump.sn_for_pair = ""
    }

    private fun failWith(@StringRes message: Int) {
        _uiState.update { it.copy(step = Ap550PairStep.BLE_SCAN, selectedDevice = null, isError = true) }
        showMessage(rh.gs(message), isError = true)
    }

    private fun showMessage(message: String, isError: Boolean) {
        _uiState.update { it.copy(message = message, isError = isError) }
    }

    companion object {

        /** The scan stops on its own after this, the same as the old screen did. */
        private const val SCAN_PERIOD_MS = 15_000L

        /** Time given to the pump to settle after the link is up, before pairing is requested. */
        private const val POST_CONNECT_SETTLE_MS = 100L

        /** Digits of the code the pump shows. The screen uses it to enable the pair button. */
        internal const val PAIRING_CODE_LENGTH = 6

        /** The first wrong code can be corrected; a later one ends the attempt. */
        private const val FIRST_ATTEMPT = 2
    }
}
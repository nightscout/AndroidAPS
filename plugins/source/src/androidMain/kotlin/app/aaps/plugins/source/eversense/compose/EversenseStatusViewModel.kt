package app.aaps.plugins.source.eversense.compose

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.eversense.EversenseCGMPlugin
import app.aaps.plugins.eversense.callbacks.EversenseScanCallback
import app.aaps.plugins.eversense.callbacks.EversenseWatcher
import app.aaps.plugins.eversense.enums.EversenseType
import app.aaps.plugins.eversense.models.ActiveAlarm
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.models.EversenseScanResult
import app.aaps.plugins.eversense.models.EversenseState
import app.aaps.plugins.source.R
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The status and connection tab of the Eversense screen: what the transmitter reports, connect and disconnect, a
 * scan for a new transmitter, a full sync.
 *
 * It was `EversenseStatusActivity`, opened from the settings. The logic is the same; the timers are coroutines now.
 */
// Registers itself: @ViewModelKey infers the key from the class. Unscoped, so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class EversenseStatusViewModel(
    private val eversense: EversenseCGMPlugin,
    val rh: ResourceHelper,
    private val dateUtil: DateUtil
) : ViewModel(), EversenseWatcher {

    enum class Dialog { NONE, DISCONNECT, CHANGE, SCANNING, RESULTS }

    data class UiState(
        val lines: List<String> = emptyList(),
        val connected: Boolean = false,
        val syncDays: Int = DEFAULT_SYNC_DAYS,
        val dialog: Dialog = Dialog.NONE,
        val found: List<EversenseScanResult> = emptyList()
    )

    private val _uiState = MutableStateFlow(UiState(syncDays = eversense.preferences.getInt(SYNC_DAYS_KEY, DEFAULT_SYNC_DAYS)))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var scanSession: AutoCloseable? = null
    private var scanJob: Job? = null

    init {
        eversense.addWatcher(this)
        refresh()
    }

    override fun onCleared() {
        stopScan()
        eversense.removeWatcher(this)
        super.onCleared()
    }

    // EversenseWatcher, called on the driver's threads
    override fun onConnectionChanged(connected: Boolean) = refresh()
    override fun onStateChanged(state: EversenseState) = refresh()

    // isConnected() is "link up and transmitter ready". The link comes up first; ready follows after the handshake,
    // with no second onConnectionChanged. Without this the tab would show "not connected" until opened again.
    override fun onTransmitterReady() = refresh()
    override fun onTransmitterNotPlaced() {}
    override fun onAlarmReceived(alarm: ActiveAlarm) {}
    override fun onCGMRead(type: EversenseType, readings: List<EversenseCGMResult>) {}

    private fun refresh() {
        val state = eversense.getCurrentState()
        val connected = eversense.isConnected()
        val never = rh.gs(R.string.eversense_status_never)
        fun date(value: Long) = if (value > 0) dateUtil.dateAndTimeString(value) else never
        _uiState.update {
            it.copy(
                connected = connected,
                lines = listOf(
                    rh.gs(R.string.eversense_status_connected_label, if (connected) "✅" else "❌"),
                    rh.gs(R.string.eversense_status_battery_label, state.batteryPercentage),
                    rh.gs(R.string.eversense_status_insertion_label, date(state.insertionDate)),
                    rh.gs(R.string.eversense_status_last_sync_label, date(state.lastSync)),
                    rh.gs(R.string.eversense_status_signal_label, signalLabel(state.sensorSignalStrength)),
                    rh.gs(R.string.eversense_status_phase_label, state.calibrationPhase.name),
                    rh.gs(R.string.eversense_status_last_cal_label, date(state.lastCalibrationDate)),
                    rh.gs(R.string.eversense_status_next_cal_label, date(state.nextCalibrationDate))
                )
            )
        }
    }

    private fun signalLabel(strength: Int): String = rh.gs(
        when {
            strength >= 75 -> R.string.eversense_signal_excellent
            strength >= 48 -> R.string.eversense_signal_good
            strength >= 30 -> R.string.eversense_signal_fair
            strength >= 25 -> R.string.eversense_signal_weak
            strength > 0   -> R.string.eversense_signal_poor
            else           -> R.string.eversense_not_connected
        }
    )

    /** Connected: ask before disconnecting. A known transmitter: connect to it. None yet: scan for one. */
    fun onConnectClick() {
        when {
            eversense.isConnected()                                       -> _uiState.update { it.copy(dialog = Dialog.DISCONNECT) }
            eversense.preferences.getString(REMOTE_DEVICE_KEY, null) != null -> inBackground(REFRESH_AFTER_CONNECT_MS) { eversense.forceReconnect() }
            else                                                          -> startScan()
        }
    }

    fun onChangeTransmitterClick() = _uiState.update { it.copy(dialog = Dialog.CHANGE) }

    fun confirmDisconnect() {
        dismissDialog()
        inBackground(REFRESH_AFTER_DISCONNECT_MS) { eversense.disconnect() }
    }

    fun confirmChangeTransmitter() {
        dismissDialog()
        eversense.clearStoredDevice()
        refresh()
        startScan()
    }

    fun onDeviceClick(item: EversenseScanResult) {
        dismissDialog()
        inBackground(REFRESH_AFTER_CONNECT_MS) { eversense.connect(item.device) }
    }

    fun onSyncClick() {
        if (eversense.isConnected()) inBackground(REFRESH_AFTER_SYNC_MS) { eversense.triggerFullSync(force = true) }
    }

    fun setSyncDays(days: Int) {
        eversense.preferences.edit().putInt(SYNC_DAYS_KEY, days).apply()
        _uiState.update { it.copy(syncDays = days) }
    }

    fun dismissDialog() {
        stopScan()
        _uiState.update { it.copy(dialog = Dialog.NONE) }
    }

    /** Runs a driver call off the main thread, then shows the state again once it had time to change. */
    private fun inBackground(refreshAfterMs: Long, call: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            call()
            delay(refreshAfterMs)
            refresh()
        }
    }

    private fun startScan() {
        stopScan()
        _uiState.update { it.copy(dialog = Dialog.SCANNING, found = emptyList()) }
        scanSession = eversense.startScan(object : EversenseScanCallback {
            override fun onResult(item: EversenseScanResult) {
                _uiState.update { state ->
                    val wanted = state.dialog == Dialog.SCANNING && TRANSMITTER_NAME.matches(item.name) && state.found.none { it.name == item.name }
                    if (wanted) state.copy(found = state.found + item) else state
                }
            }
        })
        scanJob = viewModelScope.launch {
            delay(SCAN_MS)
            scanSession?.close()
            scanSession = null
            _uiState.update { if (it.dialog == Dialog.SCANNING) it.copy(dialog = Dialog.RESULTS) else it }
        }
    }

    private fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        scanSession?.close()
        scanSession = null
    }

    private companion object {

        const val SYNC_DAYS_KEY = "eversense_sync_days"
        const val DEFAULT_SYNC_DAYS = 14
        const val REMOTE_DEVICE_KEY = "eversense_remote_device"
        const val SCAN_MS = 10_000L
        const val REFRESH_AFTER_CONNECT_MS = 3_000L
        const val REFRESH_AFTER_DISCONNECT_MS = 500L
        const val REFRESH_AFTER_SYNC_MS = 5_000L

        /** Eversense transmitters advertise as "T" and their serial number */
        val TRANSMITTER_NAME = Regex("T\\d+.*")
    }
}

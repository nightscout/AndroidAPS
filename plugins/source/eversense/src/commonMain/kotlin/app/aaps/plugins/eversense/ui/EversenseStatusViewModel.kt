package app.aaps.plugins.eversense.ui

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.source.CgmAttention
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.CoreUiStrings
import app.aaps.plugins.eversense.EversenseDriver
import app.aaps.plugins.eversense.EversenseStrings
import app.aaps.plugins.eversense.callbacks.EversenseWatcher
import app.aaps.plugins.eversense.enums.EversenseType
import app.aaps.plugins.eversense.enums.SignalStrength
import app.aaps.plugins.eversense.models.ActiveAlarm
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.models.EversenseState
import app.aaps.plugins.eversense.pairing.EversenseAccount
import app.aaps.plugins.eversense.pairing.EversensePairing
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * The status tab of the Eversense screen: the pairing (with what to fix), what the transmitter reports, and a full
 * sync. The link itself follows the plugin: it is open while Eversense is the active BG source and a transmitter is
 * paired, and the driver reconnects by itself.
 */
// Registered in EversenseRegistration. Unscoped, so each screen gets its own.
@Stable
@Inject
class EversenseStatusViewModel(
    private val eversense: EversenseDriver,
    private val pairing: EversensePairing,
    val rh: TextResolver,
    private val dateUtil: DateUtil
) : ViewModel(), EversenseWatcher {

    data class UiState(
        val lines: List<String> = emptyList(),
        val connected: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val attention: StateFlow<CgmAttention?> = pairing.attention
    val account: StateFlow<EversenseAccount?> = pairing.account
    val transmitter: StateFlow<String?> = pairing.transmitter

    init {
        eversense.addWatcher(this)
        // Connected, ready and lost come as states, with no watcher call for each step
        eversense.connectionState.onEach { refresh() }.launchIn(viewModelScope)
    }

    override fun onCleared() {
        eversense.removeWatcher(this)
        super.onCleared()
    }

    // EversenseWatcher, called on the driver's thread
    override fun onConnectionChanged(connected: Boolean) = refresh()
    override fun onStateChanged(state: EversenseState) = refresh()
    override fun onTransmitterReady() = refresh()
    override fun onTransmitterNotPlaced() {}
    override fun onAlarmReceived(alarm: ActiveAlarm) {}
    override fun onCGMRead(type: EversenseType, readings: List<EversenseCGMResult>) {}

    private fun refresh() {
        val state = eversense.getCurrentState()
        val connected = eversense.isConnected()
        // Before the first sync the stored values are the defaults, not what the transmitter said
        val synced = state.lastSync > 0
        val never = rh.gs(EversenseStrings.eversense_status_never)
        fun date(value: Long) = if (value > 0) dateUtil.dateAndTimeString(value) else never
        fun percent(value: Int) = rh.gs(CoreUiStrings.units_format_percent, value)
        val signal = SignalStrength.from365(state.sensorSignalStrength)
        val lines = buildList {
            add(rh.gs(EversenseStrings.eversense_status_connected_label, rh.gs(if (connected) CoreUiStrings.yes else CoreUiStrings.no)))
            add(rh.gs(EversenseStrings.eversense_status_battery, if (synced && state.batteryPercentage >= 0) percent(state.batteryPercentage) else NO_VALUE))
            add(rh.gs(EversenseStrings.eversense_status_insertion_label, date(state.insertionDate)))
            if (state.insertionDate > 0) {
                val lifetimeDays = if (eversense.transmitterIs365()) 365 else 180
                add(rh.gs(EversenseStrings.eversense_status_sensor_end, dateUtil.dateString(state.insertionDate + lifetimeDays * DAY_MS)))
            }
            add(rh.gs(EversenseStrings.eversense_status_last_sync_label, date(state.lastSync)))
            add(
                rh.gs(
                    EversenseStrings.eversense_status_signal_label,
                    if (synced) rh.gs(EversenseStrings.eversense_signal_value, rh.gs(signal.label), percent(state.sensorSignalStrength)) else NO_VALUE
                )
            )
            add(rh.gs(EversenseStrings.eversense_status_phase_label, if (synced) rh.gs(state.calibrationPhase.label) else NO_VALUE))
            add(rh.gs(EversenseStrings.eversense_status_last_cal_label, date(state.lastCalibrationDate)))
            add(rh.gs(EversenseStrings.eversense_status_next_cal_label, date(state.nextCalibrationDate)))
            if (state.firmwareVersion.isNotEmpty()) add(rh.gs(EversenseStrings.eversense_status_firmware, state.firmwareVersion))
            state.activeAlarms.forEach { add(rh.gs(EversenseStrings.eversense_status_alarm, rh.gs(it.code.title))) }
        }
        _uiState.value = UiState(lines = lines, connected = connected)
    }

    /** Forgets the transmitter; the login stays. */
    fun unpair() {
        pairing.unpair()
        viewModelScope.launch {
            delay(REFRESH_AFTER_UNPAIR_MS)
            refresh()
        }
    }

    fun onSyncClick() {
        if (!eversense.isConnected()) return
        viewModelScope.launch {
            eversense.triggerFullSync(force = true)
            delay(REFRESH_AFTER_SYNC_MS)
            refresh()
        }
    }

    private companion object {

        /** A value not known yet */
        const val NO_VALUE = "–"
        const val DAY_MS = 24 * 60 * 60 * 1000L
        const val REFRESH_AFTER_UNPAIR_MS = 500L
        const val REFRESH_AFTER_SYNC_MS = 5_000L
    }
}

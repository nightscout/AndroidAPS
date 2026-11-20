package app.aaps.plugins.eversense.ui

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.CgmAttention
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.eversense.EversenseCGMPlugin
import app.aaps.plugins.eversense.R
import app.aaps.plugins.eversense.callbacks.EversenseWatcher
import app.aaps.plugins.eversense.enums.EversenseType
import app.aaps.plugins.eversense.models.ActiveAlarm
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.models.EversenseState
import app.aaps.plugins.eversense.pairing.EversenseAccount
import app.aaps.plugins.eversense.pairing.EversensePairing
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The status tab of the Eversense screen: the pairing (with what to fix), what the transmitter reports, and a full
 * sync. The link itself follows the plugin: it is open while Eversense is the active BG source and a transmitter is
 * paired, and the driver reconnects by itself.
 */
// Registers itself: @ViewModelKey infers the key from the class. Unscoped, so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class EversenseStatusViewModel(
    private val eversense: EversenseCGMPlugin,
    private val pairing: EversensePairing,
    val rh: ResourceHelper,
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
        refresh()
    }

    override fun onCleared() {
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

    /** Forgets the transmitter; the login stays. */
    fun unpair() {
        viewModelScope.launch(Dispatchers.IO) {
            pairing.unpair()
            delay(REFRESH_AFTER_UNPAIR_MS)
            refresh()
        }
    }

    fun onSyncClick() {
        if (!eversense.isConnected()) return
        viewModelScope.launch(Dispatchers.IO) {
            eversense.triggerFullSync(force = true)
            delay(REFRESH_AFTER_SYNC_MS)
            refresh()
        }
    }

    private companion object {

        const val REFRESH_AFTER_UNPAIR_MS = 500L
        const val REFRESH_AFTER_SYNC_MS = 5_000L
    }
}

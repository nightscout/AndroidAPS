package app.aaps.plugins.eversense.ui

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.source.CgmAttention
import app.aaps.core.interfaces.source.SensorLifetime
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.core.ui.compose.pump.StatusBanner
import app.aaps.plugins.eversense.EversenseDriver
import app.aaps.plugins.eversense.EversenseDriver.ConnectionState
import app.aaps.plugins.eversense.EversensePlugin.Companion.BATTERY_LOW_PERCENT
import app.aaps.plugins.eversense.EversenseStrings
import app.aaps.plugins.eversense.callbacks.EversenseWatcher
import app.aaps.plugins.eversense.enums.EversenseType
import app.aaps.plugins.eversense.enums.SignalStrength
import app.aaps.plugins.eversense.models.ActiveAlarm
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.models.EversenseState
import app.aaps.plugins.eversense.pairing.EversensePairing
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * The status tab of the Eversense screen, laid out as a pump's: a banner with what needs the user first (no pairing, a
 * refused login, or the link), the transmitter's values as label and value rows, and the buttons at the bottom. The
 * link itself follows the plugin: it is open while Eversense is the active BG source and a transmitter is paired, and
 * the driver reconnects by itself.
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
        val banner: StatusBanner? = null,
        val rows: List<PumpInfoRow> = emptyList(),
        val attention: CgmAttention? = null,
        /** False for an E3 used without an Eversense account */
        val signedIn: Boolean = false,
        val transmitter: String? = null,
        val connected: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        eversense.addWatcher(this)
        // Connected, ready and lost come as states, with no watcher call for each step; the pairing as its own flows
        merge(eversense.connectionState, pairing.attention, pairing.account, pairing.transmitter)
            .onEach { refresh() }
            .launchIn(viewModelScope)
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
        val attention = pairing.attention.value
        val account = pairing.account.value
        val transmitter = pairing.transmitter.value
        val banner = when (attention) {
            CgmAttention.NOT_PAIRED     -> StatusBanner(rh.gs(EversenseStrings.eversense_not_paired), StatusLevel.WARNING)
            CgmAttention.SIGN_IN_FAILED -> StatusBanner(rh.gs(EversenseStrings.eversense_sign_in_failed), StatusLevel.CRITICAL)
            null                        -> when (eversense.connectionState.value) {
                ConnectionState.READY      -> StatusBanner(rh.gs(EversenseStrings.eversense_connected), StatusLevel.NORMAL)
                ConnectionState.CONNECTING -> StatusBanner(rh.gs(CoreUiStrings.connecting))
                else                       -> StatusBanner(rh.gs(EversenseStrings.eversense_not_connected), StatusLevel.WARNING)
            }
        }
        // What the transmitter reports means something only once one is paired
        val rows = if (attention == CgmAttention.NOT_PAIRED) emptyList() else buildList {
            transmitter?.let { add(PumpInfoRow(rh.gs(EversenseStrings.eversense_transmitter), it)) }
            add(
                if (account != null) PumpInfoRow(rh.gs(EversenseStrings.eversense_pair_account_title), account.username)
                // An E3 used without a login: it works, but nothing goes to the Eversense cloud
                else PumpInfoRow(rh.gs(EversenseStrings.eversense_pair_account_title), rh.gs(EversenseStrings.eversense_not_signed_in_no_cloud), StatusLevel.WARNING)
            )
            addAll(transmitterRows(eversense.getCurrentState()))
        }
        _uiState.value = UiState(
            banner = banner,
            rows = rows,
            attention = attention,
            signedIn = account != null,
            transmitter = transmitter,
            connected = eversense.isConnected()
        )
    }

    private fun transmitterRows(state: EversenseState): List<PumpInfoRow> {
        // Before the first sync the stored values are the defaults, not what the transmitter said
        val synced = state.lastSync > 0
        val never = rh.gs(EversenseStrings.eversense_status_never)
        fun date(value: Long) = if (value > 0) dateUtil.dateAndTimeString(value) else never
        fun percent(value: Int) = rh.gs(CoreUiStrings.units_format_percent, value)
        fun row(label: String, value: String, level: StatusLevel = StatusLevel.UNSPECIFIED) = PumpInfoRow(label, value, level)
        val now = dateUtil.now()
        val signal = SignalStrength.from365(state.sensorSignalStrength)
        return buildList {
            add(row(rh.gs(EversenseStrings.eversense_last_sync), date(state.lastSync)))
            val batteryKnown = synced && state.batteryPercentage >= 0
            add(
                row(
                    rh.gs(CoreUiStrings.battery_label),
                    if (batteryKnown) percent(state.batteryPercentage) else NO_VALUE,
                    // The same limit as the battery-low notice
                    if (batteryKnown && state.batteryPercentage <= BATTERY_LOW_PERCENT) StatusLevel.CRITICAL else StatusLevel.UNSPECIFIED
                )
            )
            add(
                row(
                    rh.gs(EversenseStrings.eversense_signal),
                    if (synced) rh.gs(EversenseStrings.eversense_signal_value, rh.gs(signal.label), percent(state.sensorSignalStrength)) else NO_VALUE,
                    if (synced) signal.level else StatusLevel.UNSPECIFIED
                )
            )
            add(row(rh.gs(EversenseStrings.eversense_sensor_inserted), date(state.insertionDate)))
            val lifetimeDays = eversense.sensorLifetimeDays()
            if (state.insertionDate > 0 && lifetimeDays != null) {
                val lifetimeHours = lifetimeDays * 24
                val ageHours = (now - state.insertionDate) / HOUR_MS
                // The same rule as the sensor age of the overview
                val level = when {
                    ageHours >= SensorLifetime.criticalHours(lifetimeHours) -> StatusLevel.CRITICAL
                    ageHours >= SensorLifetime.warningHours(lifetimeHours)  -> StatusLevel.WARNING
                    else                                                    -> StatusLevel.UNSPECIFIED
                }
                add(row(rh.gs(EversenseStrings.eversense_sensor_end), dateUtil.dateString(state.insertionDate + lifetimeHours * HOUR_MS), level))
            }
            add(row(rh.gs(EversenseStrings.eversense_calibration_phase), if (synced) rh.gs(state.calibrationPhase.label) else NO_VALUE))
            add(row(rh.gs(EversenseStrings.eversense_last_calibration), date(state.lastCalibrationDate)))
            val calibrationDue = state.nextCalibrationDate > 0 && now >= state.nextCalibrationDate
            add(row(rh.gs(EversenseStrings.eversense_next_calibration), date(state.nextCalibrationDate), if (calibrationDue) StatusLevel.WARNING else StatusLevel.UNSPECIFIED))
            if (state.firmwareVersion.isNotEmpty()) add(row(rh.gs(CoreUiStrings.firmware), state.firmwareVersion))
            state.activeAlarms.forEach { add(row(rh.gs(CoreUiStrings.alarm), rh.gs(it.code.title), StatusLevel.CRITICAL)) }
        }
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
        const val HOUR_MS = 60 * 60 * 1000L
        const val REFRESH_AFTER_UNPAIR_MS = 500L
        const val REFRESH_AFTER_SYNC_MS = 5_000L
    }
}

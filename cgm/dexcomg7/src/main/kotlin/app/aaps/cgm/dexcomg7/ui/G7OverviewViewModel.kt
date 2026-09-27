package app.aaps.cgm.dexcomg7.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.cgm.dexcomg7.R
import app.aaps.cgm.dexcomg7.alarm.G7AlarmRuntime
import app.aaps.cgm.dexcomg7.alarm.G7Alarms
import app.aaps.cgm.dexcomg7.data.G7CalibrationRecord
import app.aaps.cgm.dexcomg7.data.G7State
import app.aaps.cgm.dexcomg7.data.G7StateStore
import app.aaps.cgm.dexcomg7.protocol.AlgorithmState
import app.aaps.cgm.dexcomg7.protocol.G7Lifecycle
import app.aaps.cgm.dexcomg7.protocol.G7LifecycleState
import app.aaps.cgm.dexcomg7.protocol.G7Trend
import app.aaps.cgm.dexcomg7.session.G7ConnectionStatus
import app.aaps.cgm.dexcomg7.session.G7Session
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.ActionCategory
import app.aaps.core.ui.compose.pump.PumpAction
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import app.aaps.core.ui.compose.pump.StatusBanner
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlin.math.abs

/** One-time requests from the status screen to the screen host. */
sealed class G7OverviewEvent {

    data object StartPairing : G7OverviewEvent()
    data object ShowLog : G7OverviewEvent()
    data object Calibrate : G7OverviewEvent()
    data object ConfirmForget : G7OverviewEvent()
}

/** The status screen: sensor, last reading, session times, connection, calibration. */
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class G7OverviewViewModel(
    private val rh: ResourceHelper,
    private val dateUtil: DateUtil,
    private val profileUtil: ProfileUtil,
    private val store: G7StateStore,
    private val session: G7Session,
    private val alarms: G7Alarms
) : ViewModel() {

    val events = MutableSharedFlow<G7OverviewEvent>(extraBufferCapacity = 4)

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TICK_MS)
        }
    }

    val uiState: StateFlow<PumpOverviewUiState> =
        combine(store.state, session.status, alarms.runtime, ticker) { state, status, alarm, now -> build(state, status, alarm, now) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PumpOverviewUiState())

    val canCalibrate: Boolean get() = store.value.lifecycleState(System.currentTimeMillis()) == G7LifecycleState.OK

    val isMmol: Boolean get() = profileUtil.units == GlucoseUnit.MMOL

    fun calibrate(glucoseInUserUnits: Double) {
        val mgdl = profileUtil.convertToMgdl(glucoseInUserUnits, profileUtil.units).toInt()
        session.calibrate(mgdl, System.currentTimeMillis())
    }

    fun forgetSensor() {
        session.forgetSensor(removeBond = true)
    }

    private fun build(state: G7State, status: G7ConnectionStatus, alarm: G7AlarmRuntime, now: Long): PumpOverviewUiState {
        val lifecycle = state.lifecycleState(now)
        val raised = alarm.raised.firstOrNull()
        return PumpOverviewUiState(
            statusBanner = raised?.let { StatusBanner(rh.gs(R.string.dexcom_g7_alarm_banner, rh.gs(it.title), alarms.body(it, state)), StatusLevel.CRITICAL) }
                ?: banner(state, lifecycle, status, now),
            infoRows = rows(state, lifecycle, status, now),
            primaryActions = listOf(
                PumpAction(
                    label = rh.gs(R.string.dexcom_g7_alarm_acknowledge),
                    icon = Icons.Filled.NotificationsOff,
                    visible = raised != null,
                    onClick = { alarms.acknowledge() }
                ),
                PumpAction(
                    label = rh.gs(R.string.dexcom_g7_pair_new_sensor),
                    icon = Icons.Filled.Add,
                    onClick = { events.tryEmit(G7OverviewEvent.StartPairing) }
                ),
                PumpAction(
                    label = rh.gs(R.string.dexcom_g7_calibrate),
                    icon = Icons.Filled.WaterDrop,
                    enabled = lifecycle == G7LifecycleState.OK,
                    visible = state.isPaired,
                    onClick = { events.tryEmit(G7OverviewEvent.Calibrate) }
                ),
                PumpAction(
                    label = rh.gs(R.string.dexcom_g7_cancel_calibration),
                    icon = Icons.Filled.Close,
                    visible = state.calibration?.outcome == G7CalibrationRecord.Outcome.PENDING,
                    onClick = { session.cancelPendingCalibration() }
                )
            ),
            managementActions = listOf(
                PumpAction(
                    label = rh.gs(R.string.dexcom_g7_communication_log),
                    icon = Icons.AutoMirrored.Filled.List,
                    category = ActionCategory.MANAGEMENT,
                    onClick = { events.tryEmit(G7OverviewEvent.ShowLog) }
                ),
                PumpAction(
                    label = rh.gs(R.string.dexcom_g7_forget_sensor),
                    icon = Icons.Filled.BluetoothDisabled,
                    category = ActionCategory.MANAGEMENT,
                    visible = state.isPaired,
                    onClick = { events.tryEmit(G7OverviewEvent.ConfirmForget) }
                )
            )
        )
    }

    private fun banner(state: G7State, lifecycle: G7LifecycleState, status: G7ConnectionStatus, now: Long): StatusBanner {
        state.lastAuthenticationFailure?.let { return StatusBanner(DexcomG7Formatting.refusal(rh, it), StatusLevel.CRITICAL) }
        if (status == G7ConnectionStatus.BLOCKED) return StatusBanner(rh.gs(R.string.dexcom_g7_status_bluetooth_blocked), StatusLevel.CRITICAL)
        return when (lifecycle) {
            G7LifecycleState.UNPAIRED     -> StatusBanner(rh.gs(R.string.dexcom_g7_state_unpaired), StatusLevel.WARNING)
            G7LifecycleState.CONNECTING   -> StatusBanner(rh.gs(R.string.dexcom_g7_state_connecting), StatusLevel.NORMAL)
            G7LifecycleState.WARMUP       -> StatusBanner(
                rh.gs(R.string.dexcom_g7_state_warmup, state.warmupEndsAt?.let { DexcomG7Formatting.duration(rh, it - now) } ?: "?"),
                StatusLevel.WARNING
            )

            G7LifecycleState.OK           -> StatusBanner(
                rh.gs(R.string.dexcom_g7_state_ok, state.expiresAt?.let { DexcomG7Formatting.duration(rh, it - now) } ?: "?"),
                StatusLevel.NORMAL
            )

            G7LifecycleState.GRACE_PERIOD -> StatusBanner(rh.gs(R.string.dexcom_g7_state_grace_period), StatusLevel.WARNING)
            G7LifecycleState.EXPIRED      -> StatusBanner(rh.gs(R.string.dexcom_g7_state_expired), StatusLevel.CRITICAL)
            G7LifecycleState.FAILED       -> StatusBanner(rh.gs(R.string.dexcom_g7_state_failed), StatusLevel.CRITICAL)
        }
    }

    private fun rows(state: G7State, lifecycle: G7LifecycleState, status: G7ConnectionStatus, now: Long): List<PumpInfoRow> {
        val rows = mutableListOf<PumpInfoRow>()
        fun row(label: Int, value: String?, level: StatusLevel = StatusLevel.UNSPECIFIED) {
            if (value != null) rows += PumpInfoRow(rh.gs(label), value, level)
        }
        if (!state.isPaired) {
            row(R.string.dexcom_g7_row_how_to_start, rh.gs(R.string.dexcom_g7_row_how_to_start_text))
            state.previousSensor?.let { row(R.string.dexcom_g7_row_previous_sensor, DexcomG7Formatting.previous(rh, dateUtil, it)) }
            return rows
        }

        row(R.string.dexcom_g7_row_sensor, listOfNotNull(state.model?.displayName, state.sensorName).joinToString(" "))
        row(R.string.dexcom_g7_row_last_reading, lastReading(state, now))
        state.latestAlgorithmState?.let { row(R.string.dexcom_g7_row_sensor_state, AlgorithmState(it).toString().lowercase().replace('_', ' ')) }
        row(R.string.dexcom_g7_row_connection, connection(status), if (status == G7ConnectionStatus.BLOCKED) StatusLevel.CRITICAL else StatusLevel.UNSPECIFIED)
        state.latestConnectAt?.let { row(R.string.dexcom_g7_row_last_connect, dateUtil.minOrSecAgo(rh, it)) }
        state.activatedAt?.let { row(R.string.dexcom_g7_row_started, dateUtil.dateAndTimeString(it)) }
        if (lifecycle == G7LifecycleState.WARMUP) state.warmupEndsAt?.let { row(R.string.dexcom_g7_row_warmup_ends, dateUtil.dateAndTimeString(it)) }
        state.expiresAt?.let {
            row(R.string.dexcom_g7_row_expires, dateUtil.dateAndTimeString(it), if (it - now < G7Lifecycle.EXPIRING_SOON_LEAD_MS) StatusLevel.WARNING else StatusLevel.UNSPECIFIED)
        }
        state.endsAt?.let { row(R.string.dexcom_g7_row_grace_ends, dateUtil.dateAndTimeString(it)) }
        state.sessionLengthSeconds?.let { row(R.string.dexcom_g7_row_session_length, rh.gs(R.string.dexcom_g7_days, G7Lifecycle.lifetimeMs(it) / DAY_MS)) }
        row(R.string.dexcom_g7_row_serial, state.serial)
        row(R.string.dexcom_g7_row_pairing_code, state.pairingCode)
        row(R.string.dexcom_g7_row_firmware, state.firmware)
        state.pairedAt?.let { row(R.string.dexcom_g7_row_paired, dateUtil.dateAndTimeString(it)) }
        state.calibration?.let { row(R.string.dexcom_g7_row_calibration, calibration(it)) }
        state.previousSensor?.let { row(R.string.dexcom_g7_row_previous_sensor, DexcomG7Formatting.previous(rh, dateUtil, it)) }
        return rows
    }

    private fun lastReading(state: G7State, now: Long): String? {
        val at = state.latestReadingAt ?: return null
        val glucose = state.latestGlucose?.takeIf { state.latestAlgorithmState?.let { s -> AlgorithmState(s).hasReliableGlucose } == true }
        val value = glucose?.let { profileUtil.fromMgdlToStringInUnits(it.toDouble()) } ?: "---"
        val arrow = DexcomG7Formatting.arrow(state.latestTrendRate?.takeIf { abs(it) <= G7Trend.MAXIMUM_RATE_FOR_ARROW }?.let { G7Trend.fromRate(it) })
        return "$value $arrow  (${dateUtil.minOrSecAgo(rh, at).takeIf { at <= now } ?: ""})"
    }

    private fun connection(status: G7ConnectionStatus): String = rh.gs(
        when (status) {
            G7ConnectionStatus.STOPPED   -> R.string.dexcom_g7_connection_stopped
            G7ConnectionStatus.UNPAIRED  -> R.string.dexcom_g7_connection_unpaired
            G7ConnectionStatus.BLOCKED   -> R.string.dexcom_g7_status_bluetooth_blocked
            G7ConnectionStatus.WAITING   -> R.string.dexcom_g7_connection_waiting
            G7ConnectionStatus.CONNECTED -> R.string.dexcom_g7_connection_connected
            G7ConnectionStatus.PAIRING   -> R.string.dexcom_g7_connection_pairing
        }
    )

    private fun calibration(record: G7CalibrationRecord): String {
        val value = profileUtil.fromMgdlToStringInUnits(record.glucose.toDouble())
        val entered = dateUtil.timeString(record.enteredAt)
        return when (record.outcome) {
            G7CalibrationRecord.Outcome.PENDING  -> rh.gs(R.string.dexcom_g7_calibration_pending, value, entered)
            G7CalibrationRecord.Outcome.REJECTED -> rh.gs(R.string.dexcom_g7_calibration_rejected, value, record.status ?: 0)
            G7CalibrationRecord.Outcome.ACCEPTED -> rh.gs(
                if (record.processingStatus == "IN_PROGRESS") R.string.dexcom_g7_calibration_processing else R.string.dexcom_g7_calibration_accepted,
                value,
                dateUtil.timeString(record.outcomeAt ?: record.enteredAt)
            )
        }
    }

    companion object {

        const val TICK_MS = 30_000L
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}

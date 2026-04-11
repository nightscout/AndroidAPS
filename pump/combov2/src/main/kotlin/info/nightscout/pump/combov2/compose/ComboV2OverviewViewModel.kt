package info.nightscout.pump.combov2.compose

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.ActionCategory
import app.aaps.core.ui.compose.pump.PumpAction
import app.aaps.core.ui.compose.pump.PumpCommunicationStatus
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import app.aaps.core.ui.compose.pump.StatusBanner
import app.aaps.core.ui.compose.pump.tickerFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import info.nightscout.comboctl.base.DisplayFrame
import info.nightscout.comboctl.base.NullDisplayFrame
import info.nightscout.comboctl.parser.BatteryState
import info.nightscout.comboctl.parser.ReservoirState
import info.nightscout.pump.combov2.ComboV2Plugin
import info.nightscout.pump.combov2.R
import info.nightscout.pump.combov2.cctlBolusToIU
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Locale
import javax.inject.Inject
import kotlin.math.max
import kotlin.time.ExperimentalTime
import info.nightscout.comboctl.base.Tbr as ComboCtlTbr
import info.nightscout.comboctl.main.Pump as ComboCtlPump

sealed class ComboV2OverviewEvent {
    data object StartPairWizard : ComboV2OverviewEvent()
    data object ConfirmUnpair : ComboV2OverviewEvent()
}

data class ComboV2OverviewUiState(
    val overview: PumpOverviewUiState = PumpOverviewUiState(),
    val isPaired: Boolean = false,
    val currentActivityText: String = "",
    val currentActivityProgress: Float = 0f
)

@HiltViewModel
class ComboV2OverviewViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    rxBus: RxBus,
    private val commandQueue: CommandQueue,
    private val combov2Plugin: ComboV2Plugin,
    @ApplicationContext context: Context
) : ViewModel() {

    private val communicationStatus = PumpCommunicationStatus(rxBus, commandQueue, context, viewModelScope)

    private data class PumpSnapshot(
        val isPaired: Boolean,
        val driverState: ComboV2Plugin.DriverState,
        val lastConnectionTimestamp: Long?,
        val currentActivity: ComboV2Plugin.CurrentActivityInfo,
        val batteryState: BatteryState?,
        val reservoirLevel: ComboV2Plugin.ReservoirLevel?,
        val lastBolus: ComboCtlPump.LastBolus?,
        val currentTbr: ComboCtlTbr?,
        val baseBasalRate: Double?,
        val serialNumber: String,
        val bluetoothAddress: String
    )

    private val snapshotFlow: Flow<PumpSnapshot> = combine(
        combine(
            combov2Plugin.pairedStateUIFlow,
            combov2Plugin.driverStateUIFlow,
            combov2Plugin.lastConnectionTimestampUIFlow,
            combov2Plugin.currentActivityUIFlow,
            combov2Plugin.batteryStateUIFlow
        ) { paired, driver, lastConn, activity, battery ->
            PartA(paired, driver, lastConn, activity, battery)
        },
        combine(
            combov2Plugin.reservoirLevelUIFlow,
            combov2Plugin.lastBolusUIFlow,
            combov2Plugin.currentTbrUIFlow,
            combov2Plugin.baseBasalRateUIFlow,
            combov2Plugin.serialNumberUIFlow
        ) { reservoir, lastBolus, tbr, baseBasal, serial ->
            PartB(reservoir, lastBolus, tbr, baseBasal, serial)
        },
        combov2Plugin.bluetoothAddressUIFlow
    ) { a, b, btAddress ->
        PumpSnapshot(
            isPaired = a.isPaired,
            driverState = a.driverState,
            lastConnectionTimestamp = a.lastConnectionTimestamp,
            currentActivity = a.currentActivity,
            batteryState = a.batteryState,
            reservoirLevel = b.reservoirLevel,
            lastBolus = b.lastBolus,
            currentTbr = b.currentTbr,
            baseBasalRate = b.baseBasalRate,
            serialNumber = b.serialNumber,
            bluetoothAddress = btAddress
        )
    }

    val uiState: StateFlow<ComboV2OverviewUiState> = combine(
        snapshotFlow,
        communicationStatus.refreshTrigger,
        tickerFlow(30_000L)
    ) { snapshot, _, _ -> buildState(snapshot) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ComboV2OverviewUiState())

    val displayFrame: StateFlow<DisplayFrame> = combov2Plugin.displayFrameUIFlow
        .map { it ?: NullDisplayFrame }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NullDisplayFrame)

    private val _events = MutableSharedFlow<ComboV2OverviewEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<ComboV2OverviewEvent> = _events

    fun onRefreshClick() {
        aapsLogger.debug(LTag.PUMP, "Refresh button clicked")
        combov2Plugin.clearPumpErrorObservedFlag()
        commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.user_request), null)
    }

    fun onPairClick() {
        _events.tryEmit(ComboV2OverviewEvent.StartPairWizard)
    }

    fun onUnpairClick() {
        _events.tryEmit(ComboV2OverviewEvent.ConfirmUnpair)
    }

    fun performUnpair() {
        aapsLogger.debug(LTag.PUMP, "Performing unpair")
        combov2Plugin.unpair()
    }

    private fun managementActions(isPaired: Boolean): List<PumpAction> = listOf(
        if (isPaired) {
            PumpAction(
                label = rh.gs(app.aaps.core.ui.R.string.pump_unpair),
                icon = Icons.Filled.BluetoothDisabled,
                category = ActionCategory.MANAGEMENT,
                onClick = { onUnpairClick() }
            )
        } else {
            PumpAction(
                label = rh.gs(app.aaps.core.ui.R.string.pairing),
                icon = Icons.Filled.Bluetooth,
                category = ActionCategory.MANAGEMENT,
                onClick = { onPairClick() }
            )
        }
    )

    private fun buildState(snapshot: PumpSnapshot): ComboV2OverviewUiState {
        if (!snapshot.isPaired) {
            return ComboV2OverviewUiState(
                overview = PumpOverviewUiState(
                    statusBanner = StatusBanner(
                        text = rh.gs(R.string.combov2_not_paired),
                        level = StatusLevel.UNSPECIFIED
                    ),
                    queueStatus = communicationStatus.queueStatus(),
                    managementActions = managementActions(isPaired = false)
                ),
                isPaired = false
            )
        }

        val driverState = snapshot.driverState
        val driverStateText = driverStateText(driverState)
        val driverStateLevel = when (driverState) {
            ComboV2Plugin.DriverState.Error     -> StatusLevel.CRITICAL
            ComboV2Plugin.DriverState.Suspended -> StatusLevel.WARNING
            else                                -> StatusLevel.NORMAL
        }

        val infoRows = buildList {
            add(
                PumpInfoRow(
                    label = rh.gs(R.string.combov2_driver_state_label),
                    value = driverStateText,
                    level = driverStateLevel
                )
            )
            lastConnectionRow(snapshot.lastConnectionTimestamp)?.let { add(it) }
            batteryRow(snapshot.batteryState)?.let { add(it) }
            reservoirRow(snapshot.reservoirLevel)?.let { add(it) }
            lastBolusRow(snapshot.lastBolus)?.let { add(it) }
            currentTbrRow(snapshot.currentTbr)?.let { add(it) }
            snapshot.baseBasalRate?.let {
                add(
                    PumpInfoRow(
                        label = rh.gs(app.aaps.core.ui.R.string.base_basal_rate_label),
                        value = rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, it)
                    )
                )
            }
            if (snapshot.serialNumber.isNotEmpty()) {
                add(
                    PumpInfoRow(
                        label = rh.gs(app.aaps.core.ui.R.string.serial_number),
                        value = snapshot.serialNumber
                    )
                )
            }
            if (snapshot.bluetoothAddress.isNotEmpty()) {
                add(
                    PumpInfoRow(
                        label = rh.gs(R.string.bluetooth_address),
                        value = snapshot.bluetoothAddress.uppercase(Locale.ROOT)
                    )
                )
            }
        }

        val refreshEnabled = when (driverState) {
            ComboV2Plugin.DriverState.Disconnected,
            ComboV2Plugin.DriverState.Suspended,
            ComboV2Plugin.DriverState.Error -> true

            else                            -> false
        }

        val primaryActions = listOf(
            PumpAction(
                label = rh.gs(app.aaps.core.ui.R.string.refresh),
                iconRes = app.aaps.core.ui.R.drawable.ic_refresh,
                category = ActionCategory.PRIMARY,
                enabled = refreshEnabled,
                onClick = { onRefreshClick() }
            )
        )

        return ComboV2OverviewUiState(
            overview = PumpOverviewUiState(
                statusBanner = communicationStatus.statusBanner(),
                queueStatus = communicationStatus.queueStatus(),
                infoRows = infoRows,
                primaryActions = primaryActions,
                managementActions = managementActions(isPaired = true)
            ),
            isPaired = true,
            currentActivityText = snapshot.currentActivity.description,
            currentActivityProgress = snapshot.currentActivity.overallProgress.toFloat().coerceIn(0f, 1f)
        )
    }

    private fun driverStateText(state: ComboV2Plugin.DriverState): String = when (state) {
        ComboV2Plugin.DriverState.NotInitialized      -> rh.gs(R.string.combov2_not_initialized)
        ComboV2Plugin.DriverState.Disconnected        -> rh.gs(app.aaps.core.ui.R.string.disconnected)
        ComboV2Plugin.DriverState.Connecting          -> rh.gs(app.aaps.core.ui.R.string.connecting)
        ComboV2Plugin.DriverState.CheckingPump        -> rh.gs(R.string.combov2_checking_pump)
        ComboV2Plugin.DriverState.Ready               -> rh.gs(R.string.combov2_ready)
        ComboV2Plugin.DriverState.Suspended           -> rh.gs(R.string.combov2_suspended)
        ComboV2Plugin.DriverState.Error               -> rh.gs(app.aaps.core.ui.R.string.error)
        is ComboV2Plugin.DriverState.ExecutingCommand ->
            when (val desc = state.description) {
                is ComboCtlPump.GettingBasalProfileCommandDesc  ->
                    rh.gs(R.string.combov2_getting_basal_profile_cmddesc)

                is ComboCtlPump.SettingBasalProfileCommandDesc  ->
                    rh.gs(R.string.combov2_setting_basal_profile_cmddesc)

                is ComboCtlPump.SettingTbrCommandDesc           ->
                    if (desc.percentage != 100)
                        rh.gs(R.string.combov2_setting_tbr_cmddesc, desc.percentage, desc.durationInMinutes)
                    else
                        rh.gs(R.string.combov2_cancelling_tbr)

                is ComboCtlPump.DeliveringBolusCommandDesc      ->
                    rh.gs(R.string.combov2_delivering_bolus_cmddesc, desc.immediateBolusAmount.cctlBolusToIU())

                is ComboCtlPump.FetchingTDDHistoryCommandDesc   ->
                    rh.gs(R.string.combov2_fetching_tdd_history_cmddesc)

                is ComboCtlPump.UpdatingPumpDateTimeCommandDesc ->
                    rh.gs(R.string.combov2_updating_pump_datetime_cmddesc)

                is ComboCtlPump.UpdatingPumpStatusCommandDesc   ->
                    rh.gs(R.string.combov2_updating_pump_status_cmddesc)

                else                                            -> rh.gs(R.string.combov2_executing_command)
            }
    }

    private fun lastConnectionRow(lastConnectionTimestamp: Long?): PumpInfoRow? {
        if (lastConnectionTimestamp == null) return null
        val secondsPassed = (System.currentTimeMillis() - lastConnectionTimestamp) / 1000
        val (text, level) = when (secondsPassed) {
            in 0..60         -> rh.gs(R.string.combov2_less_than_one_minute_ago) to StatusLevel.NORMAL
            in 60..(30 * 60) -> rh.gs(app.aaps.core.interfaces.R.string.minago, secondsPassed / 60) to StatusLevel.NORMAL
            else             -> rh.gs(R.string.combov2_no_connection_for_n_mins, secondsPassed / 60) to StatusLevel.CRITICAL
        }
        return PumpInfoRow(
            label = rh.gs(app.aaps.core.ui.R.string.last_connection_label),
            value = text,
            level = level
        )
    }

    private fun batteryRow(batteryState: BatteryState?): PumpInfoRow? {
        if (batteryState == null) return null
        val (text, level) = when (batteryState) {
            BatteryState.NO_BATTERY   -> rh.gs(R.string.combov2_battery_empty) to StatusLevel.CRITICAL
            BatteryState.LOW_BATTERY  -> rh.gs(R.string.combov2_battery_low) to StatusLevel.WARNING
            BatteryState.FULL_BATTERY -> rh.gs(R.string.combov2_battery_full) to StatusLevel.NORMAL
        }
        return PumpInfoRow(
            label = rh.gs(app.aaps.core.ui.R.string.battery_label),
            value = text,
            level = level
        )
    }

    private fun reservoirRow(reservoirLevel: ComboV2Plugin.ReservoirLevel?): PumpInfoRow? {
        if (reservoirLevel == null) return null
        val level = when (reservoirLevel.state) {
            ReservoirState.EMPTY -> StatusLevel.CRITICAL
            ReservoirState.LOW   -> StatusLevel.WARNING
            ReservoirState.FULL  -> StatusLevel.NORMAL
        }
        return PumpInfoRow(
            label = rh.gs(app.aaps.core.ui.R.string.reservoir_label),
            value = "${reservoirLevel.availableUnits} ${rh.gs(app.aaps.core.ui.R.string.insulin_unit_shortname)}",
            level = level
        )
    }

    @OptIn(ExperimentalTime::class)
    private fun lastBolusRow(lastBolus: ComboCtlPump.LastBolus?): PumpInfoRow? {
        if (lastBolus == null) return null
        val secondsPassed = (System.currentTimeMillis() - lastBolus.timestamp.toEpochMilliseconds()) / 1000
        val bolusAgoText = when (secondsPassed) {
            in 0..59 -> rh.gs(R.string.combov2_less_than_one_minute_ago)
            else     -> rh.gs(app.aaps.core.interfaces.R.string.minago, secondsPassed / 60)
        }
        val text = rh.gs(
            R.string.combov2_last_bolus,
            lastBolus.bolusAmount.cctlBolusToIU(),
            rh.gs(app.aaps.core.ui.R.string.insulin_unit_shortname),
            bolusAgoText
        )
        return PumpInfoRow(
            label = rh.gs(app.aaps.core.ui.R.string.last_bolus_label),
            value = text
        )
    }

    @OptIn(ExperimentalTime::class)
    private fun currentTbrRow(currentTbr: ComboCtlTbr?): PumpInfoRow? {
        if (currentTbr == null) return null
        val remainingSeconds = max(
            currentTbr.durationInMinutes * 60 - (System.currentTimeMillis() - currentTbr.timestamp.toEpochMilliseconds()) / 1000,
            0
        )
        val text = if (remainingSeconds >= 60)
            rh.gs(R.string.combov2_current_tbr, currentTbr.percentage, remainingSeconds / 60)
        else
            rh.gs(R.string.combov2_current_tbr_less_than_1min, currentTbr.percentage)
        return PumpInfoRow(
            label = rh.gs(app.aaps.core.ui.R.string.tempbasal_label),
            value = text
        )
    }

    private data class PartA(
        val isPaired: Boolean,
        val driverState: ComboV2Plugin.DriverState,
        val lastConnectionTimestamp: Long?,
        val currentActivity: ComboV2Plugin.CurrentActivityInfo,
        val batteryState: BatteryState?
    )

    private data class PartB(
        val reservoirLevel: ComboV2Plugin.ReservoirLevel?,
        val lastBolus: ComboCtlPump.LastBolus?,
        val currentTbr: ComboCtlTbr?,
        val baseBasalRate: Double?,
        val serialNumber: String
    )
}

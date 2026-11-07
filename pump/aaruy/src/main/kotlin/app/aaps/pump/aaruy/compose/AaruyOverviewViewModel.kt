package app.aaps.pump.aaruy.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.ScreenAction
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.PumpCommunicationStatus
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import app.aaps.core.ui.compose.pump.tickerFlow
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.R
import app.aaps.pump.aaruy.common.enums.AaruyPumpState
import app.aaps.pump.aaruy.services.AaruyBLE
import app.aaps.pump.aaruy.services.AaruyConfirmAlarmsCommand
import app.aaps.pump.aaruy.services.AaruyUnpairCommand
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import app.aaps.core.interfaces.R as InterfacesR
import app.aaps.core.ui.R as CoreUiR

sealed class AaruyOverviewEvent {
    data object StartPairWizard : AaruyOverviewEvent()
    data object StartReplace : AaruyOverviewEvent()
    data object StartHistory : AaruyOverviewEvent()
    data object ConfirmUnpair : AaruyOverviewEvent()

    /** The unpair could not reach the pump. */
    data object PumpNotReached : AaruyOverviewEvent()
}

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class AaruyOverviewViewModel(
    private val rh: ResourceHelper,
    rxBus: RxBus,
    private val commandQueue: CommandQueue,
    private val dateUtil: DateUtil,
    private val aaruyPump: AaruyPump,
    private val aaruyBle: AaruyBLE,
    private val activePlugin: ActivePlugin,
    private val ch: ConcentrationHelper
) : ViewModel() {

    private val _events = MutableSharedFlow<AaruyOverviewEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<AaruyOverviewEvent> = _events

    private val communicationStatus = PumpCommunicationStatus(rxBus, commandQueue, rh, viewModelScope)

    private val pumpFlow = combine(
        aaruyPump.pumpStateFlow,
        aaruyPump.connectionStateFlow,
        aaruyPump.reservoirFlow,
        aaruyPump.remainBatteryFlow,
        aaruyPump.lastBasalRateFlow
    ) { _, _, _, _, _ -> }

    private val moreFlow = combine(
        aaruyPump.lastBolusTimeFlow,
        aaruyPump.lastBolusAmountFlow,
        aaruyPump.lastConnectionFlow,
        aaruyPump.activeAlarmsFlow
    ) { _, _, _, _ -> }

    val uiState: StateFlow<PumpOverviewUiState> = combine(
        pumpFlow,
        moreFlow,
        communicationStatus.refreshTrigger,
        tickerFlow(60_000L)
    ) { _, _, _, _ -> buildUiState() }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), buildUiState())

    private fun onRefreshClick() {
        viewModelScope.launch { commandQueue.readStatus(rh.gs(CoreUiR.string.clicked_connect_to_pump)) }
    }

    /** Through the queue, which opens the link. A confirm that does not reach the pump leaves the alarms listed. */
    private fun onConfirmAlarmsClick() {
        viewModelScope.launch { commandQueue.customCommand(AaruyConfirmAlarmsCommand()) }
    }

    val pumpName: String get() = aaruyBle.getShortDeviceName()

    /** Through the queue, which opens the link: the pump is told, so it can be paired again. */
    fun unpair() {
        viewModelScope.launch {
            if (!commandQueue.customCommand(AaruyUnpairCommand()).success) _events.emit(AaruyOverviewEvent.PumpNotReached)
        }
    }

    /** The pump could not be told. It stays paired on its side, so the screen asked a second time. */
    fun forgetPump() {
        aaruyPump.clearAlarmState()
        aaruyBle.unbindDevice()
    }

    private fun pumpStateText(state: AaruyPumpState): String = rh.gs(
        when (state) {
            AaruyPumpState.NO_RUNNING           -> R.string.aaruy_state_not_running
            AaruyPumpState.BASALRATE_RUNNING    -> R.string.aaruy_state_basal
            AaruyPumpState.TMPBASALRATE_RUNNING -> R.string.aaruy_state_temp_basal
            AaruyPumpState.LARGEDOSE_RUNNING    -> R.string.aaruy_state_bolus
            AaruyPumpState.EXHAUSTING           -> R.string.is_exhaust
            AaruyPumpState.PUSHBACK_RUNNING     -> R.string.is_backing
            AaruyPumpState.PUSHFORWARD_RUNNING  -> R.string.aaruy_state_priming
            AaruyPumpState.PAUSE_RUNNING        -> R.string.aaruy_state_paused
            AaruyPumpState.STOP_RUNNING         -> R.string.aaruy_state_stopped
            AaruyPumpState.FIRST_INIT           -> R.string.aaruy_state_unknown
        }
    )

    private fun buildUiState(): PumpOverviewUiState {
        val pump = aaruyPump
        val activePump = activePlugin.activePump
        val isConfigured = activePump.isConfigured()
        val isInitialized = activePump.isInitialized()
        val alarms = pump.activeAlarms.toList()

        val lastConnectionTime = pump.lastConnection
        val agoMinutes = ((System.currentTimeMillis() - lastConnectionTime) / 60_000).toInt()

        val infoRows = if (!isConfigured) emptyList() else buildList {
            add(PumpInfoRow(label = rh.gs(CoreUiR.string.serial_number), value = aaruyBle.getShortDeviceName()))
            add(
                PumpInfoRow(
                    label = rh.gs(R.string.pump_state_label),
                    value = pumpStateText(pump.pumpState),
                    // A pump that is stopped gives no insulin, and it has no screen to show that.
                    level = if (pump.pumpState.isSuspendedByPump()) StatusLevel.CRITICAL else StatusLevel.NORMAL
                )
            )
            add(
                PumpInfoRow(
                    label = rh.gs(R.string.active_alarms_label),
                    value = alarms.joinToString("\n") { pump.alarmStateToString(it) },
                    level = StatusLevel.CRITICAL,
                    visible = alarms.isNotEmpty()
                )
            )
            // Not known until the first status after an app start: shown as "-", not as an empty battery.
            val battery = pump.remainBattery
            add(
                PumpInfoRow(
                    label = rh.gs(CoreUiR.string.battery_label),
                    value = battery?.let { rh.gs(CoreUiR.string.units_format_percent, it.toInt()) } ?: NOT_KNOWN,
                    level = when {
                        battery == null -> StatusLevel.NORMAL
                        battery <= 26   -> StatusLevel.CRITICAL
                        battery <= 51   -> StatusLevel.WARNING
                        else            -> StatusLevel.NORMAL
                    }
                )
            )
            if (lastConnectionTime != 0L) {
                add(
                    PumpInfoRow(
                        label = rh.gs(CoreUiR.string.last_connection_label),
                        value = rh.gs(InterfacesR.string.value_with_detail, dateUtil.timeString(lastConnectionTime), rh.gs(InterfacesR.string.minago, agoMinutes)),
                        level = when {
                            agoMinutes >= 31 -> StatusLevel.CRITICAL
                            agoMinutes >= 16 -> StatusLevel.WARNING
                            else             -> StatusLevel.NORMAL
                        }
                    )
                )
            }
            if (pump.lastBolusTime != 0L) {
                add(PumpInfoRow(label = rh.gs(CoreUiR.string.last_bolus_label), value = ch.insulinAmountAgoString(PumpInsulin(pump.lastBolusAmount), pump.lastBolusTime) ?: ""))
            }
            add(PumpInfoRow(label = rh.gs(CoreUiR.string.base_basal_rate_label), value = ch.basalRateString(activePump.baseBasalRate, true)))
            add(
                PumpInfoRow(
                    label = rh.gs(CoreUiR.string.tempbasal_label),
                    value = if (pump.isTempBasalInProgress) ch.basalTbrString(
                        // From the database, so in insulin units. The pump runs it in its own units.
                        rate = ch.toPumpRate(pump.tempBasalAbsoluteRate),
                        startTime = pump.tempBasalStart,
                        durationInMin = T.msecs(pump.tempBasalDuration).mins().toInt()
                    ) else "",
                    visible = pump.isTempBasalInProgress
                )
            )
            val reservoir = pump.reservoir
            add(
                PumpInfoRow(
                    label = rh.gs(CoreUiR.string.reservoir_label),
                    value = reservoir?.let { ch.insulinAmountString(PumpInsulin(it)) } ?: NOT_KNOWN,
                    level = when {
                        reservoir == null                -> StatusLevel.NORMAL
                        reservoir <= pump.lowAlert       -> StatusLevel.CRITICAL
                        reservoir <= pump.lowAlert * 2   -> StatusLevel.WARNING
                        else                             -> StatusLevel.NORMAL
                    }
                )
            )
            add(PumpInfoRow(label = rh.gs(CoreUiR.string.firmware), value = pump.swVersion, visible = pump.swVersion.isNotEmpty()))
        }

        val primaryActions = listOf(
            ScreenAction(label = rh.gs(CoreUiR.string.refresh), icon = Icons.Filled.Refresh, visible = isInitialized, onClick = { onRefreshClick() }),
            ScreenAction(label = rh.gs(CoreUiR.string.pump_history), icon = Icons.AutoMirrored.Filled.List, visible = isInitialized, onClick = { _events.tryEmit(AaruyOverviewEvent.StartHistory) })
        )

        val managementActions = buildList {
            // A stopped pump only starts through this, as it has no screen of its own. Not during a bolus: it would end it.
            if (isInitialized && pump.pumpState != AaruyPumpState.LARGEDOSE_RUNNING) {
                add(ScreenAction(label = rh.gs(R.string.replace_label), icon = Icons.Filled.SwapHoriz, onClick = { _events.tryEmit(AaruyOverviewEvent.StartReplace) }))
            }
            // Also without a link: the queue opens one for it.
            if (alarms.isNotEmpty()) add(ScreenAction(label = rh.gs(R.string.confirm_alarms_label), icon = Icons.Filled.Check, onClick = { onConfirmAlarmsClick() }))
            add(
                if (isConfigured) ScreenAction(label = rh.gs(CoreUiR.string.pump_unpair), icon = Icons.Filled.Bluetooth, onClick = { _events.tryEmit(AaruyOverviewEvent.ConfirmUnpair) })
                else ScreenAction(label = rh.gs(CoreUiR.string.pump_pair), icon = Icons.Filled.Bluetooth, onClick = { _events.tryEmit(AaruyOverviewEvent.StartPairWizard) })
            )
        }

        return PumpOverviewUiState(
            statusBanner = communicationStatus.statusBanner(),
            queueStatus = communicationStatus.queueStatus(),
            infoRows = infoRows,
            primaryActions = primaryActions,
            managementActions = managementActions
        )
    }

    private companion object {

        /** As the status lights of the overview show a value that is not known. */
        const val NOT_KNOWN = "-"
    }
}

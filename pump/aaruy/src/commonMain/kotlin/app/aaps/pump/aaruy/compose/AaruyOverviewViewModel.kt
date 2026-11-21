package app.aaps.pump.aaruy.compose

import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.ui.CoreUiStrings
import app.aaps.pump.aaruy.AaruyStrings
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
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.ScreenAction
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.PumpCommunicationStatus
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import app.aaps.core.ui.compose.pump.tickerFlow
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.common.enums.AaruyPumpState
import app.aaps.pump.aaruy.services.AaruyBLE
import app.aaps.pump.aaruy.services.AaruyConfirmAlarmsCommand
import app.aaps.pump.aaruy.services.AaruyUnpair
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class AaruyOverviewEvent {
    data object StartPairWizard : AaruyOverviewEvent()
    data object StartReplace : AaruyOverviewEvent()
    data object StartHistory : AaruyOverviewEvent()
    data object ConfirmUnpair : AaruyOverviewEvent()
}

@Stable
@Inject
class AaruyOverviewViewModel(
    private val rh: TextResolver,
    rxBus: RxBus,
    private val commandQueue: CommandQueue,
    private val dateUtil: DateUtil,
    private val aaruyPump: AaruyPump,
    private val aaruyBle: AaruyBLE,
    private val aaruyUnpair: AaruyUnpair,
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
        viewModelScope.launch { commandQueue.readStatus(rh.gs(CoreUiStrings.clicked_connect_to_pump)) }
    }

    /** Through the queue, which opens the link. A confirm that does not reach the pump leaves the alarms listed. */
    private fun onConfirmAlarmsClick() {
        viewModelScope.launch { commandQueue.customCommand(AaruyConfirmAlarmsCommand()) }
    }

    val pumpName: String get() = aaruyBle.getShortDeviceName()

    /** The pump is told first, and removed also when it cannot be reached. Not in this screen's scope: leaving it must not stop that. */
    fun unpair() = aaruyUnpair.start()

    private fun pumpStateText(state: AaruyPumpState): String = rh.gs(
        when (state) {
            AaruyPumpState.NO_RUNNING           -> AaruyStrings.aaruy_state_not_running
            AaruyPumpState.BASALRATE_RUNNING    -> AaruyStrings.aaruy_state_basal
            AaruyPumpState.TMPBASALRATE_RUNNING -> AaruyStrings.aaruy_state_temp_basal
            AaruyPumpState.LARGEDOSE_RUNNING    -> AaruyStrings.aaruy_state_bolus
            AaruyPumpState.EXHAUSTING           -> AaruyStrings.is_exhaust
            AaruyPumpState.PUSHBACK_RUNNING     -> AaruyStrings.is_backing
            AaruyPumpState.PUSHFORWARD_RUNNING  -> AaruyStrings.aaruy_state_priming
            AaruyPumpState.PAUSE_RUNNING        -> AaruyStrings.aaruy_state_paused
            AaruyPumpState.STOP_RUNNING         -> AaruyStrings.aaruy_state_stopped
            AaruyPumpState.FIRST_INIT           -> AaruyStrings.aaruy_state_unknown
        }
    )

    private fun buildUiState(): PumpOverviewUiState {
        val pump = aaruyPump
        val activePump = activePlugin.activePump
        val isConfigured = activePump.isConfigured()
        val isInitialized = activePump.isInitialized()
        val alarms = pump.activeAlarms.keys.toList()

        val lastConnectionTime = pump.lastConnection
        val agoMinutes = ((dateUtil.now() - lastConnectionTime) / 60_000).toInt()

        val infoRows = if (!isConfigured) emptyList() else buildList {
            add(PumpInfoRow(label = rh.gs(CoreUiStrings.serial_number), value = aaruyBle.getShortDeviceName()))
            add(
                PumpInfoRow(
                    label = rh.gs(AaruyStrings.pump_state_label),
                    value = pumpStateText(pump.pumpState),
                    // A pump that is stopped gives no insulin, and it has no screen to show that.
                    level = if (pump.pumpState.isSuspendedByPump()) StatusLevel.CRITICAL else StatusLevel.NORMAL
                )
            )
            add(
                PumpInfoRow(
                    label = rh.gs(AaruyStrings.active_alarms_label),
                    value = alarms.joinToString("\n") { pump.alarmStateToString(it) },
                    level = StatusLevel.CRITICAL,
                    visible = alarms.isNotEmpty()
                )
            )
            // Not known until the first status after an app start: shown as "-", not as an empty battery.
            val battery = pump.remainBattery
            add(
                PumpInfoRow(
                    label = rh.gs(CoreUiStrings.battery_label),
                    value = battery?.let { rh.gs(CoreUiStrings.units_format_percent, it.toInt()) } ?: NOT_KNOWN,
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
                        label = rh.gs(CoreUiStrings.last_connection_label),
                        value = rh.gs(InterfacesStrings.value_with_detail, dateUtil.timeString(lastConnectionTime), rh.gs(InterfacesStrings.minago, agoMinutes)),
                        level = when {
                            agoMinutes >= 31 -> StatusLevel.CRITICAL
                            agoMinutes >= 16 -> StatusLevel.WARNING
                            else             -> StatusLevel.NORMAL
                        }
                    )
                )
            }
            if (pump.lastBolusTime != 0L) {
                add(PumpInfoRow(label = rh.gs(CoreUiStrings.last_bolus_label), value = ch.insulinAmountAgoString(PumpInsulin(pump.lastBolusAmount), pump.lastBolusTime) ?: ""))
            }
            add(PumpInfoRow(label = rh.gs(CoreUiStrings.base_basal_rate_label), value = ch.basalRateString(activePump.baseBasalRate, true)))
            add(
                PumpInfoRow(
                    label = rh.gs(CoreUiStrings.tempbasal_label),
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
                    label = rh.gs(CoreUiStrings.reservoir_label),
                    value = reservoir?.let { ch.insulinAmountString(PumpInsulin(it)) } ?: NOT_KNOWN,
                    level = when {
                        reservoir == null                -> StatusLevel.NORMAL
                        reservoir <= pump.lowAlert       -> StatusLevel.CRITICAL
                        reservoir <= pump.lowAlert * 2   -> StatusLevel.WARNING
                        else                             -> StatusLevel.NORMAL
                    }
                )
            )
            add(PumpInfoRow(label = rh.gs(CoreUiStrings.firmware), value = pump.swVersion, visible = pump.swVersion.isNotEmpty()))
            // Seen on a real pump: settings land only when no temp basal runs, a bolus step only while it is stopped.
            val settingsGap = pump.settingsGap()
            add(
                PumpInfoRow(
                    label = rh.gs(AaruyStrings.aaruy_settings_label),
                    value = rh.gs(if (settingsGap == AaruyPump.SettingsGap.STEP_ONLY) AaruyStrings.aaruy_bolus_step_not_in_pump else AaruyStrings.aaruy_settings_not_in_pump),
                    level = StatusLevel.WARNING,
                    visible = isInitialized && settingsGap != AaruyPump.SettingsGap.NONE
                )
            )
        }

        val primaryActions = listOf(
            ScreenAction(label = rh.gs(CoreUiStrings.refresh), icon = Icons.Filled.Refresh, visible = isInitialized, onClick = { onRefreshClick() }),
            ScreenAction(label = rh.gs(CoreUiStrings.pump_history), icon = Icons.AutoMirrored.Filled.List, visible = isInitialized, onClick = { _events.tryEmit(AaruyOverviewEvent.StartHistory) })
        )

        val managementActions = buildList {
            // A stopped pump only starts through this, as it has no screen of its own. Not during a bolus: it would end it.
            if (isInitialized && pump.pumpState != AaruyPumpState.LARGEDOSE_RUNNING) {
                add(ScreenAction(label = rh.gs(AaruyStrings.replace_label), icon = Icons.Filled.SwapHoriz, onClick = { _events.tryEmit(AaruyOverviewEvent.StartReplace) }))
            }
            // Also without a link: the queue opens one for it.
            // Only for an alarm the pump holds until it is confirmed. A stop or an empty reservoir ends on the pump.
            if (alarms.any { it.isConfirmedOnThePump() }) add(ScreenAction(label = rh.gs(AaruyStrings.confirm_alarms_label), icon = Icons.Filled.Check, onClick = { onConfirmAlarmsClick() }))
            add(
                if (isConfigured) ScreenAction(label = rh.gs(CoreUiStrings.pump_unpair), icon = Icons.Filled.Bluetooth, onClick = { _events.tryEmit(AaruyOverviewEvent.ConfirmUnpair) })
                else ScreenAction(label = rh.gs(CoreUiStrings.pump_pair), icon = Icons.Filled.Bluetooth, onClick = { _events.tryEmit(AaruyOverviewEvent.StartPairWizard) })
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

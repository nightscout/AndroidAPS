package app.aaps.pump.aaruy.compose

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.ScreenAction
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.PumpCommunicationStatus
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import app.aaps.core.ui.compose.pump.tickerFlow
import app.aaps.pump.aaruy.AaruyPlugin
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.R
import app.aaps.pump.aaruy.common.enums.AaruyType
import app.aaps.pump.aaruy.event.EventAaruyDeviceChange
import app.aaps.pump.aaruy.event.EventAaruyPumpConfigurationChanged
import app.aaps.pump.aaruy.event.EventAaruyPumpValuesChanged
import app.aaps.pump.aaruy.keys.AaruyStringKey
import app.aaps.pump.aaruy.services.AaruyService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
    data object StartHistory : AaruyOverviewEvent()
    data object StartReplaceSettings : AaruyOverviewEvent()
    data object ConfirmUnpair : AaruyOverviewEvent()
}

// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class AaruyOverviewViewModel(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val rxBus: RxBus,
    private val commandQueue: CommandQueue,
    private val dateUtil: DateUtil,
    private val aaruyPump: AaruyPump,
    private val aaruyPlugin: AaruyPlugin,
    private val activePlugin: ActivePlugin,
    private val uel: UserEntryLogger,
    private val preferences: Preferences,
    private val ch: ConcentrationHelper,
) : ViewModel() {
    val aaruyService: AaruyService?
        get() = aaruyPlugin.getService()

    private val _events = MutableSharedFlow<AaruyOverviewEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<AaruyOverviewEvent> = _events

    private val communicationStatus = PumpCommunicationStatus(rxBus, commandQueue, rh, viewModelScope)

    val pumpSn = aaruyPump.pumpSN

    private val aaruyRefresh = MutableStateFlow(0L).also { flow ->
        viewModelScope.launch {
            rxBus.toFlow(EventAaruyDeviceChange::class)
                .collect {

                    flow.value = System.currentTimeMillis()
                }
        }
        viewModelScope.launch {
            rxBus.toFlow(EventAaruyPumpValuesChanged::class)
                .collect { flow.value = System.currentTimeMillis() }
        }
        viewModelScope.launch {
            rxBus.toFlow(EventAaruyPumpConfigurationChanged::class)
                .collect {
                    aapsLogger.debug(LTag.PUMP, "EventMedtronicPumpConfigurationChanged triggered")
                    commandQueue.readStatus(rh.gs(R.string.requested_by_user))
                    flow.value = System.currentTimeMillis()
                }
        }
    }

    private data class PumpSnapshot(
        val lastConnectionTime: Long,
        val reservoir: Double,
        val battery: Int?,
        val lastBolusTime: Long?,
        val lastBolusAmount: Double?
    )

    private val pumpDataFlow = combine(
        aaruyPump.lastConnectionFlow,
        aaruyPump.reservoirFlow,
        aaruyPump.remainBatteryFlow,
        aaruyPump.lastBolusTimeFlow,
        aaruyPump.lastBolusAmountFlow
    ) { lastConnection, reservoir, battery, lastBolusTime, lastBolusAmount ->
        PumpSnapshot(lastConnection, reservoir, battery, lastBolusTime, lastBolusAmount)
    }

    private val refreshFlow = combine(
        aaruyRefresh,
        communicationStatus.refreshTrigger,
        tickerFlow(60_000L)
    ) { _, _, _ -> }

    val uiState: StateFlow<PumpOverviewUiState> = combine(
        pumpDataFlow,
        refreshFlow
    ) { snapshot, _ ->
        buildUiState(snapshot.lastConnectionTime, snapshot.reservoir, snapshot.battery, snapshot.lastBolusTime, snapshot.lastBolusAmount)
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = buildInitialState()
    )

    init {
        aaruyService?.getPumpVersionNum()
    }

    fun onRefreshClick() {
        aapsLogger.debug(LTag.PUMP, "Clicked connect to pump")
        aaruyPump.lastConnection = 0
        viewModelScope.launch { commandQueue.readStatus(rh.gs(CoreUiR.string.clicked_connect_to_pump)) }
    }

    fun onHistoryClick() = _events.tryEmit(AaruyOverviewEvent.StartHistory)
    fun onClickChangeRescv() = _events.tryEmit(AaruyOverviewEvent.StartReplaceSettings)
    fun onPairClick() = _events.tryEmit(AaruyOverviewEvent.StartPairWizard)
    fun onUnpairClick() = _events.tryEmit(AaruyOverviewEvent.ConfirmUnpair)

    fun performUnpair() {
        uel.log(Action.CLEAR_PAIRING_KEYS, Sources.Aaruy)
        aaruyPlugin.unbindPump()
        aaruyRefresh.value = System.currentTimeMillis()
    }

    private fun buildInitialState(): PumpOverviewUiState = buildUiState(
        lastConnectionTime = aaruyPump.lastConnection,
        reservoir = aaruyPump.reservoir,
        battery = aaruyPump.remainBattery,
        lastBolusTime = aaruyPump.lastBolusTime,
        lastBolusAmount = aaruyPump.lastBolusAmount
    )

    private fun temporaryBasalToString(): String {
        val pump = aaruyPump
        if (!pump.isTempBasalInProgress) return ""

        return ch.basalTbrString(
            rate = PumpRate(pump.tempBasalAbsoluteRate),
            startTime = pump.tempBasalStart,
            durationInMin = T.msecs(pump.tempBasalDuration).mins().toInt()
        )
    }

    private fun extendedBolusToString(): String {
        val pump = aaruyPump
        if (!pump.isExtendedInProgress) return ""

        return ch.basalTbrString(
            rate = PumpRate(pump.extendedBolusAbsoluteRate),
            startTime = pump.extendedBolusStart,
            durationInMin = pump.extendedBolusDurationInMinutes,
            isExtended = true
        )
    }

    private fun buildUiState(
        lastConnectionTime: Long,
        reservoir: Double,
        battery: Int?,
        lastBolusTime: Long?,
        lastBolusAmount: Double?
    ): PumpOverviewUiState {
        val pump = aaruyPump
        val activePump = activePlugin.activePump

        val statusBanner = communicationStatus.statusBanner()
        val queueStatus = communicationStatus.queueStatus()

        // Last connection
        val lastConnection = if (lastConnectionTime != 0L) {
            val agoMinutes = ((System.currentTimeMillis() - lastConnectionTime) / 60_000).toInt()
            rh.gs(InterfacesR.string.value_with_detail, dateUtil.timeString(lastConnectionTime), rh.gs(InterfacesR.string.minago, agoMinutes))
        } else ""

        // Last bolus
        val lastBolus = if (lastBolusTime != null && lastBolusAmount != null) {
            ch.insulinAmountAgoString(PumpInsulin(lastBolusAmount), lastBolusTime)
        } else null

        // Daily units
        val todayInsulinAmount = ch.fromPump(PumpInsulin(pump.todayTotalAmount))

        // Base basal rate
        val baseBasalRate = ch.basalRateString(activePump.baseBasalRate, true)

        // Temp basal / Extended bolus
        val tempBasalText = temporaryBasalToString()
        val extendedBolusText = extendedBolusToString()

        // Battery
        val batteryText = battery?.let { rh.gs(CoreUiR.string.units_format_percent, it) }

        // Reservoir
        val reservoirText = ch.insulinAmountString(PumpInsulin(pump.reservoir))

        // Warn levels
        val lastConnectionLevel = if (lastConnectionTime != 0L) {
            val agoMinutes = ((System.currentTimeMillis() - lastConnectionTime) / 60_000).toInt()
            when {
                agoMinutes >= 31 -> StatusLevel.CRITICAL
                agoMinutes >= 16 -> StatusLevel.WARNING
                else             -> StatusLevel.NORMAL
            }
        } else StatusLevel.UNSPECIFIED

        val batteryLevel = when {
            battery == null -> StatusLevel.UNSPECIFIED
            battery <= 26   -> StatusLevel.CRITICAL
            battery <= 51   -> StatusLevel.WARNING
            else            -> StatusLevel.NORMAL
        }

        val reservoirLevel = when {
            ch.fromPump(PumpInsulin(pump.reservoir)) <= 20.0 -> StatusLevel.CRITICAL
            ch.fromPump(PumpInsulin(pump.reservoir)) <= 50.0 -> StatusLevel.WARNING
            else                                                       -> StatusLevel.NORMAL
        }

        val bindAddress = preferences.get(AaruyStringKey.PumpBoundAddress)
        val isConfigured = bindAddress.isNotEmpty() && activePump.isConfigured()
        val isInitialized = activePump.isInitialized()

        // Info rows
        val infoRows = buildList {
            add(PumpInfoRow(label = rh.gs(CoreUiR.string.serial_number), value = pump.pumpSN))

            batteryText?.let {
                add(PumpInfoRow(label = rh.gs(CoreUiR.string.battery_label), value = it, level = batteryLevel))
            }

            if (lastConnection.isNotEmpty()) {
                add(PumpInfoRow(label = rh.gs(CoreUiR.string.last_connection_label), value = lastConnection, level = lastConnectionLevel))
            }

            lastBolus?.let {
                add(PumpInfoRow(label = rh.gs(CoreUiR.string.last_bolus_label), value = it))
            }

            add(
                PumpInfoRow(
                    label = rh.gs(CoreUiR.string.daily_units),
                    value = ch.insulinAmountString(PumpInsulin(todayInsulinAmount)), // "/ $todayInsulinLimitAmount U" removed
                    level = StatusLevel.NORMAL
                )
            )

            add(PumpInfoRow(label = rh.gs(CoreUiR.string.base_basal_rate_label), value = baseBasalRate))
            add(PumpInfoRow(label = rh.gs(R.string.pump_state_label), value = pump.pumpStateFlow.value.toString()))
            add(PumpInfoRow(label = rh.gs(CoreUiR.string.tempbasal_label), value = tempBasalText, visible = tempBasalText.isNotEmpty()))
            add(PumpInfoRow(label = rh.gs(CoreUiR.string.extended_bolus_label), value = extendedBolusText, visible = extendedBolusText.isNotEmpty()))
            add(PumpInfoRow(label = rh.gs(CoreUiR.string.reservoir_label), value = reservoirText, level = reservoirLevel))
            add(PumpInfoRow(label = rh.gs(R.string.setting_insulin_bolus_rate), value = ch.fromPump(PumpInsulin(pump.bolusStep*0.025)).toString()))

            // Firmware. The field names are technical and were never translated.
            val firmware = listOf(
                rh.gs(R.string.aaruy_pump),
                rh.gs(InterfacesR.string.confirmation_line, "Version", pump.swVersion),
                rh.gs(InterfacesR.string.confirmation_line, "ProductType", AaruyType.getTypeString(pump.pumpType)),
            ).joinToString("\n")
            add(PumpInfoRow(label = rh.gs(CoreUiR.string.firmware), value = firmware))
        }

        // Actions
        val primaryActions = listOf(
            ScreenAction(
                label = rh.gs(CoreUiR.string.refresh),
                icon = Icons.Filled.Refresh,
                visible = isInitialized,
                onClick = { onRefreshClick() }
            ),
            ScreenAction(
                label = rh.gs(CoreUiR.string.pump_history),
                icon = Icons.AutoMirrored.Filled.List,
                visible = isInitialized,
                onClick = { onHistoryClick() }
            )
        )

        val managementActions = buildList {
            add(
                ScreenAction(
                    label = rh.gs(R.string.replace_bat_rescv_label),
                    icon = Icons.Filled.SwapHoriz,
                    onClick = { onClickChangeRescv() }
                )
            )

            if (isConfigured) {
                add(
                    ScreenAction(
                        label = rh.gs(CoreUiR.string.pump_unpair),
                        icon = Icons.Filled.Bluetooth,
                        onClick = { onUnpairClick() }
                    )
                )
            } else {
                add(
                    ScreenAction(
                        label = rh.gs(CoreUiR.string.pump_pair),
                        icon = Icons.Filled.Bluetooth,
                        onClick = { onPairClick() }
                    )
                )
            }
        }

        return PumpOverviewUiState(
            statusBanner = statusBanner,
            queueStatus = queueStatus,
            infoRows = infoRows,
            primaryActions = primaryActions,
            managementActions = managementActions
        )
    }

}
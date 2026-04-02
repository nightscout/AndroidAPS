package app.aaps.pump.medtronic.compose

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.ActionCategory
import app.aaps.core.ui.compose.pump.PumpAction
import app.aaps.core.ui.compose.pump.PumpCommunicationStatus
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import app.aaps.core.ui.compose.pump.tickerFlow
import app.aaps.pump.common.events.EventRileyLinkDeviceStatusChange
import app.aaps.pump.common.extensions.stringResource
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkServiceState
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import app.aaps.pump.common.hw.rileylink.service.tasks.ResetRileyLinkConfigurationTask
import app.aaps.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor
import app.aaps.pump.common.hw.rileylink.service.tasks.WakeAndTuneTask
import app.aaps.pump.medtronic.MedtronicPumpPlugin
import app.aaps.pump.medtronic.R
import app.aaps.pump.medtronic.defs.BatteryType
import app.aaps.pump.medtronic.defs.MedtronicCommandType
import app.aaps.pump.medtronic.driver.MedtronicPumpStatus
import app.aaps.pump.medtronic.events.EventMedtronicPumpConfigurationChanged
import app.aaps.pump.medtronic.events.EventMedtronicPumpValuesChanged
import app.aaps.pump.medtronic.util.MedtronicUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Provider
import app.aaps.pump.common.hw.rileylink.R as RileyLinkR

sealed class MedtronicOverviewEvent {
    data object ShowHistory : MedtronicOverviewEvent()
    data object ShowRileyLinkPairWizard : MedtronicOverviewEvent()
    data object ShowRileyLinkStats : MedtronicOverviewEvent()
    data class ShowDialog(val title: String, val message: String) : MedtronicOverviewEvent()
    data class ShowSnackbar(val message: String) : MedtronicOverviewEvent()
}

@Stable
@HiltViewModel
class MedtronicOverviewViewModel @Inject constructor(
    private val rh: ResourceHelper,
    private val medtronicPumpPlugin: MedtronicPumpPlugin,
    private val medtronicPumpStatus: MedtronicPumpStatus,
    private val medtronicUtil: MedtronicUtil,
    private val rileyLinkServiceData: RileyLinkServiceData,
    private val serviceTaskExecutor: ServiceTaskExecutor,
    private val commandQueue: CommandQueue,
    private val rxBus: RxBus,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger,
    private val resetRileyLinkConfigurationTaskProvider: Provider<ResetRileyLinkConfigurationTask>,
    private val wakeAndTuneTaskProvider: Provider<WakeAndTuneTask>,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {

        private const val PLACEHOLDER = "-"
    }

    private val communicationStatus = PumpCommunicationStatus(rxBus, commandQueue, context, viewModelScope)

    private val _events = MutableSharedFlow<MedtronicOverviewEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<MedtronicOverviewEvent> = _events

    private val medtronicRefresh = MutableStateFlow(0L).also { flow ->
        viewModelScope.launch {
            rxBus.toFlow(EventMedtronicPumpValuesChanged::class.java)
                .collect { flow.value = System.currentTimeMillis() }
        }
        viewModelScope.launch {
            rxBus.toFlow(EventRileyLinkDeviceStatusChange::class.java)
                .collect { flow.value = System.currentTimeMillis() }
        }
        viewModelScope.launch {
            rxBus.toFlow(EventMedtronicPumpConfigurationChanged::class.java)
                .collect {
                    aapsLogger.debug(LTag.PUMP, "EventMedtronicPumpConfigurationChanged triggered")
                    medtronicPumpPlugin.rileyLinkService?.verifyConfiguration()
                    flow.value = System.currentTimeMillis()
                }
        }
    }

    val uiState: StateFlow<PumpOverviewUiState> = combine(
        communicationStatus.refreshTrigger,
        medtronicRefresh,
        tickerFlow(60_000L)
    ) { _, _, _ -> buildUiState() }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = buildUiState()
    )

    private fun buildUiState(): PumpOverviewUiState {
        return PumpOverviewUiState(
            statusBanner = communicationStatus.statusBanner(),
            infoRows = buildInfoRows(),
            primaryActions = buildPrimaryActions(),
            managementActions = buildManagementActions(),
            queueStatus = communicationStatus.queueStatus()
        )
    }

    // region Info Rows

    private fun buildInfoRows(): List<PumpInfoRow> = buildList {
        // RileyLink status
        val rlState = rileyLinkServiceData.rileyLinkServiceState
        val rlError = rileyLinkServiceData.rileyLinkError
        val rlStatusText = when {
            rlState == RileyLinkServiceState.NotStarted -> rh.gs(rlState.resourceId)
            rlState.isError() && rlError != null        -> rh.gs(rlError.getResourceId(RileyLinkTargetDevice.MedtronicPump))
            else                                        -> rh.gs(rlState.resourceId)
        }
        val rlLevel = if (rlState.isError() || rlError != null) StatusLevel.CRITICAL else StatusLevel.NORMAL
        add(PumpInfoRow(label = rh.gs(RileyLinkR.string.rileylink_status), value = rlStatusText, level = rlLevel))

        // RileyLink battery (conditional)
        if (rileyLinkServiceData.showBatteryLevel) {
            val batteryText = rileyLinkServiceData.batteryLevel?.let { "$it%" } ?: "?"
            add(PumpInfoRow(label = rh.gs(R.string.rl_battery_label), value = batteryText))
        }

        // Pump status
        val pumpStatusText = buildPumpStatusText()
        add(PumpInfoRow(label = rh.gs(RileyLinkR.string.medtronic_pump_status), value = pumpStatusText))

        // Last connection
        val (lastConnText, lastConnLevel) = buildLastConnection()
        add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.last_connection_label), value = lastConnText, level = lastConnLevel))

        // Last bolus
        add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.last_bolus_label), value = buildLastBolus()))

        // Base basal rate
        val basalText = "(" + medtronicPumpStatus.activeProfileName + ")  " +
            rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, medtronicPumpPlugin.baseBasalRate.cU)
        add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.base_basal_rate_label), value = basalText))

        // Temp basal
        val tbrText = buildTempBasal()
        add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.tempbasal_label), value = tbrText, visible = tbrText.isNotEmpty()))

        // Battery
        val (batteryText, batteryLevel) = buildBattery()
        add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.battery_label), value = batteryText, level = batteryLevel))

        // Reservoir
        val (reservoirText, reservoirLevel) = buildReservoir()
        add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.reservoir_label), value = reservoirText, level = reservoirLevel))

        // Errors
        val errorsText = medtronicPumpStatus.errorInfo
        val errorsLevel = if (errorsText != PLACEHOLDER) StatusLevel.CRITICAL else StatusLevel.NORMAL
        add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.errors), value = errorsText, level = errorsLevel))
    }

    private fun buildPumpStatusText(): String {
        return when (medtronicPumpStatus.pumpDeviceState) {
            app.aaps.core.interfaces.pump.defs.PumpDeviceState.Sleeping             ->
                rh.gs(medtronicPumpStatus.pumpDeviceState.stringResource())

            app.aaps.core.interfaces.pump.defs.PumpDeviceState.NeverContacted,
            app.aaps.core.interfaces.pump.defs.PumpDeviceState.WakingUp,
            app.aaps.core.interfaces.pump.defs.PumpDeviceState.PumpUnreachable,
            app.aaps.core.interfaces.pump.defs.PumpDeviceState.ErrorWhenCommunicating,
            app.aaps.core.interfaces.pump.defs.PumpDeviceState.TimeoutWhenCommunicating,
            app.aaps.core.interfaces.pump.defs.PumpDeviceState.InvalidConfiguration ->
                rh.gs(medtronicPumpStatus.pumpDeviceState.stringResource())

            app.aaps.core.interfaces.pump.defs.PumpDeviceState.Active               -> {
                val cmd = medtronicUtil.getCurrentCommand()
                if (cmd == null) {
                    rh.gs(medtronicPumpStatus.pumpDeviceState.stringResource())
                } else {
                    val cmdResourceId = cmd.resourceId
                    if (cmd == MedtronicCommandType.GetHistoryData) {
                        medtronicUtil.frameNumber?.let {
                            rh.gs(cmdResourceId!!, medtronicUtil.pageNumber, medtronicUtil.frameNumber)
                        } ?: rh.gs(R.string.medtronic_cmd_desc_get_history_request, medtronicUtil.pageNumber)
                    } else {
                        cmdResourceId?.let { rh.gs(it) } ?: cmd.commandDescription
                    }
                }
            }
        }
    }

    private fun buildLastConnection(): Pair<String, StatusLevel> {
        val lastConnection = medtronicPumpStatus.lastConnection
        if (lastConnection == 0L) return PLACEHOLDER to StatusLevel.NORMAL

        val min = (System.currentTimeMillis() - lastConnection) / 1000 / 60
        return when {
            lastConnection + 60 * 1000 > System.currentTimeMillis()      ->
                rh.gs(R.string.medtronic_pump_connected_now) to StatusLevel.NORMAL

            lastConnection + 30 * 60 * 1000 < System.currentTimeMillis() -> {
                val text = when {
                    min < 60   -> rh.gs(app.aaps.core.interfaces.R.string.minago, min)

                    min < 1440 -> {
                        val h = (min / 60).toInt()
                        rh.gq(RileyLinkR.plurals.duration_hours, h, h) + " " + rh.gs(R.string.ago)
                    }

                    else       -> {
                        val d = (min / 60 / 24).toInt()
                        rh.gq(RileyLinkR.plurals.duration_days, d, d) + " " + rh.gs(R.string.ago)
                    }
                }
                text to StatusLevel.WARNING
            }

            else                                                         ->
                dateUtil.minAgo(rh, lastConnection) to StatusLevel.NORMAL
        }
    }

    private fun buildLastBolus(): String {
        val bolus = medtronicPumpStatus.lastBolusAmount
        val bolusTime = medtronicPumpStatus.lastBolusTime
        if (bolus == null || bolusTime == null) return ""

        val agoMsc = System.currentTimeMillis() - bolusTime.time
        val bolusMinAgo = agoMsc.toDouble() / 60.0 / 1000.0
        val unit = rh.gs(app.aaps.core.ui.R.string.insulin_unit_shortname)
        val ago = when {
            agoMsc < 60 * 1000 -> rh.gs(R.string.medtronic_pump_connected_now)
            bolusMinAgo < 60   -> dateUtil.minAgo(rh, bolusTime.time)
            else               -> dateUtil.hourAgo(bolusTime.time, rh)
        }
        return rh.gs(R.string.mdt_last_bolus, bolus, unit, ago)
    }

    private fun buildTempBasal(): String {
        val tbrRemainingTime = medtronicPumpStatus.tbrRemainingTime ?: return ""
        return rh.gs(R.string.mdt_tbr_remaining, medtronicPumpStatus.tempBasalAmount, tbrRemainingTime)
    }

    private fun buildBattery(): Pair<String, StatusLevel> {
        val remaining = medtronicPumpStatus.batteryRemaining
        val text = if (medtronicPumpStatus.batteryType == BatteryType.None || medtronicPumpStatus.batteryVoltage == null) {
            remaining?.let { "$it%" } ?: rh.gs(app.aaps.core.ui.R.string.unknown)
        } else {
            (remaining?.let { "$it%  " } ?: "") +
                String.format(Locale.getDefault(), "(%.2f V)", medtronicPumpStatus.batteryVoltage)
        }
        val level = when {
            remaining == null -> StatusLevel.NORMAL
            remaining <= 10   -> StatusLevel.CRITICAL
            remaining <= 25   -> StatusLevel.WARNING
            else              -> StatusLevel.NORMAL
        }
        return text to level
    }

    private fun buildReservoir(): Pair<String, StatusLevel> {
        val remaining = medtronicPumpStatus.reservoirRemainingUnits
        val full = medtronicPumpStatus.reservoirFullUnits
        val text = rh.gs(app.aaps.core.ui.R.string.reservoir_value, remaining, full)
        val level = when {
            remaining <= 20.0 -> StatusLevel.CRITICAL
            remaining <= 50.0 -> StatusLevel.WARNING
            else              -> StatusLevel.NORMAL
        }
        return text to level
    }

    // endregion

    // region Actions

    private fun buildPrimaryActions(): List<PumpAction> {
        return listOf(
            PumpAction(
                label = rh.gs(app.aaps.core.ui.R.string.refresh),
                icon = Icons.Filled.Refresh,
                onClick = { onRefreshClicked() }
            )
        )
    }

    private fun buildManagementActions(): List<PumpAction> {
        val isConfigured = medtronicPumpPlugin.rileyLinkService?.verifyConfiguration() == true

        return listOf(
            PumpAction(
                label = rh.gs(RileyLinkR.string.rileylink_pair),
                icon = Icons.Filled.Bluetooth,
                category = ActionCategory.MANAGEMENT,
                onClick = { _events.tryEmit(MedtronicOverviewEvent.ShowRileyLinkPairWizard) }
            ),
            PumpAction(
                label = rh.gs(app.aaps.core.ui.R.string.pump_history),
                icon = Icons.Filled.History,
                category = ActionCategory.MANAGEMENT,
                onClick = { _events.tryEmit(MedtronicOverviewEvent.ShowHistory) }
            ),
            PumpAction(
                label = rh.gs(R.string.riley_statistics),
                icon = Icons.Filled.Timeline,
                category = ActionCategory.MANAGEMENT,
                onClick = {
                    if (isConfigured) {
                        _events.tryEmit(MedtronicOverviewEvent.ShowRileyLinkStats)
                    } else {
                        emitNotConfiguredDialog()
                    }
                }
            ),
            PumpAction(
                label = rh.gs(R.string.medtronic_custom_action_wake_and_tune),
                icon = Icons.Filled.SettingsInputAntenna,
                category = ActionCategory.MANAGEMENT,
                onClick = {
                    if (isConfigured) {
                        serviceTaskExecutor.startTask(wakeAndTuneTaskProvider.get())
                        _events.tryEmit(MedtronicOverviewEvent.ShowSnackbar(rh.gs(R.string.medtronic_custom_action_wake_and_tune)))
                    } else {
                        emitNotConfiguredDialog()
                    }
                }
            ),
            PumpAction(
                label = rh.gs(R.string.medtronic_custom_action_clear_bolus_block),
                icon = Icons.Filled.Block,
                category = ActionCategory.MANAGEMENT,
                visible = medtronicPumpPlugin.isBusyBlockingEnabled(),
                onClick = {
                    medtronicPumpPlugin.clearBusyTimestamps()
                    _events.tryEmit(MedtronicOverviewEvent.ShowSnackbar(rh.gs(R.string.medtronic_custom_action_clear_bolus_block)))
                }
            ),
            PumpAction(
                label = rh.gs(R.string.medtronic_custom_action_reset_rileylink),
                icon = Icons.Filled.RestartAlt,
                category = ActionCategory.MANAGEMENT,
                onClick = {
                    serviceTaskExecutor.startTask(resetRileyLinkConfigurationTaskProvider.get())
                    _events.tryEmit(MedtronicOverviewEvent.ShowSnackbar(rh.gs(RileyLinkR.string.rileylink_config_reset)))
                }
            )
        )
    }

    // endregion

    // region Action handlers

    private fun onRefreshClicked() {
        if (medtronicPumpPlugin.rileyLinkService?.verifyConfiguration() != true) {
            emitNotConfiguredDialog()
            return
        }
        medtronicPumpPlugin.resetStatusState()
        commandQueue.readStatus(rh.gs(R.string.clicked_refresh), object : Callback() {
            override fun run() { /* refresh button re-enabled via EventRefreshButtonState */
            }
        })
    }

    private fun emitNotConfiguredDialog() {
        _events.tryEmit(
            MedtronicOverviewEvent.ShowDialog(
                rh.gs(app.aaps.core.ui.R.string.warning),
                rh.gs(R.string.medtronic_error_operation_not_possible_no_configuration)
            )
        )
    }

    // endregion

}

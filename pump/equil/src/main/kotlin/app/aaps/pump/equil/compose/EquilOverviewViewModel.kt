package app.aaps.pump.equil.compose

import android.text.TextUtils
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.ActionCategory
import app.aaps.core.ui.compose.pump.PumpAction
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.core.ui.compose.pump.PumpCommunicationStatus
import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import app.aaps.core.ui.compose.pump.StatusBanner
import app.aaps.core.ui.compose.pump.tickerFlow
import app.aaps.pump.equil.EquilPumpPlugin
import app.aaps.pump.equil.R
import app.aaps.pump.equil.data.RunMode
import app.aaps.pump.equil.events.EventEquilDataChanged
import app.aaps.pump.equil.events.EventEquilModeChanged
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.command.CmdModelSet
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import javax.inject.Inject

sealed class EquilOverviewEvent {
    data class StartWizard(val workflow: EquilWorkflow) : EquilOverviewEvent()
    data object StartHistory : EquilOverviewEvent()
}

@HiltViewModel
@Stable
class EquilOverviewViewModel @Inject constructor(
    private val rh: ResourceHelper,
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val ch: ConcentrationHelper,
    private val equilPumpPlugin: EquilPumpPlugin,
    private val equilManager: EquilManager,
    private val commandQueue: CommandQueue,
    private val preferences: Preferences,
    private val rxBus: RxBus,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _events = MutableSharedFlow<EquilOverviewEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<EquilOverviewEvent> = _events

    // Loading state for mode toggle
    private val _isModeChanging = MutableStateFlow(false)
    val isModeChanging: StateFlow<Boolean> = _isModeChanging

    private val communicationStatus = PumpCommunicationStatus(rxBus, commandQueue, context, viewModelScope)

    // Trigger re-composition on RxBus events and periodic ticks
    private val _refreshTrigger = MutableStateFlow(0L)

    init {
        rxBus.toFlow(EventEquilDataChanged::class.java)
            .onEach { _refreshTrigger.value = System.currentTimeMillis() }
            .launchIn(viewModelScope)
        rxBus.toFlow(EventEquilModeChanged::class.java)
            .onEach { _refreshTrigger.value = System.currentTimeMillis() }
            .launchIn(viewModelScope)
    }

    val uiState: StateFlow<PumpOverviewUiState> = combine(
        _refreshTrigger,
        communicationStatus.refreshTrigger,
        tickerFlow(T.mins(1).msecs())
    ) { _, _, _ ->
        buildUiState()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), buildUiState())

    private fun buildUiState(): PumpOverviewUiState {
        val devName = equilManager.equilState?.serialNumber
        val isPaired = !TextUtils.isEmpty(devName)

        val infoRows = if (isPaired) buildPairedInfoRows() else emptyList()
        val primaryActions = buildPrimaryActions(isPaired)
        val managementActions = buildManagementActions(isPaired)

        val pumpWarning = if (isPaired) buildStatusBanner() else null
        return PumpOverviewUiState(
            statusBanner = pumpWarning ?: communicationStatus.statusBanner(),
            queueStatus = communicationStatus.queueStatus(),
            infoRows = infoRows,
            primaryActions = primaryActions,
            managementActions = managementActions
        )
    }

    private fun buildStatusBanner(): StatusBanner? {
        if (!equilManager.isActivationCompleted()) {
            return StatusBanner(
                text = rh.gs(R.string.equil_init_insulin_error),
                level = StatusLevel.WARNING
            )
        }
        val runMode = equilManager.equilState?.runMode
        return when (runMode) {
            RunMode.RUN     -> null
            RunMode.SUSPEND -> StatusBanner(
                text = rh.gs(R.string.equil_mode_suspended),
                level = StatusLevel.WARNING
            )

            RunMode.STOP    -> StatusBanner(
                text = rh.gs(R.string.equil_mode_stopped),
                level = StatusLevel.CRITICAL
            )

            else            -> null
        }
    }

    private fun buildPairedInfoRows(): List<PumpInfoRow> = buildList {
        val state = equilManager.equilState ?: return@buildList

        add(PumpInfoRow(label = rh.gs(R.string.equil_serialnumber), value = state.serialNumber ?: "-"))
        add(PumpInfoRow(label = rh.gs(R.string.equil_firmware_version), value = state.firmwareVersion ?: "-"))
        add(
            PumpInfoRow(
                label = rh.gs(R.string.equil_mode),
                value = when {
                    !equilManager.isActivationCompleted() -> rh.gs(R.string.equil_init_insulin_error)
                    state.runMode == RunMode.RUN          -> rh.gs(R.string.equil_mode_running)
                    state.runMode == RunMode.STOP         -> rh.gs(R.string.equil_mode_stopped)
                    state.runMode == RunMode.SUSPEND      -> rh.gs(R.string.equil_mode_suspended)
                    else                                  -> "-"
                },
                level = when {
                    !equilManager.isActivationCompleted() -> StatusLevel.WARNING
                    state.runMode == RunMode.STOP         -> StatusLevel.CRITICAL
                    state.runMode == RunMode.SUSPEND      -> StatusLevel.WARNING
                    else                                  -> StatusLevel.UNSPECIFIED
                }
            )
        )
        add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.last_connection_label), value = dateUtil.dateAndTimeAndSecondsString(state.lastDataTime)))
        add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.battery_label), value = "${state.battery}%"))
        add(PumpInfoRow(label = rh.gs(R.string.equil_insulin_reservoir), value = state.currentInsulin.toString()))
        add(
            PumpInfoRow(
                label = rh.gs(R.string.equil_basal_speed),
                value = ch.basalRateString(equilPumpPlugin.baseBasalRate, isAbsolute = true, decimals = 3)
            )
        )

        // Temp basal
        val tempBasal = state.tempBasal
        val tempBasalText = if (tempBasal != null && equilManager.isTempBasalRunning()) {
            val startTime = tempBasal.startTime
            val duration = tempBasal.duration / 60 / 1000
            val minutesRunning = Duration.ofMillis(System.currentTimeMillis() - startTime).toMinutes()
            rh.gs(R.string.equil_common_overview_temp_basal_value, tempBasal.rate, dateUtil.timeString(startTime), minutesRunning, duration)
        } else "-"
        add(PumpInfoRow(label = rh.gs(R.string.equil_temp_basal_rate), value = tempBasalText))

        // Total delivered
        val totalDelivered = if (state.startInsulin == -1) "-"
        else rh.gs(R.string.equil_unit_u, (state.startInsulin - state.currentInsulin).toString())
        add(PumpInfoRow(label = rh.gs(R.string.equil_total_delivered), value = totalDelivered))

        // Last bolus (bolusRecord.amount is in cU from PumpWithConcentration)
        val lastBolusText = state.bolusRecord?.let {
            ch.insulinAmountAgoString(
                PumpInsulin(it.amount),
                dateUtil.sinceString(it.startTime, rh)
            )
        } ?: "-"
        add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.last_bolus_label), value = lastBolusText))
    }

    private fun buildPrimaryActions(isPaired: Boolean): List<PumpAction> = buildList {
        if (isPaired && equilManager.isActivationCompleted()) {
            val runMode = equilManager.equilState?.runMode
            if (runMode == RunMode.RUN) {
                add(
                    PumpAction(
                    label = rh.gs(R.string.equil_common_overview_button_suspend_delivery),
                    icon = IcEquilSuspendDelivery,
                    category = ActionCategory.PRIMARY,
                    onClick = { toggleMode() }
                ))
            } else if (runMode == RunMode.SUSPEND) {
                add(
                    PumpAction(
                    label = rh.gs(R.string.equil_common_overview_button_resume_delivery),
                    icon = IcEquilResumeDelivery,
                    category = ActionCategory.PRIMARY,
                    onClick = { toggleMode() }
                ))
            }
        }
    }

    private fun buildManagementActions(isPaired: Boolean): List<PumpAction> = buildList {
        if (!isPaired) {
            add(
                PumpAction(
                label = rh.gs(R.string.equil_pair),
                iconRes = app.aaps.core.ui.R.drawable.ic_bluetooth_white_48dp,
                category = ActionCategory.MANAGEMENT,
                onClick = { _events.tryEmit(EquilOverviewEvent.StartWizard(EquilWorkflow.PAIR)) }
            ))
        } else {
            add(
                PumpAction(
                label = rh.gs(R.string.equil_dressing),
                iconRes = app.aaps.core.ui.R.drawable.ic_swap_horiz,
                category = ActionCategory.MANAGEMENT,
                onClick = { _events.tryEmit(EquilOverviewEvent.StartWizard(EquilWorkflow.CHANGE_INSULIN)) }
            ))
            add(
                PumpAction(
                label = rh.gs(app.aaps.core.ui.R.string.history),
                iconRes = app.aaps.core.ui.R.drawable.ic_pump_history,
                category = ActionCategory.MANAGEMENT,
                onClick = { _events.tryEmit(EquilOverviewEvent.StartHistory) }
            ))
            add(
                PumpAction(
                label = rh.gs(R.string.equil_unbind),
                iconRes = app.aaps.core.ui.R.drawable.ic_bluetooth_white_48dp,
                category = ActionCategory.MANAGEMENT,
                onClick = { _events.tryEmit(EquilOverviewEvent.StartWizard(EquilWorkflow.UNPAIR)) }
            ))
        }
    }

    private fun toggleMode() {
        val runMode = equilManager.equilState?.runMode ?: return
        val targetMode = if (runMode == RunMode.RUN) RunMode.SUSPEND else RunMode.RUN
        _isModeChanging.value = true
        commandQueue.customCommand(
            CmdModelSet(targetMode.command, aapsLogger, preferences, equilManager),
            object : Callback() {
                override fun run() {
                    _isModeChanging.value = false
                    aapsLogger.debug(LTag.PUMPCOMM, "toggleMode result: ${result.success}")
                    if (result.success) {
                        equilManager.equilState?.runMode = targetMode
                        _refreshTrigger.value = System.currentTimeMillis()
                    }
                }
            }
        )
    }

    private fun readableDuration(duration: Duration): String {
        val hours = duration.toHours().toInt()
        val minutes = duration.toMinutes().toInt()
        val seconds = duration.seconds
        return when {
            seconds < 10           -> rh.gs(R.string.equil_common_moments_ago)
            seconds < 60           -> rh.gs(R.string.equil_common_less_than_a_minute_ago)
            seconds < 60 * 60      -> rh.gs(R.string.equil_common_time_ago, rh.gq(R.plurals.equil_common_minutes, minutes, minutes))

            seconds < 24 * 60 * 60 -> {
                val minutesLeft = minutes % 60
                if (minutesLeft > 0)
                    rh.gs(R.string.equil_common_time_ago, rh.gs(R.string.equil_common_composite_time, rh.gq(R.plurals.equil_common_hours, hours, hours), rh.gq(R.plurals.equil_common_minutes, minutesLeft, minutesLeft)))
                else rh.gs(R.string.equil_common_time_ago, rh.gq(R.plurals.equil_common_hours, hours, hours))
            }

            else                   -> {
                val days = hours / 24
                val hoursLeft = hours % 24
                if (hoursLeft > 0)
                    rh.gs(R.string.equil_common_time_ago, rh.gs(R.string.equil_common_composite_time, rh.gq(R.plurals.equil_common_days, days, days), rh.gq(R.plurals.equil_common_hours, hoursLeft, hoursLeft)))
                else rh.gs(R.string.equil_common_time_ago, rh.gq(R.plurals.equil_common_days, days, days))
            }
        }
    }

    // viewModelScope cancels all coroutines automatically on onCleared()
}

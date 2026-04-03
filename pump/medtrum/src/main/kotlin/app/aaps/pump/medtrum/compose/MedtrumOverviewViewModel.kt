package app.aaps.pump.medtrum.compose

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.ActionCategory
import app.aaps.core.ui.compose.pump.PumpAction
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.core.ui.compose.pump.PumpOverviewStateBuilder
import app.aaps.core.ui.compose.pump.PumpCommunicationStatus
import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import app.aaps.core.ui.compose.pump.StatusBanner
import app.aaps.core.ui.compose.pump.tickerFlow
import app.aaps.pump.medtrum.MedtrumPlugin
import app.aaps.pump.medtrum.MedtrumPump
import app.aaps.pump.medtrum.R
import app.aaps.pump.medtrum.code.ConnectionState
import app.aaps.pump.medtrum.code.PatchStep
import app.aaps.pump.medtrum.comm.enums.BasalType
import app.aaps.pump.medtrum.comm.enums.MedtrumPumpState
import app.aaps.pump.medtrum.comm.enums.ModelType
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

sealed class MedtrumOverviewEvent {
    data class StartPatchWorkflow(val startStep: PatchStep) : MedtrumOverviewEvent()
    data class ShowDialog(val title: String, val message: String) : MedtrumOverviewEvent()
}

@HiltViewModel
@Stable
class MedtrumOverviewViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val profileFunction: ProfileFunction,
    private val commandQueue: CommandQueue,
    private val rxBus: RxBus,
    private val dateUtil: DateUtil,
    private val medtrumPlugin: MedtrumPlugin,
    val medtrumPump: MedtrumPump,
    private val ch: ConcentrationHelper,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val communicationStatus = PumpCommunicationStatus(rxBus, commandQueue, context, scope)
    private val stateBuilder = PumpOverviewStateBuilder(rh)

    private val _events = MutableSharedFlow<MedtrumOverviewEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<MedtrumOverviewEvent> = _events

    val uiState: StateFlow<PumpOverviewUiState> = combine(
        medtrumPump.connectionStateFlow,
        medtrumPump.pumpStateFlow,
        medtrumPump.lastBasalTypeFlow,
        medtrumPump.lastBasalRateFlow,
        medtrumPump.reservoirFlow,
        medtrumPump.batteryVoltage_BFlow,
        medtrumPump.bolusAmountDeliveredFlow,
        medtrumPump.lastBolusTimeFlow,
        medtrumPump.lastBolusAmountFlow,
        medtrumPump.lastConnectionFlow,
        communicationStatus.refreshTrigger,
        tickerFlow(60_000L)
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val connectionState = values[0] as ConnectionState
        val pumpState = values[1] as MedtrumPumpState
        val basalType = values[2] as BasalType
        val basalRate = values[3] as Double
        val reservoir = values[4] as Double
        val batteryVoltage = values[5] as Double
        val bolusDelivered = values[6] as Double
        val lastBolusTime = values[7] as Long?
        val lastBolusAmount = values[8] as Double?
        val lastConnectionTime = values[9] as Long

        buildUiState(
            connectionState, pumpState, basalType, basalRate, reservoir, batteryVoltage,
            bolusDelivered, lastBolusTime, lastBolusAmount, lastConnectionTime
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), buildInitialState())

    override fun onCleared() {
        super.onCleared()
        scope.cancel()
    }

    fun onClickRefresh() {
        commandQueue.readStatus(rh.gs(R.string.requested_by_user), null)
    }

    fun onClickResetAlarms() {
        commandQueue.clearAlarms(null)
    }

    fun onClickChangePatch() {
        aapsLogger.debug(LTag.PUMP, "ChangePatch clicked!")
        viewModelScope.launch {
            val profile = profileFunction.getProfile()
            if (profile == null) {
                _events.tryEmit(
                    MedtrumOverviewEvent.ShowDialog(
                        title = rh.gs(app.aaps.core.ui.R.string.message),
                        message = rh.gs(R.string.no_profile_selected)
                    )
                )
            } else {
                val nextStep = when {
                    medtrumPump.pumpState > MedtrumPumpState.EJECTED && medtrumPump.pumpState < MedtrumPumpState.STOPPED ->
                        PatchStep.START_DEACTIVATION

                    medtrumPump.pumpState in listOf(MedtrumPumpState.STOPPED, MedtrumPumpState.NONE)                     ->
                        PatchStep.PREPARE_PATCH

                    else                                                                                                 ->
                        PatchStep.RETRY_ACTIVATION
                }
                _events.tryEmit(MedtrumOverviewEvent.StartPatchWorkflow(nextStep))
            }
        }
    }

    private fun buildInitialState(): PumpOverviewUiState {
        return buildUiState(
            connectionState = medtrumPump.connectionState,
            pumpState = medtrumPump.pumpState,
            basalType = medtrumPump.lastBasalType,
            basalRate = medtrumPump.lastBasalRate,
            reservoir = medtrumPump.reservoir,
            batteryVoltage = medtrumPump.batteryVoltage_B,
            bolusDelivered = medtrumPump.bolusAmountDeliveredFlow.value,
            lastBolusTime = medtrumPump.lastBolusTime,
            lastBolusAmount = medtrumPump.lastBolusAmount,
            lastConnectionTime = medtrumPump.lastConnection
        )
    }

    private fun buildUiState(
        connectionState: ConnectionState,
        pumpState: MedtrumPumpState,
        basalType: BasalType,
        basalRate: Double,
        reservoir: Double,
        batteryVoltage: Double,
        bolusDelivered: Double,
        lastBolusTime: Long?,
        lastBolusAmount: Double?,
        lastConnectionTime: Long
    ): PumpOverviewUiState {
        // Status banner: communication status from shared helper, or pump-specific warning
        val statusBanner = buildStatusBanner(pumpState) ?: communicationStatus.statusBanner()
        val queueStatus = communicationStatus.queueStatus()

        // Connection state for action button enablement
        val isDisconnected = connectionState == ConnectionState.DISCONNECTED
        val isPumpActive = pumpState > MedtrumPumpState.EJECTED && pumpState < MedtrumPumpState.STOPPED
        val canRefresh = isDisconnected && isPumpActive

        // Last connection
        val lastConnection = if (lastConnectionTime != 0L) {
            val agoMinutes = (System.currentTimeMillis() - lastConnectionTime) / 1000 / 60
            rh.gs(app.aaps.core.interfaces.R.string.minago, agoMinutes)
        } else ""

        // Last bolus
        val lastBolus = if (lastBolusTime != null && lastBolusAmount != null) {
            val agoHours = (System.currentTimeMillis() - lastBolusTime).toDouble() / 1000.0 / 60.0 / 60.0
            if (agoHours < 6.0) {
                ch.insulinAmountAgoString(
                    PumpInsulin(lastBolusAmount),
                    dateUtil.sinceString(lastBolusTime, rh)
                )
            } else null
        } else null

        // Active bolus
        val activeBolusText = if (!medtrumPump.bolusDone && medtrumPlugin.isInitialized() && bolusDelivered > 0.0) {
            dateUtil.timeString(medtrumPump.bolusStartTime) + " " +
                dateUtil.sinceString(medtrumPump.bolusStartTime, rh) + " " +
                ch.bolusProgressString(PumpInsulin(bolusDelivered), ch.fromPump(PumpInsulin(medtrumPump.bolusAmountToBeDelivered))) +
                " (" + rh.gs(app.aaps.core.ui.R.string.bolus_delivered_CU, bolusDelivered, medtrumPump.bolusAmountToBeDelivered) + ")"
        } else null

        // Battery voltage
        val batteryText = if (batteryVoltage > 0.0) String.format(Locale.getDefault(), "%.2f V", batteryVoltage) else null

        // Reservoir
        val reservoirText = if (reservoir > 0.0) ch.insulinAmountString(PumpInsulin(reservoir)) else null

        // Common rows from builder
        val commonRows = stateBuilder.buildCommonRows(
            lastConnection = lastConnection,
            lastBolus = lastBolus,
            battery = batteryText,
            reservoir = reservoirText,
            serialNumber = medtrumPump.pumpSNFromSP.toString(radix = 16).uppercase()
        )

        // Medtrum-specific rows
        val specificRows = buildList {
            // Pump state
            add(PumpInfoRow(label = rh.gs(R.string.pump_state_label), value = pumpState.toString()))
            // Basal type
            add(PumpInfoRow(label = rh.gs(R.string.basal_type_label), value = basalType.toString()))
            // Basal rate
            add(PumpInfoRow(label = rh.gs(R.string.basal_rate_label), value = ch.basalRateString(PumpRate(basalRate), basalType != BasalType.RELATIVE_TEMP)))
            // Active bolus
            activeBolusText?.let {
                add(PumpInfoRow(label = rh.gs(R.string.active_bolus_label), value = it))
            }
            // Active alarms
            val activeAlarmStrings = medtrumPump.activeAlarms.map { medtrumPump.alarmStateToString(it) }
            val alarmsText = activeAlarmStrings.joinToString("\n")
            if (alarmsText.isNotEmpty()) {
                add(PumpInfoRow(label = rh.gs(R.string.active_alarms_label), value = alarmsText, level = StatusLevel.WARNING))
            }
            // Pump type
            add(PumpInfoRow(label = rh.gs(R.string.pump_type_label), value = ModelType.fromValue(medtrumPump.deviceType).toString()))
            // FW version
            if (medtrumPump.swVersion.isNotEmpty()) {
                add(PumpInfoRow(label = rh.gs(R.string.fw_version_label), value = medtrumPump.swVersion))
            }
            // Patch no
            add(PumpInfoRow(label = rh.gs(R.string.patch_no_label), value = medtrumPump.patchId.toString()))
            // Patch age
            if (medtrumPump.patchStartTime != 0L) {
                val age = System.currentTimeMillis() - medtrumPump.patchStartTime
                val agoString = dateUtil.timeAgoFullString(age, rh)
                val ageString = dateUtil.dateAndTimeString(medtrumPump.patchStartTime) + "\n" + agoString
                add(PumpInfoRow(label = rh.gs(R.string.patch_activation_time_label), value = ageString))
            }
            // Patch expiry
            val expiryText = buildExpiryText()
            if (expiryText.isNotEmpty()) {
                add(PumpInfoRow(label = rh.gs(R.string.patch_expiry_label), value = expiryText))
            }
        }

        // Primary actions
        val primaryActions = listOf(
            PumpAction(
                label = rh.gs(app.aaps.core.ui.R.string.refresh),
                iconRes = app.aaps.core.ui.R.drawable.ic_refresh,
                category = ActionCategory.PRIMARY,
                enabled = canRefresh,
                onClick = { onClickRefresh() }
            ),
            PumpAction(
                label = rh.gs(R.string.reset_alarms_label),
                iconRes = app.aaps.core.ui.R.drawable.ic_loop_resume,
                category = ActionCategory.PRIMARY,
                enabled = pumpState.isSuspendedByPump(),
                visible = pumpState.isSuspendedByPump(),
                onClick = { onClickResetAlarms() }
            )
        )

        // Management actions
        val managementActions = listOf(
            PumpAction(
                label = rh.gs(R.string.change_patch_label),
                iconRes = app.aaps.core.ui.R.drawable.ic_swap_horiz,
                category = ActionCategory.MANAGEMENT,
                onClick = { onClickChangePatch() }
            )
        )

        return PumpOverviewUiState(
            statusBanner = statusBanner,
            queueStatus = queueStatus,
            infoRows = commonRows + specificRows,
            primaryActions = primaryActions,
            managementActions = managementActions
        )
    }

    private fun buildStatusBanner(pumpState: MedtrumPumpState): StatusBanner? {
        return when {
            pumpState >= MedtrumPumpState.OCCLUSION                                     -> StatusBanner(
                text = pumpState.toString(),
                level = StatusLevel.CRITICAL
            )

            pumpState.isSuspendedByPump()                                               -> StatusBanner(
                text = rh.gs(R.string.pump_is_suspended),
                level = StatusLevel.WARNING
            )

            pumpState == MedtrumPumpState.STOPPED || pumpState == MedtrumPumpState.NONE -> StatusBanner(
                text = rh.gs(R.string.patch_not_active),
                level = StatusLevel.WARNING
            )

            else                                                                        -> null
        }
    }

    private fun buildExpiryText(): String {
        return if (medtrumPump.desiredPatchExpiration) {
            if (medtrumPump.patchStartTime == 0L) {
                ""
            } else {
                val expiry = medtrumPump.patchStartTime + T.hours(72).msecs()
                val currentTime = System.currentTimeMillis()
                val timeLeft = expiry - currentTime
                val daysLeft = T.msecs(timeLeft).days()
                val hoursLeft = T.msecs(timeLeft).hours() % 24

                val daysString = if (daysLeft > 0) "$daysLeft ${rh.gs(app.aaps.core.interfaces.R.string.days)} " else ""
                val hoursString = "$hoursLeft ${rh.gs(app.aaps.core.interfaces.R.string.hours)}"

                dateUtil.dateAndTimeString(expiry) + "\n(" + daysString + hoursString + ")"
            }
        } else {
            rh.gs(R.string.expiry_not_enabled)
        }
    }
}

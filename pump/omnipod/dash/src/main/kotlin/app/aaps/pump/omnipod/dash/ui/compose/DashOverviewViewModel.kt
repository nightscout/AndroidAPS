package app.aaps.pump.omnipod.dash.ui.compose

import android.content.Context
import android.os.SystemClock
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.defs.determineCorrectBasalSize
import app.aaps.core.interfaces.pump.defs.determineCorrectBolusSize
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.ActionCategory
import app.aaps.core.ui.compose.pump.PumpAction
import app.aaps.core.ui.compose.pump.PumpCommunicationStatus
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import app.aaps.core.ui.compose.pump.tickerFlow
import app.aaps.pump.omnipod.common.EventOmnipodDashPumpValuesChanged
import app.aaps.pump.omnipod.common.bledriver.pod.definition.ActivationProgress
import app.aaps.pump.omnipod.common.bledriver.pod.definition.AlertType
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodConstants
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager
import app.aaps.pump.omnipod.common.queue.command.CommandHandleTimeChange
import app.aaps.pump.omnipod.common.queue.command.CommandPlayTestBeep
import app.aaps.pump.omnipod.common.queue.command.CommandResumeDelivery
import app.aaps.pump.omnipod.common.queue.command.CommandSilenceAlerts
import app.aaps.pump.omnipod.common.queue.command.CommandSuspendDelivery
import app.aaps.pump.omnipod.common.ui.wizard.compose.ActivationType
import app.aaps.pump.omnipod.common.ui.wizard.compose.OmnipodOverviewEvent
import app.aaps.pump.omnipod.dash.OmnipodDashPumpPlugin
import app.aaps.pump.omnipod.dash.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import app.aaps.pump.omnipod.common.R as CommonR
import app.aaps.core.ui.R as CoreUiR

@Stable
@HiltViewModel
class DashOverviewViewModel @Inject constructor(
    private val rh: ResourceHelper,
    private val podStateManager: OmnipodDashPodStateManager,
    private val omnipodDashPumpPlugin: OmnipodDashPumpPlugin,
    private val commandQueue: CommandQueue,
    private val rxBus: RxBus,
    private val dateUtil: DateUtil,
    private val preferences: Preferences,
    private val profileFunction: ProfileFunction,
    private val notificationManager: NotificationManager,
    private val uiInteraction: UiInteraction,
    private val config: Config,
    private val aapsLogger: AAPSLogger,
    private val ch: ConcentrationHelper,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {

        private const val PLACEHOLDER = "-"
        private const val MAX_TIME_DEVIATION_MINUTES = 10L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val communicationStatus = PumpCommunicationStatus(rxBus, commandQueue, context, scope)

    private val _events = MutableSharedFlow<OmnipodOverviewEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<OmnipodOverviewEvent> = _events

    // Trigger flow from RxBus omnipod events
    private val omnipodRefresh = MutableStateFlow(0L).also { flow ->
        scope.launch {
            rxBus.toFlow(EventOmnipodDashPumpValuesChanged::class.java)
                .collect { flow.value = System.currentTimeMillis() }
        }
    }

    val uiState: StateFlow<PumpOverviewUiState> = combine(
        communicationStatus.refreshTrigger,
        omnipodRefresh,
        tickerFlow(15_000L)
    ) { _, _, _ -> buildUiState() }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = buildUiState()
    )

    private fun buildUiState(): PumpOverviewUiState {
        val infoRows = buildInfoRows()
        val primaryActions = buildPrimaryActions()
        val managementActions = buildManagementActions()

        return PumpOverviewUiState(
            statusBanner = communicationStatus.statusBanner(),
            infoRows = infoRows,
            primaryActions = primaryActions,
            managementActions = managementActions,
            queueStatus = communicationStatus.queueStatus()
        )
    }

    // region Info Rows

    private fun buildInfoRows(): List<PumpInfoRow> = buildList {
        val initialized = podStateManager.activationProgress.isAtLeast(ActivationProgress.SET_UNIQUE_ID)

        // Bluetooth section (Dash-specific)
        add(
            PumpInfoRow(
                label = rh.gs(R.string.omnipod_dash_overview_bluetooth_address),
                value = podStateManager.bluetoothAddress ?: PLACEHOLDER
            )
        )

        val connPct = podStateManager.connectionSuccessRatio() * 100
        val connAttempts = podStateManager.failedConnectionsAfterRetries + podStateManager.successfulConnectionAttemptsAfterRetries
        val connQualityStr = String.format(Locale.getDefault(), "%.2f %%", connPct)
        val connQuality = "${podStateManager.successfulConnectionAttemptsAfterRetries}/$connAttempts :: $connQualityStr"
        val connLevel = when {
            connPct < 70 && podStateManager.successfulConnectionAttemptsAfterRetries > 50 -> StatusLevel.CRITICAL
            connPct < 90 && podStateManager.successfulConnectionAttemptsAfterRetries > 50 -> StatusLevel.WARNING
            else                                                                          -> StatusLevel.NORMAL
        }
        add(
            PumpInfoRow(
                label = rh.gs(R.string.omnipod_dash_overview_bluetooth_connection_quality),
                value = connQuality,
                level = connLevel
            )
        )

        if (config.isEngineeringMode()) {
            add(
                PumpInfoRow(
                    label = rh.gs(R.string.omnipod_dash_overview_delivery_status),
                    value = podStateManager.deliveryStatus?.toString() ?: PLACEHOLDER
                )
            )
        }

        // Pod identity
        if (!initialized) {
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_unique_id), value = PLACEHOLDER))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_lot_number), value = PLACEHOLDER))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_sequence_number), value = PLACEHOLDER))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_firmware_version), value = PLACEHOLDER))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_time_on_pod), value = PLACEHOLDER))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_expiry_date), value = PLACEHOLDER))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_status), value = buildPodStatusText(), level = buildPodStatusLevel()))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_last_connection), value = PLACEHOLDER))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_last_bolus), value = PLACEHOLDER))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_base_basal_rate), value = PLACEHOLDER))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_temp_basal_rate), value = PLACEHOLDER, visible = false))
            add(PumpInfoRow(label = rh.gs(CoreUiR.string.reservoir_label), value = PLACEHOLDER))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_total_delivered), value = PLACEHOLDER))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_active_alerts), value = PLACEHOLDER))
            add(PumpInfoRow(label = rh.gs(CoreUiR.string.errors), value = PLACEHOLDER))
        } else {
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_unique_id), value = podStateManager.uniqueId.toString()))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_lot_number), value = podStateManager.lotNumber.toString()))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_sequence_number), value = podStateManager.podSequenceNumber.toString()))
            add(
                PumpInfoRow(
                    label = rh.gs(CommonR.string.omnipod_common_overview_firmware_version),
                    value = rh.gs(R.string.omnipod_dash_overview_firmware_version_value, podStateManager.firmwareVersion.toString(), podStateManager.bluetoothVersion.toString())
                )
            )

            // Time on pod
            val timeZoneStr = podStateManager.timeZoneId?.let { tzId ->
                podStateManager.timeZoneUpdated?.let { tzUpdated ->
                    val tz = TimeZone.getTimeZone(tzId)
                    val inDST = tz.inDaylightTime(Date(tzUpdated))
                    tz.getDisplayName(inDST, TimeZone.SHORT, Locale.getDefault())
                } ?: PLACEHOLDER
            } ?: PLACEHOLDER

            val timeOnPodValue = podStateManager.time?.let {
                rh.gs(CommonR.string.omnipod_common_time_with_timezone, dateUtil.dateAndTimeString(it.toEpochSecond() * 1000), timeZoneStr)
            } ?: PLACEHOLDER

            val timeDeviationTooBig = podStateManager.timeDrift?.let {
                Duration.ofMinutes(MAX_TIME_DEVIATION_MINUTES).minus(it.abs()).isNegative
            } == true
            val timeLevel = when {
                !podStateManager.sameTimeZone -> StatusLevel.CRITICAL  // magenta → critical
                timeDeviationTooBig           -> StatusLevel.WARNING
                else                          -> StatusLevel.NORMAL
            }
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_time_on_pod), value = timeOnPodValue, level = timeLevel))

            // Pod expiry
            val expiresAt = podStateManager.expiry
            val expiryValue = expiresAt?.let { dateUtil.dateAndTimeString(it.toEpochSecond() * 1000) } ?: PLACEHOLDER
            val expiryLevel = when {
                expiresAt != null && ZonedDateTime.now().isAfter(expiresAt)               -> StatusLevel.CRITICAL
                expiresAt != null && ZonedDateTime.now().isAfter(expiresAt.minusHours(4)) -> StatusLevel.WARNING
                else                                                                      -> StatusLevel.NORMAL
            }
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_expiry_date), value = expiryValue, level = expiryLevel))

            // Pod status
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_status), value = buildPodStatusText(), level = buildPodStatusLevel()))

            // Last connection
            val lastConnValue = if (podStateManager.isUniqueIdSet) {
                readableDuration(Duration.ofMillis(System.currentTimeMillis() - podStateManager.lastUpdatedSystem))
            } else PLACEHOLDER
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_last_connection), value = lastConnValue))

            // Last bolus
            val (lastBolusText, lastBolusLevel) = buildLastBolus()
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_last_bolus), value = lastBolusText, level = lastBolusLevel))

            // Base basal rate
            val basalText = if (podStateManager.basalProgram != null && !podStateManager.isSuspended) {
                rh.gs(
                    app.aaps.core.ui.R.string.pump_base_basal_rate,
                    omnipodDashPumpPlugin.model().determineCorrectBasalSize(podStateManager.basalProgram!!.rateAt(System.currentTimeMillis()))
                )
            } else PLACEHOLDER
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_base_basal_rate), value = basalText))

            // Temp basal
            val tempBasalText = buildTempBasalText()
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_temp_basal_rate), value = tempBasalText, visible = tempBasalText != PLACEHOLDER))

            // Reservoir
            val (reservoirText, reservoirLevel) = buildReservoir()
            add(PumpInfoRow(label = rh.gs(CoreUiR.string.reservoir_label), value = reservoirText, level = reservoirLevel))

            // Total delivered
            val totalDelivered = if (podStateManager.isActivationCompleted && podStateManager.pulsesDelivered != null) {
                rh.gs(CommonR.string.omnipod_common_overview_total_delivered_value, podStateManager.pulsesDelivered!! * PodConstants.POD_PULSE_BOLUS_UNITS)
            } else PLACEHOLDER
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_total_delivered), value = totalDelivered))

            // Active alerts
            val alertsText = podStateManager.activeAlerts?.let {
                it.joinToString("\n") { t -> translatedActiveAlert(t) }
            } ?: PLACEHOLDER
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_active_alerts), value = alertsText))

            // Errors
            val errors = buildList {
                podStateManager.alarmType?.let {
                    add(rh.gs(CommonR.string.omnipod_common_pod_status_pod_fault_description, it.value, it.toString()))
                }
            }
            val errorsText = if (errors.isEmpty()) PLACEHOLDER else errors.joinToString("\n")
            val errorsLevel = if (errors.isEmpty()) StatusLevel.NORMAL else StatusLevel.CRITICAL
            add(PumpInfoRow(label = rh.gs(CoreUiR.string.errors), value = errorsText, level = errorsLevel))
        }
    }

    // endregion

    // region Actions

    private fun buildPrimaryActions(): List<PumpAction> {
        val queueEmpty = isQueueEmpty()

        return listOf(
            PumpAction(
                label = rh.gs(app.aaps.core.ui.R.string.refresh),
                icon = Icons.Filled.Refresh,
                enabled = podStateManager.isUniqueIdSet && queueEmpty,
                onClick = {
                    commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.refresh), object : Callback() {
                        override fun run() {}
                    })
                }
            ),
            PumpAction(
                label = rh.gs(CommonR.string.omnipod_common_overview_button_silence_alerts),
                icon = Icons.Filled.NotificationsOff,
                enabled = queueEmpty,
                visible = podStateManager.isPodRunning && (podStateManager.activeAlerts?.isNotEmpty() == true || commandQueue.isCustomCommandInQueue(CommandSilenceAlerts::class.java)),
                onClick = { commandQueue.customCommand(CommandSilenceAlerts(), DisplayResultDialogCallback(rh.gs(CommonR.string.omnipod_common_error_failed_to_silence_alerts), false)) }
            ),
            PumpAction(
                label = rh.gs(CommonR.string.omnipod_common_overview_button_resume_delivery),
                icon = Icons.Filled.PlayArrow,
                enabled = queueEmpty,
                visible = podStateManager.isPodRunning && (podStateManager.isSuspended || commandQueue.isCustomCommandInQueue(CommandResumeDelivery::class.java)),
                onClick = { commandQueue.customCommand(CommandResumeDelivery(), DisplayResultDialogCallback(rh.gs(CommonR.string.omnipod_common_error_failed_to_resume_delivery), false)) }
            ),
            PumpAction(
                label = rh.gs(CommonR.string.omnipod_common_overview_button_suspend_delivery),
                icon = Icons.Filled.Pause,
                visible = false, // Suspend button always hidden for Dash (same as fragment)
                onClick = { commandQueue.customCommand(CommandSuspendDelivery(), DisplayResultDialogCallback(rh.gs(CommonR.string.omnipod_common_error_failed_to_suspend_delivery), false)) }
            ),
            PumpAction(
                label = rh.gs(CommonR.string.omnipod_common_overview_button_set_time),
                icon = Icons.Filled.Schedule,
                enabled = !podStateManager.isSuspended && queueEmpty,
                visible = podStateManager.isActivationCompleted && !podStateManager.sameTimeZone,
                onClick = { commandQueue.customCommand(CommandHandleTimeChange(true), DisplayResultDialogCallback(rh.gs(CommonR.string.omnipod_common_error_failed_to_set_time), false)) }
            )
        )
    }

    private fun buildManagementActions(): List<PumpAction> {
        isQueueEmpty()

        return listOf(
            PumpAction(
                label = rh.gs(CommonR.string.omnipod_common_pod_management_button_activate_pod),
                icon = Icons.Filled.PlayArrow,
                category = ActionCategory.MANAGEMENT,
                visible = !podStateManager.isActivationCompleted,
                onClick = { onActivatePodClicked() }
            ),
            PumpAction(
                label = rh.gs(CommonR.string.omnipod_common_pod_management_button_deactivate_pod),
                icon = Icons.Filled.Pause,
                category = ActionCategory.MANAGEMENT,
                visible = podStateManager.isActivationCompleted,
                enabled = podStateManager.bluetoothAddress != null || podStateManager.ltk != null,
                onClick = { _events.tryEmit(OmnipodOverviewEvent.StartDeactivation) }
            ),
            PumpAction(
                label = rh.gs(CommonR.string.omnipod_common_pod_management_button_play_test_beep),
                icon = Icons.Filled.VolumeUp,
                category = ActionCategory.MANAGEMENT,
                enabled = podStateManager.activationProgress.isAtLeast(ActivationProgress.PHASE_1_COMPLETED) && !commandQueue.isCustomCommandInQueue(CommandPlayTestBeep::class.java),
                visible = podStateManager.activationProgress.isAtLeast(ActivationProgress.PHASE_1_COMPLETED),
                onClick = { commandQueue.customCommand(CommandPlayTestBeep(), DisplayResultDialogCallback(rh.gs(CommonR.string.omnipod_common_error_failed_to_play_test_beep), false)) }
            ),
            PumpAction(
                label = rh.gs(CommonR.string.omnipod_common_pod_management_button_pod_history),
                icon = Icons.Filled.History,
                category = ActionCategory.MANAGEMENT,
                onClick = { _events.tryEmit(OmnipodOverviewEvent.ShowHistory) }
            ),
            PumpAction(
                label = rh.gs(CommonR.string.omnipod_common_pod_management_button_discard_pod),
                icon = Icons.Filled.Delete,
                category = ActionCategory.MANAGEMENT,
                visible = podStateManager.uniqueId != null && podStateManager.activationProgress.isBefore(ActivationProgress.SET_UNIQUE_ID),
                onClick = { onDiscardPodClicked() }
            )
        )
    }

    // endregion

    // region Action handlers

    private fun onActivatePodClicked() {
        viewModelScope.launch {
            val profile = profileFunction.getProfile()
            if (profile == null) {
                _events.tryEmit(
                    OmnipodOverviewEvent.ShowDialog(
                        rh.gs(CoreUiR.string.warning),
                        rh.gs(CommonR.string.omnipod_common_error_failed_to_set_profile_empty_profile)
                    )
                )
                return@launch
            }

            val type = if (podStateManager.activationProgress.isAtLeast(ActivationProgress.PRIME_COMPLETED)) {
                ActivationType.SHORT
            } else {
                ActivationType.LONG
            }
            _events.tryEmit(OmnipodOverviewEvent.StartActivation(type))
        }
    }

    private fun onDiscardPodClicked() {
        _events.tryEmit(
            OmnipodOverviewEvent.ShowDialog(
                rh.gs(CommonR.string.omnipod_common_pod_management_button_discard_pod),
                rh.gs(CommonR.string.omnipod_common_pod_management_discard_pod_confirmation)
            )
        )
    }

    fun confirmDiscardPod() {
        podStateManager.reset()
    }

    // endregion

    // region Helpers

    private fun buildPodStatusText(): String {
        return if (podStateManager.activationProgress == ActivationProgress.NOT_STARTED) {
            rh.gs(CommonR.string.omnipod_common_pod_status_no_active_pod)
        } else if (!podStateManager.isActivationCompleted) {
            if (!podStateManager.isUniqueIdSet) {
                rh.gs(CommonR.string.omnipod_common_pod_status_waiting_for_activation)
            } else {
                if (podStateManager.activationProgress.isBefore(ActivationProgress.PRIME_COMPLETED)) {
                    rh.gs(CommonR.string.omnipod_common_pod_status_waiting_for_activation)
                } else {
                    rh.gs(CommonR.string.omnipod_common_pod_status_waiting_for_cannula_insertion)
                }
            }
        } else {
            if (podStateManager.podStatus!!.isRunning()) {
                if (podStateManager.isSuspended) {
                    rh.gs(CommonR.string.omnipod_common_pod_status_suspended)
                } else {
                    rh.gs(CommonR.string.omnipod_common_pod_status_running)
                }
            } else {
                podStateManager.podStatus.toString()
            }
        }
    }

    private fun buildPodStatusLevel(): StatusLevel = when {
        !podStateManager.isActivationCompleted || podStateManager.isPodKaput || podStateManager.isSuspended -> StatusLevel.CRITICAL
        podStateManager.activeCommand != null                                                               -> StatusLevel.WARNING
        else                                                                                                -> StatusLevel.NORMAL
    }

    private fun buildLastBolus(): Pair<String, StatusLevel> {
        podStateManager.activeCommand?.let {
            val requestedBolus = it.requestedBolus
            if (requestedBolus != null) {
                var text = ch.insulinAmountAgoString(
                    PumpInsulin(omnipodDashPumpPlugin.model().determineCorrectBolusSize(requestedBolus)),
                    readableDuration(Duration.ofMillis(SystemClock.elapsedRealtime() - it.createdRealtime))
                )
                text += " (${rh.gs(CommonR.string.omnipod_common_uncertain)})"
                return text to StatusLevel.CRITICAL
            }
        }

        podStateManager.lastBolus?.let {
            val bolusSize = it.deliveredUnits() ?: it.requestedUnits
            val text = ch.insulinAmountAgoString(
                PumpInsulin(omnipodDashPumpPlugin.model().determineCorrectBolusSize(bolusSize)),
                readableDuration(Duration.ofMillis(System.currentTimeMillis() - it.startTime))
            )
            val level = if (!it.deliveryComplete) StatusLevel.WARNING else StatusLevel.NORMAL
            return text to level
        }

        return PLACEHOLDER to StatusLevel.NORMAL
    }

    private fun buildTempBasalText(): String {
        val tempBasal = podStateManager.tempBasal
        if (podStateManager.isActivationCompleted && podStateManager.tempBasalActive && tempBasal != null) {
            val minutesRunning = Duration.ofMillis(System.currentTimeMillis() - tempBasal.startTime).toMinutes()
            return rh.gs(
                CommonR.string.omnipod_common_overview_temp_basal_concentration_value,
                ch.basalRateString(PumpRate(tempBasal.rate), true),
                dateUtil.timeString(tempBasal.startTime),
                minutesRunning,
                tempBasal.durationInMinutes
            )
        }
        return PLACEHOLDER
    }

    private fun buildReservoir(): Pair<String, StatusLevel> {
        if (podStateManager.pulsesRemaining == null) {
            return rh.gs(CommonR.string.omnipod_common_overview_reservoir_concentration_value_over50, ch.insulinAmountString(PumpInsulin(50.0))) to StatusLevel.NORMAL
        }
        val lowThreshold: Short = PodConstants.DEFAULT_MAX_RESERVOIR_ALERT_THRESHOLD
        val text = ch.insulinAmountString(PumpInsulin(podStateManager.pulsesRemaining!! * PodConstants.POD_PULSE_BOLUS_UNITS))
        val level = if (ch.fromPump(PumpInsulin(podStateManager.pulsesRemaining!! * PodConstants.POD_PULSE_BOLUS_UNITS)) < lowThreshold.toDouble()) StatusLevel.CRITICAL else StatusLevel.NORMAL
        return text to level
    }

    private fun translatedActiveAlert(alert: AlertType): String {
        val id = when (alert) {
            AlertType.LOW_RESERVOIR       -> CommonR.string.omnipod_common_alert_low_reservoir
            AlertType.EXPIRATION          -> CommonR.string.omnipod_common_alert_expiration_advisory
            AlertType.EXPIRATION_IMMINENT -> CommonR.string.omnipod_common_alert_expiration
            AlertType.USER_SET_EXPIRATION -> CommonR.string.omnipod_common_alert_expiration_advisory
            AlertType.AUTO_OFF            -> CommonR.string.omnipod_common_alert_shutdown_imminent
            AlertType.SUSPEND_IN_PROGRESS -> R.string.omnipod_common_alert_delivery_suspended
            AlertType.SUSPEND_ENDED       -> R.string.omnipod_common_alert_delivery_suspended
            else                          -> CommonR.string.omnipod_common_alert_unknown_alert
        }
        return rh.gs(id)
    }

    private fun readableDuration(duration: Duration): String {
        val hours = duration.toHours().toInt()
        val minutes = duration.toMinutes().toInt()
        val seconds = duration.seconds
        return when {
            seconds < 10           -> rh.gs(CommonR.string.omnipod_common_moments_ago)
            seconds < 60           -> rh.gs(CommonR.string.omnipod_common_less_than_a_minute_ago)
            seconds < 60 * 60      -> rh.gs(CommonR.string.omnipod_common_time_ago, rh.gq(CommonR.plurals.omnipod_common_minutes, minutes, minutes))

            seconds < 24 * 60 * 60 -> {
                val minutesLeft = minutes % 60
                if (minutesLeft > 0)
                    rh.gs(CommonR.string.omnipod_common_time_ago, rh.gs(CommonR.string.omnipod_common_composite_time, rh.gq(CommonR.plurals.omnipod_common_hours, hours, hours), rh.gq(CommonR.plurals.omnipod_common_minutes, minutesLeft, minutesLeft)))
                else
                    rh.gs(CommonR.string.omnipod_common_time_ago, rh.gq(CommonR.plurals.omnipod_common_hours, hours, hours))
            }

            else                   -> {
                val days = hours / 24
                val hoursLeft = hours % 24
                if (hoursLeft > 0)
                    rh.gs(CommonR.string.omnipod_common_time_ago, rh.gs(CommonR.string.omnipod_common_composite_time, rh.gq(CommonR.plurals.omnipod_common_days, days, days), rh.gq(CommonR.plurals.omnipod_common_hours, hoursLeft, hoursLeft)))
                else
                    rh.gs(CommonR.string.omnipod_common_time_ago, rh.gq(CommonR.plurals.omnipod_common_days, days, days))
            }
        }
    }

    private fun isQueueEmpty(): Boolean = commandQueue.size() == 0 && commandQueue.performing() == null

    // endregion

    inner class DisplayResultDialogCallback(
        private val errorMessagePrefix: String,
        private val withSoundOnError: Boolean
    ) : Callback() {

        override fun run() {
            if (result.success) {
                // Success — no dialog needed, UI will refresh via events
            } else {
                _events.tryEmit(
                    OmnipodOverviewEvent.ShowErrorDialog(
                        rh.gs(CoreUiR.string.warning),
                        rh.gs(CommonR.string.omnipod_common_two_strings_concatenated_by_colon, errorMessagePrefix, result.comment)
                    )
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        scope.cancel()
    }
}

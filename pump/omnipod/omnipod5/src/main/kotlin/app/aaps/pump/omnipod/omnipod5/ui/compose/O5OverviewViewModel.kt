package app.aaps.pump.omnipod.omnipod5.ui.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.defs.determineCorrectBasalSize
import app.aaps.core.interfaces.pump.defs.determineCorrectBolusSize
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.queue.CustomCommand
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
import app.aaps.pump.omnipod.common.bledriver.pod.definition.ActivationProgress
import app.aaps.pump.omnipod.common.bledriver.pod.definition.AlertType
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodConstants
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.O5PodStateManager
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.expiry
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.time
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.timeDrift
import app.aaps.pump.omnipod.common.queue.command.CommandDeactivatePod
import app.aaps.pump.omnipod.common.queue.command.CommandHandleTimeChange
import app.aaps.pump.omnipod.common.queue.command.CommandPlayTestBeep
import app.aaps.pump.omnipod.common.queue.command.CommandResumeDelivery
import app.aaps.pump.omnipod.common.queue.command.CommandSilenceAlerts
import app.aaps.pump.omnipod.common.ui.wizard.compose.ActivationType
import app.aaps.pump.omnipod.common.ui.wizard.compose.OmnipodOverviewEvent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
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
import java.time.Duration
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import app.aaps.core.ui.R as CoreUiR
import app.aaps.pump.omnipod.common.R as CommonR

/**
 * O5's pod-status display screen, mirroring
 * `DashOverviewViewModel`'s structure. O5's
 * [O5PodStateManager] is flatter than Dash's state manager (no computed derived
 * properties, no `activeCommand`/`lastBolus`/`tempBasal` value objects, no per-retry
 * connection-quality counters, no timezone-drift tracking) - those are approximated here
 * from O5's own flat fields ([O5PodStateManager.pendingDoseCommand] in place of Dash's
 * `activeCommand`, the [expiry] extension in place of Dash's `expiry` property, etc.)
 * rather than adding matching complexity to the state manager.
 */
@Stable
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class O5OverviewViewModel @Inject constructor(
    private val rh: ResourceHelper,
    private val podStateManager: O5PodStateManager,
    private val commandQueue: CommandQueue,
    private val rxBus: RxBus,
    private val dateUtil: DateUtil,
    private val config: Config,
    private val ch: ConcentrationHelper
) : ViewModel() {

    companion object {

        private const val PLACEHOLDER = "-"

        /** Pod-clock drift above this is shown as a warning, same threshold as Dash. */
        private const val MAX_TIME_DEVIATION_MINUTES = 10L
        private const val POD_GRACE_PERIOD_HOURS = 8L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val communicationStatus = PumpCommunicationStatus(rxBus, commandQueue, rh, scope)

    private val _events = MutableSharedFlow<OmnipodOverviewEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<OmnipodOverviewEvent> = _events

    val uiState: StateFlow<PumpOverviewUiState> = combine(
        communicationStatus.refreshTrigger,
        tickerFlow(15_000L)
    ) { _, _ -> buildUiState() }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = buildUiState()
    )

    private fun buildUiState(): PumpOverviewUiState =
        PumpOverviewUiState(
            statusBanner = communicationStatus.statusBanner(),
            infoRows = buildInfoRows(),
            primaryActions = buildPrimaryActions(),
            managementActions = buildManagementActions(),
            queueStatus = communicationStatus.queueStatus()
        )

    private fun buildInfoRows(): List<PumpInfoRow> = buildList {
        val activated = podStateManager.activationProgress.isAtLeast(ActivationProgress.SET_UNIQUE_ID)

        add(
            PumpInfoRow(
                label = rh.gs(CommonR.string.omnipod_common_overview_bluetooth_address),
                value = podStateManager.bluetoothAddress ?: PLACEHOLDER
            )
        )
        val attempts = podStateManager.connectionAttempts
        val connQuality = if (attempts > 0) {
            val pct = podStateManager.successfulConnections.toDouble() / attempts * 100
            "${podStateManager.successfulConnections}/$attempts :: ${String.format(Locale.getDefault(), "%.2f %%", pct)}"
        } else PLACEHOLDER
        add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_bluetooth_connection_quality), value = connQuality))

        if (config.isEngineeringMode()) {
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_delivery_status), value = podStateManager.deliveryStatus?.toString() ?: PLACEHOLDER))
        }

        if (!activated) {
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_unique_id), value = PLACEHOLDER))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_lot_number), value = PLACEHOLDER))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_sequence_number), value = PLACEHOLDER))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_firmware_version), value = PLACEHOLDER))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_time_on_pod), value = PLACEHOLDER))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_expiry_date), value = PLACEHOLDER))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_hard_end_date), value = PLACEHOLDER))
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
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_unique_id), value = podStateManager.podId.toString()))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_lot_number), value = podStateManager.lotNumber.toString()))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_sequence_number), value = podStateManager.podSequenceNumber.toString()))
            add(
                PumpInfoRow(
                    label = rh.gs(CommonR.string.omnipod_common_overview_firmware_version),
                    value = rh.gs(CommonR.string.omnipod_common_overview_firmware_version_value, podStateManager.firmwareVersion.toString(), podStateManager.bleVersion.toString())
                )
            )

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
                !podStateManager.sameTimeZone -> StatusLevel.CRITICAL
                timeDeviationTooBig           -> StatusLevel.WARNING
                else                          -> StatusLevel.NORMAL
            }
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_time_on_pod), value = timeOnPodValue, level = timeLevel))

            podStateManager.podActivatedAt?.let {
                add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_activated_at), value = dateUtil.dateAndTimeString(it)))
            }

            val expiresAt = podStateManager.expiry
            val expiryValue = expiresAt?.let { dateUtil.dateAndTimeString(it.toEpochSecond() * 1000) } ?: PLACEHOLDER
            val expiryLevel = when {
                expiresAt != null && ZonedDateTime.now().isAfter(expiresAt)               -> StatusLevel.CRITICAL
                expiresAt != null && ZonedDateTime.now().isAfter(expiresAt.minusHours(4)) -> StatusLevel.WARNING
                else                                                                      -> StatusLevel.NORMAL
            }
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_expiry_date), value = expiryValue, level = expiryLevel))

            val hardEndAt = expiresAt?.plusHours(POD_GRACE_PERIOD_HOURS)
            val hardEndValue = hardEndAt?.let { dateUtil.dateAndTimeString(it.toEpochSecond() * 1000) } ?: PLACEHOLDER
            val hardEndLevel = when {
                hardEndAt != null && ZonedDateTime.now().isAfter(hardEndAt)               -> StatusLevel.CRITICAL
                hardEndAt != null && ZonedDateTime.now().isAfter(hardEndAt.minusHours(4)) -> StatusLevel.WARNING
                else                                                                      -> StatusLevel.NORMAL
            }
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_hard_end_date), value = hardEndValue, level = hardEndLevel))

            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_status), value = buildPodStatusText(), level = buildPodStatusLevel()))

            val lastConnValue = podStateManager.lastStatusResponseReceived?.let {
                readableDuration(Duration.ofMillis(System.currentTimeMillis() - it))
            } ?: PLACEHOLDER
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_last_connection), value = lastConnValue))

            val (lastBolusText, lastBolusLevel) = buildLastBolus()
            lastBolusText?.let { add(PumpInfoRow(label = rh.gs(CoreUiR.string.last_bolus_label), value = it, level = lastBolusLevel)) }

            val basalText = if (podStateManager.basalProgram != null && !podStateManager.deliverySuspended) {
                ch.basalRateString(
                    rate = PumpRate(PumpType.OMNIPOD_5.determineCorrectBasalSize(podStateManager.basalProgram!!.rateAt(System.currentTimeMillis()))),
                    isAbsolute = true
                )
            } else PLACEHOLDER
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_base_basal_rate), value = basalText))

            val tempBasalText = buildTempBasalText()
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_temp_basal_rate), value = tempBasalText, visible = tempBasalText != PLACEHOLDER))

            val (reservoirText, reservoirLevel) = buildReservoir()
            add(PumpInfoRow(label = rh.gs(CoreUiR.string.reservoir_label), value = reservoirText, level = reservoirLevel))

            val totalDelivered = podStateManager.totalPulsesDelivered?.let {
                ch.insulinAmountString(PumpInsulin(it * PodConstants.POD_PULSE_BOLUS_UNITS))
            } ?: PLACEHOLDER
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_total_delivered), value = totalDelivered))

            val alertsText = podStateManager.activeAlerts?.let { alerts ->
                alerts.joinToString("\n") { t -> translatedActiveAlert(t) + (triggeredAlertTimeSuffix(t) ?: "") }
            } ?: PLACEHOLDER
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_active_alerts), value = alertsText))

            val errors = buildList {
                podStateManager.alarmType?.let { alarm ->
                    add(rh.gs(CommonR.string.omnipod_common_pod_status_pod_fault_description, alarm.code, alarm.toString()))
                    faultTimeText()?.let { add(rh.gs(CommonR.string.omnipod_common_two_strings_concatenated_by_colon, rh.gs(CommonR.string.omnipod_common_pod_fault_time_label), it)) }
                }
            }
            val errorsText = if (errors.isEmpty()) PLACEHOLDER else errors.joinToString("\n")
            add(PumpInfoRow(label = rh.gs(CoreUiR.string.errors), value = errorsText, level = if (errors.isEmpty()) StatusLevel.NORMAL else StatusLevel.CRITICAL))
            podStateManager.alarmType?.pdmFaultCategory?.let { category ->
                podStateManager.pdmRef?.let { ref ->
                    add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_pdm_notification_label), value = category.notificationText(rh, ref)))
                }
            }
        }
    }



    private fun buildPrimaryActions(): List<PumpAction> {
        val queueEmpty = isQueueEmpty()
        val activated = podStateManager.activationProgress == ActivationProgress.COMPLETED
        val podRunning = activated && podStateManager.podStatus?.isRunning() == true

        return listOf(
            PumpAction(
                label = rh.gs(CoreUiR.string.refresh),
                icon = Icons.Filled.Refresh,
                enabled = podStateManager.activationProgress.isAtLeast(ActivationProgress.SET_UNIQUE_ID) && queueEmpty,
                onClick = { viewModelScope.launch { commandQueue.readStatus(rh.gs(CoreUiR.string.refresh)) } }
            ),
            PumpAction(
                label = rh.gs(CommonR.string.omnipod_common_overview_button_silence_alerts),
                icon = Icons.Filled.NotificationsOff,
                enabled = queueEmpty,
                visible = podRunning && (podStateManager.activeAlerts?.isNotEmpty() == true || commandQueue.isCustomCommandInQueue(CommandSilenceAlerts::class)),
                onClick = { runCustomCommandWithErrorDialog(CommandSilenceAlerts(), rh.gs(CommonR.string.omnipod_common_error_failed_to_silence_alerts)) }
            ),
            PumpAction(
                label = rh.gs(CommonR.string.omnipod_common_overview_button_resume_delivery),
                icon = Icons.Filled.PlayArrow,
                enabled = queueEmpty,
                visible = podRunning && (podStateManager.deliverySuspended || commandQueue.isCustomCommandInQueue(CommandResumeDelivery::class)),
                onClick = { runCustomCommandWithErrorDialog(CommandResumeDelivery(), rh.gs(CommonR.string.omnipod_common_error_failed_to_resume_delivery)) }
            ),
            PumpAction(
                label = rh.gs(CommonR.string.omnipod_common_overview_button_set_time),
                icon = Icons.Filled.Schedule,
                enabled = !podStateManager.deliverySuspended && queueEmpty,
                visible = false,
                onClick = { runCustomCommandWithErrorDialog(CommandHandleTimeChange(true), rh.gs(CommonR.string.omnipod_common_error_failed_to_set_time)) }
            )
        )
    }

    private fun buildManagementActions(): List<PumpAction> {
        isQueueEmpty()
        val activated = podStateManager.activationProgress == ActivationProgress.COMPLETED

        return listOf(
            PumpAction(
                label = rh.gs(CommonR.string.omnipod_common_pod_management_button_activate_pod),
                icon = Icons.Filled.PlayArrow,
                category = ActionCategory.MANAGEMENT,
                visible = !activated,
                onClick = { onActivatePodClicked() }
            ),
            PumpAction(
                label = rh.gs(CommonR.string.omnipod_common_pod_management_button_deactivate_pod),
                icon = Icons.Filled.Pause,
                category = ActionCategory.MANAGEMENT,
                visible = activated,
                enabled = podStateManager.bluetoothAddress != null || podStateManager.ltk != null,
                onClick = { _events.tryEmit(OmnipodOverviewEvent.StartDeactivation) }
            ),
            PumpAction(
                label = rh.gs(CommonR.string.omnipod_common_pod_management_button_play_test_beep),
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                category = ActionCategory.MANAGEMENT,
                enabled = podStateManager.activationProgress.isAtLeast(ActivationProgress.PHASE_1_COMPLETED) && !commandQueue.isCustomCommandInQueue(CommandPlayTestBeep::class),
                visible = podStateManager.activationProgress.isAtLeast(ActivationProgress.PHASE_1_COMPLETED),
                onClick = { runCustomCommandWithErrorDialog(CommandPlayTestBeep(), rh.gs(CommonR.string.omnipod_common_error_failed_to_play_test_beep)) }
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
                visible = podStateManager.podId != null && podStateManager.activationProgress.isBefore(ActivationProgress.SET_UNIQUE_ID),
                onClick = { onDiscardPodClicked() }
            )
        )
    }



    private fun onActivatePodClicked() {
        viewModelScope.launch {
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



    private fun buildPodStatusText(): String =
        when {
            podStateManager.activationProgress == ActivationProgress.NOT_STARTED       -> rh.gs(CommonR.string.omnipod_common_pod_status_no_active_pod)
            podStateManager.activationProgress != ActivationProgress.COMPLETED         ->
                if (podStateManager.activationProgress.isBefore(ActivationProgress.SET_UNIQUE_ID)) {
                    rh.gs(CommonR.string.omnipod_common_pod_status_waiting_for_activation)
                } else if (podStateManager.activationProgress.isBefore(ActivationProgress.PRIME_COMPLETED)) {
                    rh.gs(CommonR.string.omnipod_common_pod_status_waiting_for_activation)
                } else {
                    rh.gs(CommonR.string.omnipod_common_pod_status_waiting_for_cannula_insertion)
                }

            podStateManager.podStatus?.isRunning() == true                             ->
                if (podStateManager.deliverySuspended) rh.gs(CommonR.string.omnipod_common_pod_status_suspended)
                else rh.gs(CommonR.string.omnipod_common_pod_status_running)

            else                                                                       -> podStateManager.podStatus.toString()
        }

    private fun buildPodStatusLevel(): StatusLevel = when {
        podStateManager.activationProgress != ActivationProgress.COMPLETED ||
            podStateManager.alarmType != null || podStateManager.deliverySuspended -> StatusLevel.CRITICAL
        podStateManager.pendingDoseCommand != null                                 -> StatusLevel.WARNING
        else                                                                       -> StatusLevel.NORMAL
    }

    private fun buildLastBolus(): Pair<String?, StatusLevel> {
        val pending = podStateManager.pendingDoseCommand
        if (pending != null && pending.type == O5PodStateManager.PendingDoseType.BOLUS) {
            val requested = pending.requestedUnits ?: return null to StatusLevel.NORMAL
            var text = ch.insulinAmountAgoString(PumpInsulin(PumpType.OMNIPOD_5.determineCorrectBolusSize(requested)), pending.startedAt)
            text += " (${rh.gs(CommonR.string.omnipod_common_uncertain)})"
            return text to StatusLevel.CRITICAL
        }
        val startTime = podStateManager.lastBolusStartTime ?: return null to StatusLevel.NORMAL
        val bolusSize = podStateManager.lastBolusDeliveredUnits ?: podStateManager.lastBolusRequestedUnits ?: return null to StatusLevel.NORMAL
        val text = ch.insulinAmountAgoString(PumpInsulin(PumpType.OMNIPOD_5.determineCorrectBolusSize(bolusSize)), startTime)
        val level = if (podStateManager.lastBolusDeliveredUnits == null) StatusLevel.WARNING else StatusLevel.NORMAL
        return text to level
    }

    private fun buildTempBasalText(): String {
        val startTime = podStateManager.activeTempBasalStartTime
        val rate = podStateManager.activeTempBasalRate
        val duration = podStateManager.activeTempBasalDurationMinutes
        if (podStateManager.deliveryStatus?.tempBasalActive() == true && startTime != null && rate != null && duration != null) {
            return ch.basalTbrString(rate = PumpRate(rate), startTime = startTime, durationInMin = duration.toInt())
        }
        return PLACEHOLDER
    }

    private fun buildReservoir(): Pair<String, StatusLevel> {
        val reservoirLevel = podStateManager.reservoirPulsesRemaining?.let { PumpInsulin(it * PodConstants.POD_PULSE_BOLUS_UNITS) }
        return reservoirLevel?.let {
            val lowThreshold = PodConstants.DEFAULT_MAX_RESERVOIR_ALERT_THRESHOLD.toDouble()
            val text = ch.insulinAmountString(it)
            val level = if (ch.fromPump(it) < lowThreshold) StatusLevel.CRITICAL else StatusLevel.NORMAL
            text to level
        } ?: (rh.gs(CoreUiR.string.overview_reservoir_concentration_value_over, ch.insulinAmountString(PumpInsulin(50.0))) to StatusLevel.NORMAL)
    }

    private fun translatedActiveAlert(alert: AlertType): String {
        val id = when (alert) {
            AlertType.LOW_RESERVOIR       -> CommonR.string.omnipod_common_alert_low_reservoir
            AlertType.EXPIRATION          -> CommonR.string.omnipod_common_alert_expiration_advisory
            AlertType.EXPIRATION_IMMINENT -> CommonR.string.omnipod_common_alert_expiration
            AlertType.USER_SET_EXPIRATION -> CommonR.string.omnipod_common_alert_expiration_advisory
            AlertType.AUTO_OFF            -> CommonR.string.omnipod_common_alert_shutdown_imminent
            AlertType.SUSPEND_IN_PROGRESS -> CommonR.string.omnipod_common_alert_delivery_suspended
            AlertType.SUSPEND_ENDED       -> CommonR.string.omnipod_common_alert_delivery_suspended
            else                          -> CommonR.string.omnipod_common_alert_unknown_alert
        }
        return rh.gs(id)
    }

    /** " (triggered <duration> ago)"-style suffix for [alert], if page 1 has reported a
     *  trigger time for it - null otherwise (never triggered, or page 1 not fetched). */
    private fun triggeredAlertTimeSuffix(alert: AlertType): String? {
        val triggered = podStateManager.triggeredAlertTimes?.get(alert) ?: return null
        val minutesSince = podStateManager.minutesSinceActivation ?: return null
        val elapsed = (minutesSince - triggered).coerceAtLeast(0)
        return " (${readableDuration(Duration.ofMinutes(elapsed.toLong()))})"
    }

    /** Elapsed-since-fault text derived from [O5PodStateManager.alarmTime] (a
     *  pod-clock-relative minutes value, same convention as [O5PodStateManager
     *  .minutesSinceActivation]) - null until a status read has populated both. */
    private fun faultTimeText(): String? {
        val alarmTime = podStateManager.alarmTime ?: return null
        val minutesSince = podStateManager.minutesSinceActivation ?: return null
        val elapsed = (minutesSince - alarmTime).coerceAtLeast(0)
        return readableDuration(Duration.ofMinutes(elapsed.toLong()))
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


    private fun runCustomCommandWithErrorDialog(customCommand: CustomCommand, errorMessagePrefix: String) {
        viewModelScope.launch {
            val result = commandQueue.customCommand(customCommand)
            if (!result.success) {
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

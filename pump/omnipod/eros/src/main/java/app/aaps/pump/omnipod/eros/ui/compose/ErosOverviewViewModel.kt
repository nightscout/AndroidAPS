package app.aaps.pump.omnipod.eros.ui.compose

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
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
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.ActionCategory
import app.aaps.core.ui.compose.pump.PumpAction
import app.aaps.core.ui.compose.pump.PumpCommunicationStatus
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import app.aaps.core.ui.compose.pump.tickerFlow
import app.aaps.pump.common.events.EventRileyLinkDeviceStatusChange
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkServiceState
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import app.aaps.pump.common.hw.rileylink.service.tasks.ResetRileyLinkConfigurationTask
import app.aaps.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor
import app.aaps.pump.omnipod.common.queue.command.CommandHandleTimeChange
import app.aaps.pump.omnipod.common.queue.command.CommandPlayTestBeep
import app.aaps.pump.omnipod.common.queue.command.CommandResumeDelivery
import app.aaps.pump.omnipod.common.queue.command.CommandSilenceAlerts
import app.aaps.pump.omnipod.common.queue.command.CommandSuspendDelivery
import app.aaps.pump.omnipod.common.ui.wizard.compose.ActivationType
import app.aaps.pump.omnipod.common.ui.wizard.compose.OmnipodOverviewEvent
import app.aaps.pump.omnipod.eros.OmnipodErosPumpPlugin
import app.aaps.pump.omnipod.eros.R
import app.aaps.pump.omnipod.eros.driver.definition.ActivationProgress
import app.aaps.pump.omnipod.eros.driver.definition.OmnipodConstants
import app.aaps.pump.omnipod.eros.driver.definition.PodProgressStatus
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager
import app.aaps.pump.omnipod.eros.driver.util.TimeUtil
import app.aaps.pump.omnipod.eros.event.EventOmnipodErosPumpValuesChanged
import app.aaps.pump.omnipod.eros.manager.AapsOmnipodErosManager
import app.aaps.pump.omnipod.eros.queue.command.CommandGetPodStatus
import app.aaps.pump.omnipod.eros.util.AapsOmnipodUtil
import app.aaps.pump.omnipod.eros.util.OmnipodAlertUtil
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
import org.joda.time.DateTime
import org.joda.time.Duration
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Provider
import app.aaps.pump.common.hw.rileylink.R as RileyLinkR
import app.aaps.pump.omnipod.common.R as CommonR
import app.aaps.core.ui.R as CoreUiR

@Stable
@HiltViewModel
class ErosOverviewViewModel @Inject constructor(
    private val rh: ResourceHelper,
    private val podStateManager: ErosPodStateManager,
    private val omnipodErosPumpPlugin: OmnipodErosPumpPlugin,
    private val omnipodManager: AapsOmnipodErosManager,
    private val omnipodUtil: AapsOmnipodUtil,
    private val omnipodAlertUtil: OmnipodAlertUtil,
    private val rileyLinkServiceData: RileyLinkServiceData,
    private val serviceTaskExecutor: ServiceTaskExecutor,
    private val commandQueue: CommandQueue,
    private val rxBus: RxBus,
    private val dateUtil: DateUtil,
    private val preferences: Preferences,
    private val profileFunction: ProfileFunction,
    private val notificationManager: NotificationManager,
    private val uiInteraction: UiInteraction,
    private val config: Config,
    private val aapsLogger: AAPSLogger,
    private val resetRileyLinkConfigurationTaskProvider: Provider<ResetRileyLinkConfigurationTask>,
    private val ch: ConcentrationHelper,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {

        private const val PLACEHOLDER = "-"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val communicationStatus = PumpCommunicationStatus(rxBus, commandQueue, context, scope)

    private val _events = MutableSharedFlow<OmnipodOverviewEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<OmnipodOverviewEvent> = _events

    private val omnipodRefresh = MutableStateFlow(0L).also { flow ->
        scope.launch {
            rxBus.toFlow(EventOmnipodErosPumpValuesChanged::class.java)
                .collect { flow.value = System.currentTimeMillis() }
        }
        scope.launch {
            rxBus.toFlow(EventRileyLinkDeviceStatusChange::class.java)
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
        // RileyLink status (Eros-specific)
        val rlState = rileyLinkServiceData.rileyLinkServiceState
        val rlError = rileyLinkServiceData.rileyLinkError
        val rlStatusText = when {
            rlState == RileyLinkServiceState.NotStarted -> rh.gs(rlState.resourceId)
            rlState.isError() && rlError != null        -> rh.gs(rlError.getResourceId(RileyLinkTargetDevice.Omnipod))
            else                                        -> rh.gs(rlState.resourceId)
        }
        val rlLevel = if (rlState.isError() || rlError != null) StatusLevel.CRITICAL else StatusLevel.NORMAL
        add(PumpInfoRow(label = rh.gs(RileyLinkR.string.rileylink_status), value = rlStatusText, level = rlLevel))

        val initialized = podStateManager.hasPodState() && podStateManager.isPodInitialized

        // Pod identity
        if (!initialized) {
            val uniqueIdValue = if (podStateManager.hasPodState()) podStateManager.address.toString() else PLACEHOLDER
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_unique_id), value = uniqueIdValue))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_lot_number), value = PLACEHOLDER))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_sequence_number), value = PLACEHOLDER))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_firmware_version), value = PLACEHOLDER))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_time_on_pod), value = PLACEHOLDER))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_expiry_date), value = PLACEHOLDER))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_status), value = buildPodStatusText(), level = buildPodStatusLevel()))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_last_connection), value = buildLastConnection().first, level = buildLastConnection().second))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_last_bolus), value = PLACEHOLDER))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_base_basal_rate), value = PLACEHOLDER))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_temp_basal_rate), value = PLACEHOLDER, visible = false))
            add(PumpInfoRow(label = rh.gs(CoreUiR.string.reservoir_label), value = PLACEHOLDER))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_total_delivered), value = PLACEHOLDER))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_active_alerts), value = PLACEHOLDER))
        } else {
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_unique_id), value = podStateManager.address.toString()))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_lot_number), value = podStateManager.lot.toString()))
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_sequence_number), value = podStateManager.tid.toString()))
            add(
                PumpInfoRow(
                    label = rh.gs(CommonR.string.omnipod_common_overview_firmware_version),
                    value = rh.gs(R.string.omnipod_eros_overview_firmware_version_value, podStateManager.pmVersion.toString(), podStateManager.piVersion.toString())
                )
            )

            // Time on pod
            val timeOnPodValue = readableZonedTime(podStateManager.time)
            val timeLevel = if (podStateManager.timeDeviatesMoreThan(OmnipodConstants.TIME_DEVIATION_THRESHOLD)) StatusLevel.WARNING else StatusLevel.NORMAL
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_time_on_pod), value = timeOnPodValue, level = timeLevel))

            // Pod expiry
            val expiresAt = podStateManager.expiresAt
            val expiryValue = expiresAt?.let { readableZonedTime(it) } ?: PLACEHOLDER
            val expiryLevel = if (expiresAt != null && DateTime.now().isAfter(expiresAt)) StatusLevel.CRITICAL else StatusLevel.NORMAL
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_expiry_date), value = expiryValue, level = expiryLevel))

            // Pod status
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_status), value = buildPodStatusText(), level = buildPodStatusLevel()))

            // Last connection
            val (lastConnText, lastConnLevel) = buildLastConnection()
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_last_connection), value = lastConnText, level = lastConnLevel))

            // Last bolus
            val (lastBolusText, lastBolusLevel) = buildLastBolus()
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_last_bolus), value = lastBolusText, level = lastBolusLevel))

            // Base basal rate
            val basalText = if (podStateManager.isPodActivationCompleted) {
                rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, omnipodErosPumpPlugin.model().determineCorrectBasalSize(podStateManager.basalSchedule.rateAt(TimeUtil.toDuration(DateTime.now()))))
            } else PLACEHOLDER
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_base_basal_rate), value = basalText))

            // Temp basal
            val (tempBasalText, tempBasalLevel) = buildTempBasal()
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_temp_basal_rate), value = tempBasalText, level = tempBasalLevel, visible = tempBasalText != PLACEHOLDER))

            // Reservoir
            val (reservoirText, reservoirLevel) = buildReservoir()
            add(PumpInfoRow(label = rh.gs(CoreUiR.string.reservoir_label), value = reservoirText, level = reservoirLevel))

            // Total delivered
            val totalDelivered = if (podStateManager.isPodActivationCompleted && podStateManager.totalInsulinDelivered != null) {
                rh.gs(CommonR.string.omnipod_common_overview_total_delivered_value, podStateManager.totalInsulinDelivered - OmnipodConstants.POD_SETUP_UNITS)
            } else PLACEHOLDER
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_total_delivered), value = totalDelivered))

            // Active alerts
            val alertsText = if (podStateManager.hasActiveAlerts()) {
                omnipodUtil.getTranslatedActiveAlerts(podStateManager).joinToString("\n")
            } else PLACEHOLDER
            add(PumpInfoRow(label = rh.gs(CommonR.string.omnipod_common_overview_pod_active_alerts), value = alertsText))
        }

        // Errors
        val errors = buildList {
            omnipodErosPumpPlugin.rileyLinkService?.errorDescription?.takeIf { it.isNotEmpty() }?.let { add(it) }
            if (podStateManager.isPodFaulted) {
                podStateManager.faultEventCode?.let { add(rh.gs(CommonR.string.omnipod_common_pod_status_pod_fault_description, it.value, it.name)) }
            }
        }
        val errorsText = if (errors.isEmpty()) PLACEHOLDER else errors.joinToString("\n")
        val errorsLevel = if (errors.isEmpty()) StatusLevel.NORMAL else StatusLevel.CRITICAL
        add(PumpInfoRow(label = rh.gs(CoreUiR.string.errors), value = errorsText, level = errorsLevel))
    }

    // endregion

    // region Actions

    private fun buildPrimaryActions(): List<PumpAction> {
        val queueEmpty = isQueueEmpty()
        val rlReady = rileyLinkServiceData.rileyLinkServiceState.isReady()

        return listOf(
            PumpAction(
                label = rh.gs(app.aaps.core.ui.R.string.refresh),
                icon = Icons.Filled.Refresh,
                enabled = podStateManager.isPodInitialized && podStateManager.activationProgress.isAtLeast(ActivationProgress.PAIRING_COMPLETED) && rlReady && queueEmpty,
                onClick = { commandQueue.customCommand(CommandGetPodStatus(), DisplayResultDialogCallback(rh.gs(CommonR.string.omnipod_common_error_failed_to_refresh_status), false)) }
            ),
            PumpAction(
                label = rh.gs(CommonR.string.omnipod_common_overview_button_silence_alerts),
                icon = Icons.Filled.NotificationsOff,
                enabled = rlReady && queueEmpty,
                visible = !omnipodManager.isAutomaticallyAcknowledgeAlertsEnabled && podStateManager.isPodRunning && (podStateManager.hasActiveAlerts() || commandQueue.isCustomCommandInQueue(CommandSilenceAlerts::class.java)),
                onClick = { commandQueue.customCommand(CommandSilenceAlerts(), DisplayResultDialogCallback(rh.gs(CommonR.string.omnipod_common_error_failed_to_silence_alerts), false)) }
            ),
            PumpAction(
                label = rh.gs(CommonR.string.omnipod_common_overview_button_resume_delivery),
                icon = Icons.Filled.PlayArrow,
                enabled = rlReady && queueEmpty,
                visible = podStateManager.isPodRunning && (podStateManager.isSuspended || commandQueue.isCustomCommandInQueue(CommandResumeDelivery::class.java)),
                onClick = { commandQueue.customCommand(CommandResumeDelivery(), DisplayResultDialogCallback(rh.gs(CommonR.string.omnipod_common_error_failed_to_resume_delivery), true)) }
            ),
            PumpAction(
                label = rh.gs(CommonR.string.omnipod_common_overview_button_suspend_delivery),
                icon = Icons.Filled.Pause,
                enabled = podStateManager.isPodRunning && !podStateManager.isSuspended && rlReady && queueEmpty,
                visible = omnipodManager.isSuspendDeliveryButtonEnabled && podStateManager.isPodRunning && (!podStateManager.isSuspended || commandQueue.isCustomCommandInQueue(CommandSuspendDelivery::class.java)),
                onClick = { commandQueue.customCommand(CommandSuspendDelivery(), DisplayResultDialogCallback(rh.gs(CommonR.string.omnipod_common_error_failed_to_suspend_delivery), true)) }
            ),
            PumpAction(
                label = rh.gs(CommonR.string.omnipod_common_overview_button_set_time),
                icon = Icons.Filled.Schedule,
                enabled = podStateManager.isPodRunning && !podStateManager.isSuspended && rlReady && queueEmpty,
                visible = podStateManager.isPodRunning && (podStateManager.timeDeviatesMoreThan(Duration.standardMinutes(5)) || commandQueue.isCustomCommandInQueue(CommandHandleTimeChange::class.java)),
                onClick = { commandQueue.customCommand(CommandHandleTimeChange(true), DisplayResultDialogCallback(rh.gs(CommonR.string.omnipod_common_error_failed_to_set_time), true)) }
            )
        )
    }

    private fun buildManagementActions(): List<PumpAction> {
        val rlReady = rileyLinkServiceData.rileyLinkServiceState.isReady()

        return listOf(
            PumpAction(
                label = rh.gs(CommonR.string.omnipod_common_pod_management_button_activate_pod),
                icon = Icons.Filled.PlayArrow,
                category = ActionCategory.MANAGEMENT,
                visible = !podStateManager.isPodActivationCompleted,
                enabled = rlReady,
                onClick = { onActivatePodClicked() }
            ),
            PumpAction(
                label = rh.gs(CommonR.string.omnipod_common_pod_management_button_deactivate_pod),
                icon = Icons.Filled.Pause,
                category = ActionCategory.MANAGEMENT,
                visible = podStateManager.isPodActivationCompleted,
                enabled = podStateManager.activationProgress.isAtLeast(ActivationProgress.PAIRING_COMPLETED) && rlReady,
                onClick = { _events.tryEmit(OmnipodOverviewEvent.StartDeactivation) }
            ),
            PumpAction(
                label = rh.gs(CommonR.string.omnipod_common_pod_management_button_play_test_beep),
                icon = Icons.Filled.VolumeUp,
                category = ActionCategory.MANAGEMENT,
                enabled = rlReady && !commandQueue.isCustomCommandInQueue(CommandPlayTestBeep::class.java),
                visible = podStateManager.isPodInitialized && podStateManager.activationProgress.isAtLeast(ActivationProgress.PAIRING_COMPLETED),
                onClick = { commandQueue.customCommand(CommandPlayTestBeep(), DisplayResultDialogCallback(rh.gs(CommonR.string.omnipod_common_error_failed_to_play_test_beep), false)) }
            ),
            PumpAction(
                label = rh.gs(CommonR.string.omnipod_common_pod_management_button_pod_history),
                icon = Icons.Filled.History,
                category = ActionCategory.MANAGEMENT,
                onClick = { _events.tryEmit(OmnipodOverviewEvent.ShowHistory) }
            ),
            PumpAction(
                label = rh.gs(RileyLinkR.string.rileylink_pair),
                icon = Icons.Filled.Bluetooth,
                category = ActionCategory.MANAGEMENT,
                onClick = { _events.tryEmit(OmnipodOverviewEvent.ShowRileyLinkPairWizard) }
            ),
            PumpAction(
                label = rh.gs(R.string.omnipod_eros_pod_management_button_riley_link_stats),
                icon = Icons.Filled.Bluetooth,
                category = ActionCategory.MANAGEMENT,
                visible = omnipodManager.isRileylinkStatsButtonEnabled,
                onClick = {
                    if (omnipodErosPumpPlugin.rileyLinkService?.verifyConfiguration() == true) {
                        _events.tryEmit(OmnipodOverviewEvent.ShowRileyLinkStats)
                    }
                }
            ),
            PumpAction(
                label = rh.gs(R.string.omnipod_eros_pod_management_button_reset_riley_link_config),
                icon = Icons.Filled.RestartAlt,
                category = ActionCategory.MANAGEMENT,
                onClick = {
                    serviceTaskExecutor.startTask(resetRileyLinkConfigurationTaskProvider.get())
                    _events.tryEmit(OmnipodOverviewEvent.ShowSnackbar(rh.gs(RileyLinkR.string.rileylink_config_reset)))
                }
            ),
            PumpAction(
                label = rh.gs(CommonR.string.omnipod_common_pod_management_button_discard_pod),
                icon = Icons.Filled.Delete,
                category = ActionCategory.MANAGEMENT,
                visible = podStateManager.hasPodState() && (!podStateManager.isPodInitialized || config.isEngineeringMode()),
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

            val type = if (podStateManager.isPodInitialized && podStateManager.activationProgress.isAtLeast(ActivationProgress.PRIMING_COMPLETED)) {
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
        omnipodManager.discardPodState()
    }

    // endregion

    // region Helpers

    private fun buildPodStatusText(): String {
        return if (!podStateManager.hasPodState()) {
            rh.gs(CommonR.string.omnipod_common_pod_status_no_active_pod)
        } else if (!podStateManager.isPodActivationCompleted) {
            if (!podStateManager.isPodInitialized) {
                rh.gs(CommonR.string.omnipod_common_pod_status_waiting_for_activation)
            } else {
                if (podStateManager.activationProgress.isBefore(ActivationProgress.PRIMING_COMPLETED)) {
                    rh.gs(CommonR.string.omnipod_common_pod_status_waiting_for_activation)
                } else {
                    rh.gs(CommonR.string.omnipod_common_pod_status_waiting_for_cannula_insertion)
                }
            }
        } else {
            if (podStateManager.podProgressStatus.isRunning) {
                var status = if (podStateManager.isSuspended) {
                    rh.gs(CommonR.string.omnipod_common_pod_status_suspended)
                } else {
                    rh.gs(CommonR.string.omnipod_common_pod_status_running)
                }
                if (!podStateManager.isBasalCertain) {
                    status += " (${rh.gs(CommonR.string.omnipod_common_uncertain)})"
                }
                status
            } else if (podStateManager.podProgressStatus == PodProgressStatus.FAULT_EVENT_OCCURRED) {
                rh.gs(CommonR.string.omnipod_common_pod_status_pod_fault)
            } else if (podStateManager.podProgressStatus == PodProgressStatus.INACTIVE) {
                rh.gs(CommonR.string.omnipod_common_pod_status_inactive)
            } else {
                podStateManager.podProgressStatus.toString()
            }
        }
    }

    private fun buildPodStatusLevel(): StatusLevel = when {
        !podStateManager.isPodActivationCompleted || podStateManager.isPodDead || podStateManager.isSuspended || (podStateManager.isPodRunning && !podStateManager.isBasalCertain) -> StatusLevel.CRITICAL
        else                                                                                                                                                                       -> StatusLevel.NORMAL
    }

    private fun buildLastConnection(): Pair<String, StatusLevel> {
        if (podStateManager.isPodInitialized && podStateManager.lastSuccessfulCommunication != null) {
            val text = readableDuration(podStateManager.lastSuccessfulCommunication)
            val level = if (omnipodErosPumpPlugin.isUnreachableAlertTimeoutExceeded(getPumpUnreachableTimeout().millis)) StatusLevel.CRITICAL else StatusLevel.NORMAL
            return text to level
        }
        if (podStateManager.hasPodState() && podStateManager.lastSuccessfulCommunication != null) {
            return readableDuration(podStateManager.lastSuccessfulCommunication) to StatusLevel.NORMAL
        }
        return PLACEHOLDER to StatusLevel.NORMAL
    }

    private fun buildLastBolus(): Pair<String, StatusLevel> {
        if (podStateManager.isPodActivationCompleted && podStateManager.hasLastBolus()) {
            var text = ch.insulinAmountAgoString(
                PumpInsulin(omnipodErosPumpPlugin.model().determineCorrectBolusSize(podStateManager.lastBolusAmount)),
                readableDuration(podStateManager.lastBolusStartTime)
            )
            if (!podStateManager.isLastBolusCertain) {
                text += " (${rh.gs(CommonR.string.omnipod_common_uncertain)})"
                return text to StatusLevel.CRITICAL
            }
            return text to StatusLevel.NORMAL
        }
        return PLACEHOLDER to StatusLevel.NORMAL
    }

    private fun buildTempBasal(): Pair<String, StatusLevel> {
        if (podStateManager.isPodActivationCompleted && podStateManager.isTempBasalRunning) {
            if (!podStateManager.hasTempBasal()) {
                return "???" to StatusLevel.CRITICAL
            }
            val now = DateTime.now()
            val minutesRunning = Duration(podStateManager.tempBasalStartTime, now).standardMinutes
            var text = rh.gs(
                CommonR.string.omnipod_common_overview_temp_basal_concentration_value,
                ch.basalRateString(PumpRate(podStateManager.tempBasalAmount), true),
                dateUtil.timeString(podStateManager.tempBasalStartTime.millis),
                minutesRunning,
                podStateManager.tempBasalDuration.standardMinutes
            )
            if (!podStateManager.isTempBasalCertain) {
                text += " (${rh.gs(CommonR.string.omnipod_common_uncertain)})"
                return text to StatusLevel.CRITICAL
            }
            return text to StatusLevel.NORMAL
        }

        // Not running
        if (podStateManager.isPodActivationCompleted && !podStateManager.isTempBasalCertain) {
            return "$PLACEHOLDER (${rh.gs(CommonR.string.omnipod_common_uncertain)})" to StatusLevel.CRITICAL
        }
        return PLACEHOLDER to StatusLevel.NORMAL
    }

    private fun buildReservoir(): Pair<String, StatusLevel> {
        if (podStateManager.reservoirLevel == null) {
            return rh.gs(CommonR.string.omnipod_common_overview_reservoir_concentration_value_over50, ch.insulinAmountString(PumpInsulin(50.0))) to StatusLevel.NORMAL
        }
        val lowThreshold = (omnipodAlertUtil.lowReservoirAlertUnits ?: OmnipodConstants.DEFAULT_MAX_RESERVOIR_ALERT_THRESHOLD).toDouble()
        val text = ch.insulinAmountString(PumpInsulin(podStateManager.reservoirLevel))
        val level = if (ch.fromPump(PumpInsulin(podStateManager.reservoirLevel)) < lowThreshold) StatusLevel.CRITICAL else StatusLevel.NORMAL
        return text to level
    }

    private fun readableZonedTime(time: DateTime): String {
        val timeAsJavaDate = time.toLocalDateTime().toDate()
        val timeZone = podStateManager.timeZone.toTimeZone()
        if (timeZone == TimeZone.getDefault()) {
            return dateUtil.dateAndTimeString(timeAsJavaDate.time)
        }
        val isDaylightTime = timeZone.inDaylightTime(timeAsJavaDate)
        val locale = context.resources.configuration.locales[0]
        val tzDisplayName = timeZone.getDisplayName(isDaylightTime, TimeZone.SHORT, locale) + " " + timeZone.getDisplayName(isDaylightTime, TimeZone.LONG, locale)
        return rh.gs(CommonR.string.omnipod_common_time_with_timezone, dateUtil.dateAndTimeString(timeAsJavaDate.time), tzDisplayName)
    }

    private fun readableDuration(dateTime: DateTime): String {
        val duration = Duration(dateTime, DateTime.now())
        val hours = duration.standardHours.toInt()
        val minutes = duration.standardMinutes.toInt()
        val seconds = duration.standardSeconds.toInt()
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

    private fun getPumpUnreachableTimeout(): Duration =
        Duration.standardMinutes(preferences.get(IntKey.AlertsPumpUnreachableThreshold).toLong())

    // endregion

    inner class DisplayResultDialogCallback(
        private val errorMessagePrefix: String,
        private val withSoundOnError: Boolean
    ) : Callback() {

        override fun run() {
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

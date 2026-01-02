package app.aaps.pump.omnipod.eros.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.defs.determineCorrectBasalSize
import app.aaps.core.interfaces.pump.defs.determineCorrectBolusSize
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventQueueChanged
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.pump.common.events.EventRileyLinkDeviceStatusChange
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkServiceState
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import app.aaps.pump.omnipod.common.databinding.OmnipodCommonOverviewButtonsBinding
import app.aaps.pump.omnipod.common.databinding.OmnipodCommonOverviewPodInfoBinding
import app.aaps.pump.omnipod.common.queue.command.CommandHandleTimeChange
import app.aaps.pump.omnipod.common.queue.command.CommandResumeDelivery
import app.aaps.pump.omnipod.common.queue.command.CommandSilenceAlerts
import app.aaps.pump.omnipod.common.queue.command.CommandSuspendDelivery
import app.aaps.pump.omnipod.eros.OmnipodErosPumpPlugin
import app.aaps.pump.omnipod.eros.R
import app.aaps.pump.omnipod.eros.databinding.OmnipodErosOverviewBinding
import app.aaps.pump.omnipod.eros.databinding.OmnipodErosOverviewRileyLinkStatusBinding
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
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import org.joda.time.Duration
import java.util.TimeZone
import javax.inject.Inject

class OmnipodErosOverviewFragment : DaggerFragment() {
    companion object {

        private const val REFRESH_INTERVAL_MILLIS = 15 * 1000L // 15 seconds
        private const val PLACEHOLDER = "-"
    }

    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var omnipodErosPumpPlugin: OmnipodErosPumpPlugin
    @Inject lateinit var podStateManager: ErosPodStateManager
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var omnipodUtil: AapsOmnipodUtil
    @Inject lateinit var omnipodAlertUtil: OmnipodAlertUtil
    @Inject lateinit var rileyLinkServiceData: RileyLinkServiceData
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var omnipodManager: AapsOmnipodErosManager
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uiInteraction: UiInteraction

    private var disposables: CompositeDisposable = CompositeDisposable()

    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private var refreshLoop: Runnable

    init {
        refreshLoop = Runnable {
            activity?.runOnUiThread { updateUi() }
            handler.postDelayed(refreshLoop, REFRESH_INTERVAL_MILLIS)
        }
    }

    private var _binding: OmnipodErosOverviewBinding? = null
    private var _rileyLinkStatusBinding: OmnipodErosOverviewRileyLinkStatusBinding? = null
    private var _podInfoBinding: OmnipodCommonOverviewPodInfoBinding? = null
    private var _buttonBinding: OmnipodCommonOverviewButtonsBinding? = null

    private val rileyLinkStatusBinding get() = _rileyLinkStatusBinding!!
    private val podInfoBinding get() = _podInfoBinding!!
    private val buttonBinding get() = _buttonBinding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        OmnipodErosOverviewBinding.inflate(inflater, container, false).also {
            _buttonBinding = OmnipodCommonOverviewButtonsBinding.bind(it.root)
            _podInfoBinding = OmnipodCommonOverviewPodInfoBinding.bind(it.root)
            _rileyLinkStatusBinding = OmnipodErosOverviewRileyLinkStatusBinding.bind(it.root)
            _binding = it
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        buttonBinding.buttonPodManagement.setOnClickListener {
            if (omnipodErosPumpPlugin.rileyLinkService?.verifyConfiguration() == true) {
                activity?.let { activity ->
                    context?.let { context ->
                        protectionCheck.queryProtection(
                            activity, ProtectionCheck.Protection.PREFERENCES,
                            app.aaps.core.ui.UIRunnable { startActivity(Intent(context, ErosPodManagementActivity::class.java)) }
                        )
                    }
                }
            } else {
                displayNotConfiguredDialog()
            }
        }

        buttonBinding.buttonResumeDelivery.setOnClickListener {
            disablePodActionButtons()
            commandQueue.customCommand(
                CommandResumeDelivery(),
                DisplayResultDialogCallback(
                    rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_error_failed_to_resume_delivery),
                    true
                ).messageOnSuccess(rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_confirmation_delivery_resumed))
            )
        }

        buttonBinding.buttonRefreshStatus.setOnClickListener {
            disablePodActionButtons()
            commandQueue.customCommand(
                CommandGetPodStatus(),
                DisplayResultDialogCallback(rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_error_failed_to_refresh_status), false)
            )
        }

        buttonBinding.buttonSilenceAlerts.setOnClickListener {
            disablePodActionButtons()
            commandQueue.customCommand(
                CommandSilenceAlerts(),
                DisplayResultDialogCallback(rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_error_failed_to_silence_alerts), false)
                    .messageOnSuccess(rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_confirmation_silenced_alerts))
                    .actionOnSuccess { rxBus.send(EventDismissNotification(Notification.OMNIPOD_POD_ALERTS)) })
        }

        buttonBinding.buttonSuspendDelivery.setOnClickListener {
            disablePodActionButtons()
            commandQueue.customCommand(
                CommandSuspendDelivery(),
                DisplayResultDialogCallback(rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_error_failed_to_suspend_delivery), true)
                    .messageOnSuccess(rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_confirmation_suspended_delivery))
            )
        }

        buttonBinding.buttonSetTime.setOnClickListener {
            disablePodActionButtons()
            commandQueue.customCommand(
                CommandHandleTimeChange(true),
                DisplayResultDialogCallback(rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_error_failed_to_set_time), true)
                    .messageOnSuccess(rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_confirmation_time_on_pod_updated))
            )
        }
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(refreshLoop, REFRESH_INTERVAL_MILLIS)
        disposables += rxBus
            .toObservable(EventRileyLinkDeviceStatusChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           updateRileyLinkStatus()
                           updatePodActionButtons()
                       }, fabricPrivacy::logException)
        disposables += rxBus
            .toObservable(EventOmnipodErosPumpValuesChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           updateOmnipodStatus()
                           updatePodActionButtons()
                       }, fabricPrivacy::logException)
        disposables += rxBus
            .toObservable(EventQueueChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           updateQueueStatus()
                           updatePodActionButtons()
                       }, fabricPrivacy::logException)
        disposables += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           updatePodActionButtons()
                       }, fabricPrivacy::logException)
        updateUi()
    }

    override fun onPause() {
        super.onPause()
        disposables.clear()
        handler.removeCallbacksAndMessages(null)
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        handler.looper.quitSafely()
    }

    private fun updateUi() {
        updateRileyLinkStatus()
        updateOmnipodStatus()
        updatePodActionButtons()
        updateQueueStatus()
    }

    @Synchronized
    private fun updateRileyLinkStatus() {
        val rileyLinkServiceState = rileyLinkServiceData.rileyLinkServiceState

        val resourceId = rileyLinkServiceState.resourceId
        val rileyLinkError = rileyLinkServiceData.rileyLinkError

        rileyLinkStatusBinding.rileyLinkStatus.text =
            when {
                rileyLinkServiceState == RileyLinkServiceState.NotStarted -> rh.gs(resourceId)
                rileyLinkServiceState.isConnecting()                      -> "{fa-bluetooth-b spin}   " + rh.gs(resourceId)
                rileyLinkServiceState.isError() && rileyLinkError == null -> "{fa-bluetooth-b}   " + rh.gs(resourceId)
                rileyLinkServiceState.isError() && rileyLinkError != null -> "{fa-bluetooth-b}   " + rh.gs(rileyLinkError.getResourceId(RileyLinkTargetDevice.Omnipod))
                else                                                      -> "{fa-bluetooth-b}   " + rh.gs(resourceId)
            }
        rileyLinkStatusBinding.rileyLinkStatus.setTextColor(
            rh.gac(
                context,
                if (rileyLinkServiceState.isError() || rileyLinkError != null) app.aaps.core.ui.R.attr.warningColor else app.aaps.core.ui.R.attr.defaultTextColor
            )
        )
    }

    private fun updateOmnipodStatus() {
        updateLastConnection()
        updateLastBolus()
        updateTempBasal()
        updatePodStatus()

        val errors = ArrayList<String>()
        if (omnipodErosPumpPlugin.rileyLinkService != null) {
            val rileyLinkErrorDescription = omnipodErosPumpPlugin.rileyLinkService?.errorDescription
            if (StringUtils.isNotEmpty(rileyLinkErrorDescription)) {
                errors.add(rileyLinkErrorDescription!!)
            }
        }

        if (!podStateManager.hasPodState() || !podStateManager.isPodInitialized) {
            podInfoBinding.uniqueId.text = if (podStateManager.hasPodState()) {
                podStateManager.address.toString()
            } else {
                PLACEHOLDER
            }
            podInfoBinding.podLot.text = PLACEHOLDER
            podInfoBinding.podSequenceNumber.text = PLACEHOLDER
            podInfoBinding.firmwareVersion.text = PLACEHOLDER
            podInfoBinding.timeOnPod.text = PLACEHOLDER
            podInfoBinding.podExpiryDate.text = PLACEHOLDER
            podInfoBinding.podExpiryDate.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor))
            podInfoBinding.baseBasalRate.text = PLACEHOLDER
            podInfoBinding.totalDelivered.text = PLACEHOLDER
            podInfoBinding.reservoir.text = PLACEHOLDER
            podInfoBinding.reservoir.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor))
            podInfoBinding.podActiveAlerts.text = PLACEHOLDER
        } else {
            podInfoBinding.uniqueId.text = podStateManager.address.toString()
            podInfoBinding.podLot.text = podStateManager.lot.toString()
            podInfoBinding.podSequenceNumber.text = podStateManager.tid.toString()
            podInfoBinding.firmwareVersion.text = rh.gs(R.string.omnipod_eros_overview_firmware_version_value, podStateManager.pmVersion.toString(), podStateManager.piVersion.toString())

            podInfoBinding.timeOnPod.text = readableZonedTime(podStateManager.time)
            podInfoBinding.timeOnPod.setTextColor(
                rh.gac(
                    context,
                    if (podStateManager.timeDeviatesMoreThan(OmnipodConstants.TIME_DEVIATION_THRESHOLD)) {
                        app.aaps.core.ui.R.attr.warningColor
                    } else {
                        app.aaps.core.ui.R.attr.defaultTextColor
                    }
                )
            )
            val expiresAt = podStateManager.expiresAt
            if (expiresAt == null) {
                podInfoBinding.podExpiryDate.text = PLACEHOLDER
                podInfoBinding.podExpiryDate.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor))
            } else {
                podInfoBinding.podExpiryDate.text = readableZonedTime(expiresAt)
                podInfoBinding.podExpiryDate.setTextColor(
                    rh.gac(
                        context,
                        if (DateTime.now().isAfter(expiresAt)) {
                            app.aaps.core.ui.R.attr.warningColor
                        } else {
                            app.aaps.core.ui.R.attr.defaultTextColor
                        }
                    )
                )
            }

            if (podStateManager.isPodFaulted) {
                podStateManager.faultEventCode?.let { faultEventCode ->
                    errors.add(rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_status_pod_fault_description, faultEventCode.value, faultEventCode.name))
                }
            }

            // base basal rate
            podInfoBinding.baseBasalRate.text = if (podStateManager.isPodActivationCompleted) {
                rh.gs(
                    app.aaps.core.ui.R.string.pump_base_basal_rate,
                    omnipodErosPumpPlugin.model().determineCorrectBasalSize(podStateManager.basalSchedule.rateAt(TimeUtil.toDuration(DateTime.now())))
                )
            } else {
                PLACEHOLDER
            }

            // total delivered
            podInfoBinding.totalDelivered.text = if (podStateManager.isPodActivationCompleted && podStateManager.totalInsulinDelivered != null) {
                rh.gs(
                    app.aaps.pump.omnipod.common.R.string.omnipod_common_overview_total_delivered_value,
                    podStateManager.totalInsulinDelivered - OmnipodConstants.POD_SETUP_UNITS
                )
            } else {
                PLACEHOLDER
            }

            // reservoir
            if (podStateManager.reservoirLevel == null) {
                podInfoBinding.reservoir.text = rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_overview_reservoir_value_over50)
                podInfoBinding.reservoir.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor))
            } else {
                val lowReservoirThreshold = (omnipodAlertUtil.lowReservoirAlertUnits
                    ?: OmnipodConstants.DEFAULT_MAX_RESERVOIR_ALERT_THRESHOLD).toDouble()

                podInfoBinding.reservoir.text = rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_overview_reservoir_value, podStateManager.reservoirLevel)
                podInfoBinding.reservoir.setTextColor(
                    rh.gac(
                        context,
                        if (podStateManager.reservoirLevel < lowReservoirThreshold) {
                            app.aaps.core.ui.R.attr.warningColor
                        } else {
                            app.aaps.core.ui.R.attr.defaultTextColor
                        }
                    )
                )
            }

            podInfoBinding.podActiveAlerts.text = if (podStateManager.hasActiveAlerts()) {
                TextUtils.join(System.lineSeparator(), omnipodUtil.getTranslatedActiveAlerts(podStateManager))
            } else {
                PLACEHOLDER
            }
        }

        if (errors.isEmpty()) {
            podInfoBinding.errors.text = PLACEHOLDER
            podInfoBinding.errors.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor))
        } else {
            podInfoBinding.errors.text = StringUtils.join(errors, System.lineSeparator())
            podInfoBinding.errors.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.warningColor))
        }
    }

    private fun updateLastConnection() {
        if (podStateManager.isPodInitialized && podStateManager.lastSuccessfulCommunication != null) {
            podInfoBinding.lastConnection.text = readableDuration(podStateManager.lastSuccessfulCommunication)
            val lastConnectionColor =
                rh.gac(
                    context,
                    if (omnipodErosPumpPlugin.isUnreachableAlertTimeoutExceeded(getPumpUnreachableTimeout().millis)) {
                        app.aaps.core.ui.R.attr.warningColor
                    } else {
                        app.aaps.core.ui.R.attr.defaultTextColor
                    }
                )
            podInfoBinding.lastConnection.setTextColor(lastConnectionColor)
        } else {
            podInfoBinding.lastConnection.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor))
            podInfoBinding.lastConnection.text = if (podStateManager.hasPodState() && podStateManager.lastSuccessfulCommunication != null) {
                readableDuration(podStateManager.lastSuccessfulCommunication)
            } else {
                PLACEHOLDER
            }
        }
    }

    private fun updatePodStatus() {
        podInfoBinding.podStatus.text = if (!podStateManager.hasPodState()) {
            rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_status_no_active_pod)
        } else if (!podStateManager.isPodActivationCompleted) {
            if (!podStateManager.isPodInitialized) {
                rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_status_waiting_for_activation)
            } else {
                if (podStateManager.activationProgress.isBefore(ActivationProgress.PRIMING_COMPLETED)) {
                    rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_status_waiting_for_activation)
                } else {
                    rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_status_waiting_for_cannula_insertion)
                }
            }
        } else {
            if (podStateManager.podProgressStatus.isRunning) {
                var status = if (podStateManager.isSuspended) {
                    rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_status_suspended)
                } else {
                    rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_status_running)
                }

                if (!podStateManager.isBasalCertain) {
                    status += " (" + rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_uncertain) + ")"
                }

                status
            } else if (podStateManager.podProgressStatus == PodProgressStatus.FAULT_EVENT_OCCURRED) {
                rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_status_pod_fault)
            } else if (podStateManager.podProgressStatus == PodProgressStatus.INACTIVE) {
                rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_status_inactive)
            } else {
                podStateManager.podProgressStatus.toString()
            }
        }

        val podStatusColor =
            rh.gac(
                context,
                if (!podStateManager.isPodActivationCompleted || podStateManager.isPodDead || podStateManager.isSuspended || (podStateManager.isPodRunning && !podStateManager.isBasalCertain)) {
                    app.aaps.core.ui.R.attr.warningColor
                } else {
                    app.aaps.core.ui.R.attr.defaultTextColor
                }
            )
        podInfoBinding.podStatus.setTextColor(podStatusColor)
    }

    private fun updateLastBolus() {
        if (podStateManager.isPodActivationCompleted && podStateManager.hasLastBolus()) {
            var text =
                rh.gs(
                    app.aaps.pump.omnipod.common.R.string.omnipod_common_overview_last_bolus_value,
                    omnipodErosPumpPlugin.model().determineCorrectBolusSize(podStateManager.lastBolusAmount),
                    rh.gs(app.aaps.core.ui.R.string.insulin_unit_shortname),
                    readableDuration(podStateManager.lastBolusStartTime)
                )
            val textColor: Int
            if (podStateManager.isLastBolusCertain) {
                textColor = rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor)
            } else {
                textColor = rh.gac(context, app.aaps.core.ui.R.attr.warningColor)
                text += " (" + rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_uncertain) + ")"
            }

            podInfoBinding.lastBolus.text = text
            podInfoBinding.lastBolus.setTextColor(textColor)

        } else {
            podInfoBinding.lastBolus.text = PLACEHOLDER
            podInfoBinding.lastBolus.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor))
        }
    }

    private fun updateTempBasal() {
        if (podStateManager.isPodActivationCompleted && podStateManager.isTempBasalRunning) {
            if (!podStateManager.hasTempBasal()) {
                podInfoBinding.tempBasal.text = "???"
                podInfoBinding.tempBasal.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.warningColor))
            } else {
                val now = DateTime.now()

                val startTime = podStateManager.tempBasalStartTime
                val amount = podStateManager.tempBasalAmount
                val duration = podStateManager.tempBasalDuration

                val minutesRunning = Duration(startTime, now).standardMinutes
                val textColor: Int
                var text: String = rh.gs(
                    app.aaps.pump.omnipod.common.R.string.omnipod_common_overview_temp_basal_value,
                    amount,
                    dateUtil.timeString(startTime.millis),
                    minutesRunning,
                    duration.standardMinutes
                )
                if (podStateManager.isTempBasalCertain) {
                    textColor = rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor)
                } else {
                    textColor = rh.gac(context, app.aaps.core.ui.R.attr.warningColor)
                    text += " (" + rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_uncertain) + ")"
                }

                podInfoBinding.tempBasal.text = text
                podInfoBinding.tempBasal.setTextColor(textColor)
            }
        } else {
            var text = PLACEHOLDER
            val textColor: Int

            if (!podStateManager.isPodActivationCompleted || podStateManager.isTempBasalCertain) {
                textColor = rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor)
            } else {
                textColor = rh.gac(context, app.aaps.core.ui.R.attr.warningColor)
                text += " (" + rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_uncertain) + ")"
            }

            podInfoBinding.tempBasal.text = text
            podInfoBinding.tempBasal.setTextColor(textColor)
        }
    }

    private fun updateQueueStatus() {
        if (isQueueEmpty()) {
            podInfoBinding.queue.visibility = View.GONE
        } else {
            podInfoBinding.queue.visibility = View.VISIBLE
            podInfoBinding.queue.text = commandQueue.spannedStatus().toString()
        }
    }

    private fun updatePodActionButtons() {
        updateRefreshStatusButton()
        updateResumeDeliveryButton()
        updateSilenceAlertsButton()
        updateSuspendDeliveryButton()
        updateSetTimeButton()
    }

    private fun disablePodActionButtons() {
        buttonBinding.buttonSilenceAlerts.isEnabled = false
        buttonBinding.buttonResumeDelivery.isEnabled = false
        buttonBinding.buttonSuspendDelivery.isEnabled = false
        buttonBinding.buttonSetTime.isEnabled = false
        buttonBinding.buttonRefreshStatus.isEnabled = false
    }

    private fun updateRefreshStatusButton() {
        buttonBinding.buttonRefreshStatus.isEnabled = podStateManager.isPodInitialized && podStateManager.activationProgress.isAtLeast(ActivationProgress.PAIRING_COMPLETED)
            && rileyLinkServiceData.rileyLinkServiceState.isReady() && isQueueEmpty()
    }

    private fun updateResumeDeliveryButton() {
        if (podStateManager.isPodRunning && (podStateManager.isSuspended || commandQueue.isCustomCommandInQueue(CommandResumeDelivery::class.java))) {
            buttonBinding.buttonResumeDelivery.visibility = View.VISIBLE
            buttonBinding.buttonResumeDelivery.isEnabled = rileyLinkServiceData.rileyLinkServiceState.isReady() && isQueueEmpty()
        } else {
            buttonBinding.buttonResumeDelivery.visibility = View.GONE
        }
    }

    private fun updateSilenceAlertsButton() {
        if (!omnipodManager.isAutomaticallyAcknowledgeAlertsEnabled && podStateManager.isPodRunning && (podStateManager.hasActiveAlerts() || commandQueue.isCustomCommandInQueue(
                CommandSilenceAlerts::class.java
            ))
        ) {
            buttonBinding.buttonSilenceAlerts.visibility = View.VISIBLE
            buttonBinding.buttonSilenceAlerts.isEnabled = rileyLinkServiceData.rileyLinkServiceState.isReady() && isQueueEmpty()
        } else {
            buttonBinding.buttonSilenceAlerts.visibility = View.GONE
        }
    }

    private fun updateSuspendDeliveryButton() {
        // If the Pod is currently suspended, we show the Resume delivery button instead.
        if (omnipodManager.isSuspendDeliveryButtonEnabled && podStateManager.isPodRunning && (!podStateManager.isSuspended || commandQueue.isCustomCommandInQueue(CommandSuspendDelivery::class.java))) {
            buttonBinding.buttonSuspendDelivery.visibility = View.VISIBLE
            buttonBinding.buttonSuspendDelivery.isEnabled = podStateManager.isPodRunning && !podStateManager.isSuspended && rileyLinkServiceData.rileyLinkServiceState.isReady() && isQueueEmpty()
        } else {
            buttonBinding.buttonSuspendDelivery.visibility = View.GONE
        }
    }

    private fun updateSetTimeButton() {
        if (podStateManager.isPodRunning && (podStateManager.timeDeviatesMoreThan(Duration.standardMinutes(5)) || commandQueue.isCustomCommandInQueue(CommandHandleTimeChange::class.java))) {
            buttonBinding.buttonSetTime.visibility = View.VISIBLE
            buttonBinding.buttonSetTime.isEnabled = !podStateManager.isSuspended && rileyLinkServiceData.rileyLinkServiceState.isReady() && isQueueEmpty()
        } else {
            buttonBinding.buttonSetTime.visibility = View.GONE
        }
    }

    private fun displayNotConfiguredDialog() {
        context?.let {
            app.aaps.core.ui.UIRunnable {
                OKDialog.show(
                    it, rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_warning),
                    rh.gs(R.string.omnipod_eros_error_operation_not_possible_no_configuration)
                )
            }.run()
        }
    }

    private fun displayErrorDialog(title: String, message: String, withSound: Boolean) {
        uiInteraction.runAlarm(message, title, if (withSound) app.aaps.core.ui.R.raw.boluserror else 0)
    }

    private fun displayOkDialog(title: String, message: String) {
        context?.let {
            app.aaps.core.ui.UIRunnable {
                OKDialog.show(it, title, message)
            }.run()
        }
    }

    private fun readableZonedTime(time: DateTime): String {
        val timeAsJavaData = time.toLocalDateTime().toDate()
        val timeZone = podStateManager.timeZone.toTimeZone()
        if (timeZone == TimeZone.getDefault()) {
            return dateUtil.dateAndTimeString(timeAsJavaData.time)
        }

        val isDaylightTime = timeZone.inDaylightTime(timeAsJavaData)
        val locale = resources.configuration.locales[0]
        val timeZoneDisplayName = timeZone.getDisplayName(isDaylightTime, TimeZone.SHORT, locale) + " " + timeZone.getDisplayName(isDaylightTime, TimeZone.LONG, locale)
        return rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_time_with_timezone, dateUtil.dateAndTimeString(timeAsJavaData.time), timeZoneDisplayName)
    }

    private fun readableDuration(dateTime: DateTime): String {
        val duration = Duration(dateTime, DateTime.now())
        val hours = duration.standardHours.toInt()
        val minutes = duration.standardMinutes.toInt()
        val seconds = duration.standardSeconds.toInt()
        when {
            seconds < 10           -> return rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_moments_ago)
            seconds < 60           -> return rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_less_than_a_minute_ago)
            seconds < 60 * 60      -> return rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_time_ago, rh.gq(app.aaps.pump.omnipod.common.R.plurals.omnipod_common_minutes, minutes, minutes)) // < 1 hour
            seconds < 24 * 60 * 60 -> { // < 1 day
                val minutesLeft = minutes % 60
                if (minutesLeft > 0)
                    return rh.gs(
                        app.aaps.pump.omnipod.common.R.string.omnipod_common_time_ago,
                        rh.gs(
                            app.aaps.pump.omnipod.common.R.string.omnipod_common_composite_time,
                            rh.gq(app.aaps.pump.omnipod.common.R.plurals.omnipod_common_hours, hours, hours),
                            rh.gq(app.aaps.pump.omnipod.common.R.plurals.omnipod_common_minutes, minutesLeft, minutesLeft)
                        )
                    )
                return rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_time_ago, rh.gq(app.aaps.pump.omnipod.common.R.plurals.omnipod_common_hours, hours, hours))
            }

            else                   -> {
                val days = hours / 24
                val hoursLeft = hours % 24
                if (hoursLeft > 0)
                    return rh.gs(
                        app.aaps.pump.omnipod.common.R.string.omnipod_common_time_ago,
                        rh.gs(
                            app.aaps.pump.omnipod.common.R.string.omnipod_common_composite_time,
                            rh.gq(app.aaps.pump.omnipod.common.R.plurals.omnipod_common_days, days, days),
                            rh.gq(app.aaps.pump.omnipod.common.R.plurals.omnipod_common_hours, hoursLeft, hoursLeft)
                        )
                    )
                return rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_time_ago, rh.gq(app.aaps.pump.omnipod.common.R.plurals.omnipod_common_days, days, days))
            }
        }
    }

    private fun isQueueEmpty(): Boolean {
        return commandQueue.size() == 0 && commandQueue.performing() == null
    }

    // FIXME ideally we should just have access to LocalAlertUtils here
    private fun getPumpUnreachableTimeout(): Duration {
        return Duration.standardMinutes(preferences.get(IntKey.AlertsPumpUnreachableThreshold).toLong())
    }

    inner class DisplayResultDialogCallback(private val errorMessagePrefix: String, private val withSoundOnError: Boolean) : Callback() {

        private var messageOnSuccess: String? = null
        private var actionOnSuccess: Runnable? = null

        override fun run() {
            if (result.success) {
                val messageOnSuccess = this.messageOnSuccess
                if (messageOnSuccess != null) {
                    displayOkDialog(rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_confirmation), messageOnSuccess)
                }
                actionOnSuccess?.run()
            } else {
                displayErrorDialog(
                    rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_warning),
                    rh.gs(app.aaps.pump.omnipod.common.R.string.omnipod_common_two_strings_concatenated_by_colon, errorMessagePrefix, result.comment),
                    withSoundOnError
                )
            }
        }

        fun messageOnSuccess(message: String): DisplayResultDialogCallback {
            messageOnSuccess = message
            return this
        }

        fun actionOnSuccess(action: Runnable): DisplayResultDialogCallback {
            actionOnSuccess = action
            return this
        }
    }

}

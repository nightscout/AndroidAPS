package info.nightscout.androidaps.plugins.pump.omnipod.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData
import info.nightscout.androidaps.plugins.pump.omnipod.OmnipodPumpPlugin
import info.nightscout.androidaps.plugins.pump.omnipod.R
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.ActivationProgress
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.OmnipodConstants
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodProgressStatus
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.driver.util.TimeUtil
import info.nightscout.androidaps.plugins.pump.omnipod.event.EventOmnipodPumpValuesChanged
import info.nightscout.androidaps.plugins.pump.omnipod.manager.AapsOmnipodManager
import info.nightscout.androidaps.plugins.pump.omnipod.queue.command.CommandAcknowledgeAlerts
import info.nightscout.androidaps.plugins.pump.omnipod.queue.command.CommandGetPodStatus
import info.nightscout.androidaps.plugins.pump.omnipod.queue.command.CommandHandleTimeChange
import info.nightscout.androidaps.plugins.pump.omnipod.queue.command.CommandResumeDelivery
import info.nightscout.androidaps.plugins.pump.omnipod.queue.command.CommandSuspendDelivery
import info.nightscout.androidaps.plugins.pump.omnipod.util.AapsOmnipodUtil
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodAlertUtil
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.queue.events.EventQueueChanged
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.protection.ProtectionCheck
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import info.nightscout.androidaps.utils.ui.UIRunnable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.omnipod_overview.*
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import org.joda.time.Duration
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList

class OmnipodOverviewFragment : DaggerFragment() {
    companion object {

        private val REFRESH_INTERVAL_MILLIS = 15 * 1000L // 15 seconds
        private val PLACEHOLDER = "-" // 15 seconds
    }

    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var omnipodPumpPlugin: OmnipodPumpPlugin
    @Inject lateinit var podStateManager: PodStateManager
    @Inject lateinit var sp: SP
    @Inject lateinit var omnipodUtil: AapsOmnipodUtil
    @Inject lateinit var omnipodAlertUtil: OmnipodAlertUtil
    @Inject lateinit var rileyLinkServiceData: RileyLinkServiceData
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var omnipodManager: AapsOmnipodManager
    @Inject lateinit var protectionCheck: ProtectionCheck

    private var disposables: CompositeDisposable = CompositeDisposable()

    private val loopHandler = Handler(Looper.getMainLooper())
    private lateinit var refreshLoop: Runnable

    init {
        refreshLoop = Runnable {
            activity?.runOnUiThread { updateUi() }
            loopHandler.postDelayed(refreshLoop, REFRESH_INTERVAL_MILLIS)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.omnipod_overview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        omnipod_overview_button_pod_management.setOnClickListener {
            if (omnipodPumpPlugin.rileyLinkService?.verifyConfiguration() == true) {
                activity?.let { activity ->
                    context?.let { context ->
                        protectionCheck.queryProtection(
                            activity, ProtectionCheck.Protection.PREFERENCES,
                            UIRunnable { startActivity(Intent(context, PodManagementActivity::class.java)) }
                        )
                    }
                }
            } else {
                displayNotConfiguredDialog()
            }
        }

        omnipod_overview_button_resume_delivery.setOnClickListener {
            disablePodActionButtons()
            commandQueue.customCommand(CommandResumeDelivery(),
                DisplayResultDialogCallback(resourceHelper.gs(R.string.omnipod_error_failed_to_resume_delivery), true).messageOnSuccess(resourceHelper.gs(R.string.omnipod_confirmation_delivery_resumed)))
        }

        omnipod_overview_button_refresh_status.setOnClickListener {
            disablePodActionButtons()
            commandQueue.customCommand(CommandGetPodStatus(),
                DisplayResultDialogCallback(resourceHelper.gs(R.string.omnipod_error_failed_to_refresh_status), false))
        }

        omnipod_overview_button_acknowledge_active_alerts.setOnClickListener {
            disablePodActionButtons()
            commandQueue.customCommand(CommandAcknowledgeAlerts(),
                DisplayResultDialogCallback(resourceHelper.gs(R.string.omnipod_error_failed_to_acknowledge_alerts), false)
                    .messageOnSuccess(resourceHelper.gs(R.string.omnipod_confirmation_acknowledged_alerts))
                    .actionOnSuccess { rxBus.send(EventDismissNotification(Notification.OMNIPOD_POD_ALERTS)) })
        }

        omnipod_overview_button_suspend_delivery.setOnClickListener {
            disablePodActionButtons()
            commandQueue.customCommand(CommandSuspendDelivery(),
                DisplayResultDialogCallback(resourceHelper.gs(R.string.omnipod_error_failed_to_suspend_delivery), true)
                    .messageOnSuccess(resourceHelper.gs(R.string.omnipod_confirmation_suspended_delivery)))
        }

        omnipod_overview_button_set_time.setOnClickListener {
            disablePodActionButtons()
            commandQueue.customCommand(CommandHandleTimeChange(true),
                DisplayResultDialogCallback(resourceHelper.gs(R.string.omnipod_error_failed_to_set_time), true)
                    .messageOnSuccess(resourceHelper.gs(R.string.omnipod_confirmation_time_on_pod_updated)))
        }
    }

    override fun onResume() {
        super.onResume()
        loopHandler.postDelayed(refreshLoop, REFRESH_INTERVAL_MILLIS)
        disposables += rxBus
            .toObservable(EventRileyLinkDeviceStatusChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                updateRileyLinkStatus()
                updatePodActionButtons()
            }, { fabricPrivacy.logException(it) })
        disposables += rxBus
            .toObservable(EventOmnipodPumpValuesChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                updateOmnipodStatus()
                updatePodActionButtons()
            }, { fabricPrivacy.logException(it) })
        disposables += rxBus
            .toObservable(EventQueueChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                updateQueueStatus()
                updatePodActionButtons()
            }, { fabricPrivacy.logException(it) })
        disposables += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                updatePodActionButtons()
            }, { fabricPrivacy.logException(it) })
        updateUi()
    }

    override fun onPause() {
        super.onPause()
        disposables.clear()
        loopHandler.removeCallbacks(refreshLoop)
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

        omnipod_overview_riley_link_status.text =
            when {
                rileyLinkServiceState == RileyLinkServiceState.NotStarted -> resourceHelper.gs(resourceId)
                rileyLinkServiceState.isConnecting                        -> "{fa-bluetooth-b spin}   " + resourceHelper.gs(resourceId)
                rileyLinkServiceState.isError && rileyLinkError == null   -> "{fa-bluetooth-b}   " + resourceHelper.gs(resourceId)
                rileyLinkServiceState.isError && rileyLinkError != null   -> "{fa-bluetooth-b}   " + resourceHelper.gs(rileyLinkError.getResourceId(RileyLinkTargetDevice.Omnipod))
                else                                                      -> "{fa-bluetooth-b}   " + resourceHelper.gs(resourceId)
            }
        omnipod_overview_riley_link_status.setTextColor(if (rileyLinkServiceState.isError || rileyLinkError != null) Color.RED else Color.WHITE)
    }

    private fun updateOmnipodStatus() {
        updateLastConnection()
        updateLastBolus()
        updateTempBasal()
        updatePodStatus()

        val errors = ArrayList<String>()
        if (omnipodPumpPlugin.rileyLinkService != null) {
            val rileyLinkErrorDescription = omnipodPumpPlugin.rileyLinkService.errorDescription
            if (StringUtils.isNotEmpty(rileyLinkErrorDescription)) {
                errors.add(rileyLinkErrorDescription)
            }
        }

        if (!podStateManager.hasPodState() || !podStateManager.isPodInitialized) {
            omnipod_overview_pod_address.text = if (podStateManager.hasPodState()) {
                podStateManager.address.toString()
            } else {
                PLACEHOLDER
            }
            omnipod_overview_pod_lot.text = PLACEHOLDER
            omnipod_overview_pod_tid.text = PLACEHOLDER
            omnipod_overview_firmware_version.text = PLACEHOLDER
            omnipod_overview_time_on_pod.text = PLACEHOLDER
            omnipod_overview_pod_expiry_date.text = PLACEHOLDER
            omnipod_overview_pod_expiry_date.setTextColor(Color.WHITE)
            omnipod_overview_base_basal_rate.text = PLACEHOLDER
            omnipod_overview_total_delivered.text = PLACEHOLDER
            omnipod_overview_reservoir.text = PLACEHOLDER
            omnipod_overview_reservoir.setTextColor(Color.WHITE)
            omnipod_overview_pod_active_alerts.text = PLACEHOLDER
        } else {
            omnipod_overview_pod_address.text = podStateManager.address.toString()
            omnipod_overview_pod_lot.text = podStateManager.lot.toString()
            omnipod_overview_pod_tid.text = podStateManager.tid.toString()
            omnipod_overview_firmware_version.text = resourceHelper.gs(R.string.omnipod_firmware_version_value, podStateManager.pmVersion.toString(), podStateManager.piVersion.toString())

            omnipod_overview_time_on_pod.text = readableZonedTime(podStateManager.time)
            omnipod_overview_time_on_pod.setTextColor(if (podStateManager.timeDeviatesMoreThan(OmnipodConstants.TIME_DEVIATION_THRESHOLD)) {
                Color.RED
            } else {
                Color.WHITE
            })
            val expiresAt = podStateManager.expiresAt
            if (expiresAt == null) {
                omnipod_overview_pod_expiry_date.text = PLACEHOLDER
                omnipod_overview_pod_expiry_date.setTextColor(Color.WHITE)
            } else {
                omnipod_overview_pod_expiry_date.text = readableZonedTime(expiresAt)
                omnipod_overview_pod_expiry_date.setTextColor(if (DateTime.now().isAfter(expiresAt)) {
                    Color.RED
                } else {
                    Color.WHITE
                })
            }

            if (podStateManager.isPodFaulted) {
                val faultEventCode = podStateManager.faultEventCode
                errors.add(resourceHelper.gs(R.string.omnipod_pod_status_pod_fault_description, faultEventCode.value, faultEventCode.name))
            }

            // base basal rate
            omnipod_overview_base_basal_rate.text = if (podStateManager.isPodActivationCompleted) {
                resourceHelper.gs(R.string.pump_basebasalrate, omnipodPumpPlugin.model().determineCorrectBasalSize(podStateManager.basalSchedule.rateAt(TimeUtil.toDuration(DateTime.now()))))
            } else {
                PLACEHOLDER
            }

            // total delivered
            omnipod_overview_total_delivered.text = if (podStateManager.isPodActivationCompleted && podStateManager.totalInsulinDelivered != null) {
                resourceHelper.gs(R.string.omnipod_overview_total_delivered_value, podStateManager.totalInsulinDelivered - OmnipodConstants.POD_SETUP_UNITS)
            } else {
                PLACEHOLDER
            }

            // reservoir
            if (podStateManager.reservoirLevel == null) {
                omnipod_overview_reservoir.text = resourceHelper.gs(R.string.omnipod_overview_reservoir_value_over50)
                omnipod_overview_reservoir.setTextColor(Color.WHITE)
            } else {
                val lowReservoirThreshold = (omnipodAlertUtil.lowReservoirAlertUnits
                    ?: OmnipodConstants.DEFAULT_MAX_RESERVOIR_ALERT_THRESHOLD).toDouble()

                omnipod_overview_reservoir.text = resourceHelper.gs(R.string.omnipod_overview_reservoir_value, podStateManager.reservoirLevel)
                omnipod_overview_reservoir.setTextColor(if (podStateManager.reservoirLevel < lowReservoirThreshold) {
                    Color.RED
                } else {
                    Color.WHITE
                })
            }

            omnipod_overview_pod_active_alerts.text = if (podStateManager.hasActiveAlerts()) {
                TextUtils.join(System.lineSeparator(), omnipodUtil.getTranslatedActiveAlerts(podStateManager))
            } else {
                PLACEHOLDER
            }
        }

        if (errors.size == 0) {
            omnipod_overview_errors.text = PLACEHOLDER
            omnipod_overview_errors.setTextColor(Color.WHITE)
        } else {
            omnipod_overview_errors.text = StringUtils.join(errors, System.lineSeparator())
            omnipod_overview_errors.setTextColor(Color.RED)
        }
    }

    private fun updateLastConnection() {
        if (podStateManager.isPodInitialized && podStateManager.lastSuccessfulCommunication != null) {
            omnipod_overview_last_connection.text = readableDuration(podStateManager.lastSuccessfulCommunication)
            val lastConnectionColor =
                if (omnipodPumpPlugin.isUnreachableAlertTimeoutExceeded(getPumpUnreachableTimeout().millis)) {
                    Color.RED
                } else {
                    Color.WHITE
                }
            omnipod_overview_last_connection.setTextColor(lastConnectionColor)
        } else {
            omnipod_overview_last_connection.setTextColor(Color.WHITE)
            omnipod_overview_last_connection.text = if (podStateManager.hasPodState() && podStateManager.lastSuccessfulCommunication != null) {
                readableDuration(podStateManager.lastSuccessfulCommunication)
            } else {
                PLACEHOLDER
            }
        }
    }

    private fun updatePodStatus() {
        omnipod_overview_pod_status.text = if (!podStateManager.hasPodState()) {
            resourceHelper.gs(R.string.omnipod_pod_status_no_active_pod)
        } else if (!podStateManager.isPodActivationCompleted) {
            if (!podStateManager.isPodInitialized) {
                resourceHelper.gs(R.string.omnipod_pod_status_waiting_for_activation)
            } else {
                if (podStateManager.activationProgress.isBefore(ActivationProgress.PRIMING_COMPLETED)) {
                    resourceHelper.gs(R.string.omnipod_pod_status_waiting_for_activation)
                } else {
                    resourceHelper.gs(R.string.omnipod_pod_status_waiting_for_cannula_insertion)
                }
            }
        } else {
            if (podStateManager.podProgressStatus.isRunning) {
                var status = if (podStateManager.isSuspended) {
                    resourceHelper.gs(R.string.omnipod_pod_status_suspended)
                } else {
                    resourceHelper.gs(R.string.omnipod_pod_status_running)
                }

                if (!podStateManager.isBasalCertain) {
                    status += " (" + resourceHelper.gs(R.string.omnipod_uncertain) + ")"
                }

                status
            } else if (podStateManager.podProgressStatus == PodProgressStatus.FAULT_EVENT_OCCURRED) {
                resourceHelper.gs(R.string.omnipod_pod_status_pod_fault)
            } else if (podStateManager.podProgressStatus == PodProgressStatus.INACTIVE) {
                resourceHelper.gs(R.string.omnipod_pod_status_inactive)
            } else {
                podStateManager.podProgressStatus.toString()
            }
        }

        val podStatusColor = if (!podStateManager.isPodActivationCompleted || podStateManager.isPodDead || podStateManager.isSuspended || (podStateManager.isPodRunning && !podStateManager.isBasalCertain)) {
            Color.RED
        } else {
            Color.WHITE
        }
        omnipod_overview_pod_status.setTextColor(podStatusColor)
    }

    private fun updateLastBolus() {
        if (podStateManager.isPodActivationCompleted && podStateManager.hasLastBolus()) {
            var text = resourceHelper.gs(R.string.omnipod_overview_last_bolus_value, omnipodPumpPlugin.model().determineCorrectBolusSize(podStateManager.lastBolusAmount), resourceHelper.gs(R.string.insulin_unit_shortname), readableDuration(podStateManager.lastBolusStartTime))
            val textColor: Int

            if (podStateManager.isLastBolusCertain) {
                textColor = Color.WHITE
            } else {
                textColor = Color.RED
                text += " (" + resourceHelper.gs(R.string.omnipod_uncertain) + ")"
            }

            omnipod_overview_last_bolus.text = text
            omnipod_overview_last_bolus.setTextColor(textColor)

        } else {
            omnipod_overview_last_bolus.text = PLACEHOLDER
            omnipod_overview_last_bolus.setTextColor(Color.WHITE)
        }
    }

    private fun updateTempBasal() {
        if (podStateManager.isPodActivationCompleted && podStateManager.isTempBasalRunning) {
            if (!podStateManager.hasTempBasal()) {
                omnipod_overview_temp_basal.text = "???"
                omnipod_overview_temp_basal.setTextColor(Color.RED)
            } else {
                val now = DateTime.now()

                val startTime = podStateManager.tempBasalStartTime
                val amount = podStateManager.tempBasalAmount
                val duration = podStateManager.tempBasalDuration

                val minutesRunning = Duration(startTime, now).standardMinutes

                var text: String
                val textColor: Int
                text = resourceHelper.gs(R.string.omnipod_overview_temp_basal_value, amount, dateUtil.timeString(startTime.millis), minutesRunning, duration.standardMinutes)
                if (podStateManager.isTempBasalCertain) {
                    textColor = Color.WHITE
                } else {
                    textColor = Color.RED
                    text += " (" + resourceHelper.gs(R.string.omnipod_uncertain) + ")"
                }

                omnipod_overview_temp_basal.text = text
                omnipod_overview_temp_basal.setTextColor(textColor)
            }
        } else {
            var text = PLACEHOLDER
            val textColor: Int

            if (!podStateManager.isPodActivationCompleted || podStateManager.isTempBasalCertain) {
                textColor = Color.WHITE
            } else {
                textColor = Color.RED
                text += " (" + resourceHelper.gs(R.string.omnipod_uncertain) + ")"
            }

            omnipod_overview_temp_basal.text = text
            omnipod_overview_temp_basal.setTextColor(textColor)
        }
    }

    private fun updateQueueStatus() {
        if (isQueueEmpty()) {
            omnipod_overview_queue.visibility = View.GONE
        } else {
            omnipod_overview_queue.visibility = View.VISIBLE
            omnipod_overview_queue.text = commandQueue.spannedStatus().toString()
        }
    }

    private fun updatePodActionButtons() {
        updateRefreshStatusButton()
        updateResumeDeliveryButton()
        updateAcknowledgeAlertsButton()
        updateSuspendDeliveryButton()
        updateSetTimeButton()
    }

    private fun disablePodActionButtons() {
        omnipod_overview_button_acknowledge_active_alerts.isEnabled = false
        omnipod_overview_button_resume_delivery.isEnabled = false
        omnipod_overview_button_suspend_delivery.isEnabled = false
        omnipod_overview_button_set_time.isEnabled = false
        omnipod_overview_button_refresh_status.isEnabled = false
    }

    private fun updateRefreshStatusButton() {
        omnipod_overview_button_refresh_status.isEnabled = podStateManager.isPodInitialized && podStateManager.activationProgress.isAtLeast(ActivationProgress.PAIRING_COMPLETED)
            && rileyLinkServiceData.rileyLinkServiceState.isReady && isQueueEmpty()
    }

    private fun updateResumeDeliveryButton() {
        if (podStateManager.isPodRunning && (podStateManager.isSuspended || commandQueue.isCustomCommandInQueue(CommandResumeDelivery::class.java))) {
            omnipod_overview_button_resume_delivery.visibility = View.VISIBLE
            omnipod_overview_button_resume_delivery.isEnabled = rileyLinkServiceData.rileyLinkServiceState.isReady && isQueueEmpty()
        } else {
            omnipod_overview_button_resume_delivery.visibility = View.GONE
        }
    }

    private fun updateAcknowledgeAlertsButton() {
        if (!omnipodManager.isAutomaticallyAcknowledgeAlertsEnabled && podStateManager.isPodRunning && (podStateManager.hasActiveAlerts() || commandQueue.isCustomCommandInQueue(CommandAcknowledgeAlerts::class.java))) {
            omnipod_overview_button_acknowledge_active_alerts.visibility = View.VISIBLE
            omnipod_overview_button_acknowledge_active_alerts.isEnabled = rileyLinkServiceData.rileyLinkServiceState.isReady && isQueueEmpty()
        } else {
            omnipod_overview_button_acknowledge_active_alerts.visibility = View.GONE
        }
    }

    private fun updateSuspendDeliveryButton() {
        // If the Pod is currently suspended, we show the Resume delivery button instead.
        if (omnipodManager.isSuspendDeliveryButtonEnabled && podStateManager.isPodRunning && (!podStateManager.isSuspended || commandQueue.isCustomCommandInQueue(CommandSuspendDelivery::class.java))) {
            omnipod_overview_button_suspend_delivery.visibility = View.VISIBLE
            omnipod_overview_button_suspend_delivery.isEnabled = podStateManager.isPodRunning && !podStateManager.isSuspended && rileyLinkServiceData.rileyLinkServiceState.isReady && isQueueEmpty()
        } else {
            omnipod_overview_button_suspend_delivery.visibility = View.GONE
        }
    }

    private fun updateSetTimeButton() {
        if (podStateManager.isPodRunning && (podStateManager.timeDeviatesMoreThan(Duration.standardMinutes(5)) || commandQueue.isCustomCommandInQueue(CommandHandleTimeChange::class.java))) {
            omnipod_overview_button_set_time.visibility = View.VISIBLE
            omnipod_overview_button_set_time.isEnabled = !podStateManager.isSuspended && rileyLinkServiceData.rileyLinkServiceState.isReady && isQueueEmpty()
        } else {
            omnipod_overview_button_set_time.visibility = View.GONE
        }
    }

    private fun displayNotConfiguredDialog() {
        context?.let {
            UIRunnable {
                OKDialog.show(it, resourceHelper.gs(R.string.omnipod_warning),
                    resourceHelper.gs(R.string.omnipod_error_operation_not_possible_no_configuration), null)
            }.run()
        }
    }

    private fun displayErrorDialog(title: String, message: String, withSound: Boolean) {
        context?.let {
            val i = Intent(it, ErrorHelperActivity::class.java)
            i.putExtra("soundid", if (withSound) R.raw.boluserror else 0)
            i.putExtra("status", message)
            i.putExtra("title", title)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            it.startActivity(i)
        }
    }

    private fun displayOkDialog(title: String, message: String) {
        context?.let {
            UIRunnable {
                OKDialog.show(it, title, message, null)
            }.run()
        }
    }

    private fun readableZonedTime(time: DateTime): String {
        val timeAsJavaData = time.toLocalDateTime().toDate()
        val timeZone = podStateManager.timeZone.toTimeZone()
        if (timeZone == TimeZone.getDefault()) {
            return dateUtil.dateAndTimeString(timeAsJavaData)
        }

        val isDaylightTime = timeZone.inDaylightTime(timeAsJavaData)
        val locale = resources.configuration.locales.get(0)
        val timeZoneDisplayName = timeZone.getDisplayName(isDaylightTime, TimeZone.SHORT, locale) + " " + timeZone.getDisplayName(isDaylightTime, TimeZone.LONG, locale)
        return resourceHelper.gs(R.string.omnipod_time_with_timezone, dateUtil.dateAndTimeString(timeAsJavaData), timeZoneDisplayName)
    }

    private fun readableDuration(dateTime: DateTime): String {
        val duration = Duration(dateTime, DateTime.now())
        val hours = duration.standardHours.toInt()
        val minutes = duration.standardMinutes.toInt()
        val seconds = duration.standardSeconds.toInt()
        when {
            seconds < 10           -> {
                return resourceHelper.gs(R.string.omnipod_moments_ago)
            }

            seconds < 60           -> {
                return resourceHelper.gs(R.string.omnipod_less_than_a_minute_ago)
            }

            seconds < 60 * 60      -> { // < 1 hour
                return resourceHelper.gs(R.string.omnipod_time_ago, resourceHelper.gq(R.plurals.omnipod_minutes, minutes, minutes))
            }

            seconds < 24 * 60 * 60 -> { // < 1 day
                val minutesLeft = minutes % 60
                if (minutesLeft > 0)
                    return resourceHelper.gs(R.string.omnipod_time_ago,
                        resourceHelper.gs(R.string.omnipod_composite_time, resourceHelper.gq(R.plurals.omnipod_hours, hours, hours), resourceHelper.gq(R.plurals.omnipod_minutes, minutesLeft, minutesLeft)))
                return resourceHelper.gs(R.string.omnipod_time_ago, resourceHelper.gq(R.plurals.omnipod_hours, hours, hours))
            }

            else                   -> {
                val days = hours / 24
                val hoursLeft = hours % 24
                if (hoursLeft > 0)
                    return resourceHelper.gs(R.string.omnipod_time_ago,
                        resourceHelper.gs(R.string.omnipod_composite_time, resourceHelper.gq(R.plurals.omnipod_days, days, days), resourceHelper.gq(R.plurals.omnipod_hours, hoursLeft, hoursLeft)))
                return resourceHelper.gs(R.string.omnipod_time_ago, resourceHelper.gq(R.plurals.omnipod_days, days, days))
            }
        }
    }

    private fun isQueueEmpty(): Boolean {
        return commandQueue.size() == 0 && commandQueue.performing() == null
    }

    // FIXME ideally we should just have access to LocalAlertUtils here
    private fun getPumpUnreachableTimeout(): Duration {
        return Duration.standardMinutes(sp.getInt(R.string.key_pump_unreachable_threshold_minutes, Constants.DEFAULT_PUMP_UNREACHABLE_THRESHOLD_MINUTES).toLong())
    }

    inner class DisplayResultDialogCallback(private val errorMessagePrefix: String, private val withSoundOnError: Boolean) : Callback() {

        private var messageOnSuccess: String? = null
        private var actionOnSuccess: Runnable? = null

        override fun run() {
            if (result.success) {
                val messageOnSuccess = this.messageOnSuccess
                if (messageOnSuccess != null) {
                    displayOkDialog(resourceHelper.gs(R.string.omnipod_confirmation), messageOnSuccess)
                }
                actionOnSuccess?.run()
            } else {
                displayErrorDialog(resourceHelper.gs(R.string.omnipod_warning), resourceHelper.gs(R.string.omnipod_two_strings_concatenated_by_colon, errorMessagePrefix, result.comment), withSoundOnError)
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

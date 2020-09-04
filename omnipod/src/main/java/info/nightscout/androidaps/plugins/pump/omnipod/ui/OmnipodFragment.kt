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
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.dialog.RileyLinkStatusActivity
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData
import info.nightscout.androidaps.plugins.pump.omnipod.OmnipodPumpPlugin
import info.nightscout.androidaps.plugins.pump.omnipod.R
import info.nightscout.androidaps.plugins.pump.omnipod.definition.OmnipodStatusRequestType
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.OmnipodConstants
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodProgressStatus
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.event.EventOmnipodPumpValuesChanged
import info.nightscout.androidaps.plugins.pump.omnipod.manager.AapsOmnipodManager
import info.nightscout.androidaps.plugins.pump.omnipod.util.AapsOmnipodUtil
import info.nightscout.androidaps.queue.commands.Command
import info.nightscout.androidaps.queue.events.EventQueueChanged
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.WarnColors
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.protection.ProtectionCheck
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import info.nightscout.androidaps.utils.ui.UIRunnable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.omnipod_fragment.*
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import org.joda.time.Duration
import javax.inject.Inject

class OmnipodFragment : DaggerFragment() {
    companion object {
        private val REFRESH_INTERVAL_MILLIS = 15 * 1000L; // 15 seconds
        private val PLACEHOLDER = "-"; // 15 seconds
    }

    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var omnipodPumpPlugin: OmnipodPumpPlugin
    @Inject lateinit var warnColors: WarnColors
    @Inject lateinit var podStateManager: PodStateManager
    @Inject lateinit var sp: SP
    @Inject lateinit var omnipodUtil: AapsOmnipodUtil
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
        return inflater.inflate(R.layout.omnipod_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        omnipod_button_resume_delivery.setOnClickListener {
            disablePodActionButtons()
            commandQueue.startPump(null)
        }

        omnipod_button_pod_mgmt.setOnClickListener {
            if (omnipodPumpPlugin.rileyLinkService?.verifyConfiguration() == true) {
                activity?.let { activity ->
                    protectionCheck.queryProtection(
                        activity, ProtectionCheck.Protection.PREFERENCES,
                        UIRunnable(Runnable { startActivity(Intent(context, PodManagementActivity::class.java)) })
                    )
                }
            } else {
                displayNotConfiguredDialog()
            }
        }

        omnipod_button_refresh_status.setOnClickListener {
            disablePodActionButtons()
            omnipodPumpPlugin.addPodStatusRequest(OmnipodStatusRequestType.GET_POD_STATE);
            commandQueue.readStatus("Clicked Refresh", null)
        }

        omnipod_button_rileylink_stats.setOnClickListener {
            if (omnipodPumpPlugin.rileyLinkService?.verifyConfiguration() == true) {
                startActivity(Intent(context, RileyLinkStatusActivity::class.java))
            } else {
                displayNotConfiguredDialog()
            }
        }

        omnipod_button_acknowledge_active_alerts.setOnClickListener {
            disablePodActionButtons()
            omnipodPumpPlugin.addPodStatusRequest(OmnipodStatusRequestType.ACKNOWLEDGE_ALERTS);
            commandQueue.readStatus("Clicked Acknowledge Alert", null)
        }

        omnipod_button_suspend_delivery.setOnClickListener {
            disablePodActionButtons()
            omnipodPumpPlugin.addPodStatusRequest(OmnipodStatusRequestType.SUSPEND_DELIVERY);
            commandQueue.readStatus("Clicked Suspend Delivery", null)
        }

        omnipod_button_pulse_log.setOnClickListener {
            disablePodActionButtons()
            omnipodPumpPlugin.addPodStatusRequest(OmnipodStatusRequestType.GET_PULSE_LOG);
            commandQueue.readStatus("Clicked Pulse Log", null)
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
            .observeOn(Schedulers.io())
            .subscribe({
                updatePulseLogButton()
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

        val resourceId = rileyLinkServiceState.getResourceId()
        val rileyLinkError = rileyLinkServiceData.rileyLinkError

        omnipod_rl_status.text =
            when {
                rileyLinkServiceState == RileyLinkServiceState.NotStarted -> resourceHelper.gs(resourceId)
                rileyLinkServiceState.isConnecting                        -> "{fa-bluetooth-b spin}   " + resourceHelper.gs(resourceId)
                rileyLinkServiceState.isError && rileyLinkError == null   -> "{fa-bluetooth-b}   " + resourceHelper.gs(resourceId)
                rileyLinkServiceState.isError && rileyLinkError != null   -> "{fa-bluetooth-b}   " + resourceHelper.gs(rileyLinkError.getResourceId(RileyLinkTargetDevice.Omnipod))
                else                                                      -> "{fa-bluetooth-b}   " + resourceHelper.gs(resourceId)
            }
        omnipod_rl_status.setTextColor(if (rileyLinkServiceState.isError || rileyLinkError != null) Color.RED else Color.WHITE)
    }

    private fun updateOmnipodStatus() {
        updateLastConnection()
        updateLastBolus()
        updateTempBasal()
        updatePodStatus()

        val errors = ArrayList<String>();
        if (omnipodPumpPlugin.rileyLinkService != null) {
            val rileyLinkErrorDescription = omnipodPumpPlugin.rileyLinkService.errorDescription
            if (StringUtils.isNotEmpty(rileyLinkErrorDescription)) {
                errors.add(rileyLinkErrorDescription)
            }
        }

        if (!podStateManager.hasPodState() || !podStateManager.isPodInitialized) {
            omnipod_pod_address.text = if (podStateManager.hasPodState()) {
                podStateManager.address.toString()
            } else {
                PLACEHOLDER
            }
            omnipod_pod_lot.text = PLACEHOLDER
            omnipod_pod_tid.text = PLACEHOLDER
            omnipod_pod_firmware_version.text = PLACEHOLDER
            omnipod_pod_expiry.text = PLACEHOLDER
            omnipod_pod_expiry.setTextColor(Color.WHITE)
            omnipod_base_basal_rate.text = PLACEHOLDER
            omnipod_total_delivered.text = PLACEHOLDER
            omnipod_reservoir.text = PLACEHOLDER
            omnipod_pod_active_alerts.text = PLACEHOLDER
        } else {
            omnipod_pod_address.text = podStateManager.address.toString()
            omnipod_pod_lot.text = podStateManager.lot.toString()
            omnipod_pod_tid.text = podStateManager.tid.toString()
            omnipod_pod_firmware_version.text = resourceHelper.gs(R.string.omnipod_pod_firmware_version_value, podStateManager.pmVersion.toString(), podStateManager.piVersion.toString())
            val expiresAt = podStateManager.expiresAt
            if (expiresAt == null) {
                omnipod_pod_expiry.text = PLACEHOLDER
                omnipod_pod_expiry.setTextColor(Color.WHITE)
            } else {
                omnipod_pod_expiry.text = dateUtil.dateAndTimeString(expiresAt.toDate())
                omnipod_pod_expiry.setTextColor(if (DateTime.now().isAfter(expiresAt)) {
                    Color.RED
                } else {
                    Color.WHITE
                })
            }

            if (podStateManager.hasFaultEvent()) {
                val faultEventCode = podStateManager.faultEvent.faultEventCode
                errors.add(resourceHelper.gs(R.string.omnipod_pod_status_pod_fault_description, faultEventCode.value, faultEventCode.name))
            }

            val now = DateTime.now()

            // base basal rate
            omnipod_base_basal_rate.text = if (podStateManager.isPodActivationCompleted) {
                resourceHelper.gs(R.string.pump_basebasalrate, omnipodPumpPlugin.model().determineCorrectBasalSize(podStateManager.basalSchedule.rateAt(Duration(now.withTimeAtStartOfDay(), now))))
            } else {
                PLACEHOLDER
            }

            // total delivered
            omnipod_total_delivered.text = if (podStateManager.isPodActivationCompleted && podStateManager.totalInsulinDelivered != null) {
                resourceHelper.gs(R.string.omnipod_total_delivered, podStateManager.totalInsulinDelivered - OmnipodConstants.POD_SETUP_UNITS);
            } else {
                PLACEHOLDER
            }

            // reservoir
            if (podStateManager.reservoirLevel == null) {
                omnipod_reservoir.text = resourceHelper.gs(R.string.omnipod_reservoir_over50)
                omnipod_reservoir.setTextColor(Color.WHITE)
            } else {
                omnipod_reservoir.text = resourceHelper.gs(R.string.omnipod_reservoir_left, podStateManager.reservoirLevel)
                warnColors.setColorInverse(omnipod_reservoir, podStateManager.reservoirLevel, 50.0, 20.0)
            }

            omnipod_pod_active_alerts.text = if (podStateManager.hasActiveAlerts()) {
                TextUtils.join(System.lineSeparator(), omnipodUtil.getTranslatedActiveAlerts(podStateManager))
            } else {
                PLACEHOLDER
            }
        }

        if (errors.size == 0) {
            omnipod_errors.text = PLACEHOLDER
            omnipod_errors.setTextColor(Color.WHITE)
        } else {
            omnipod_errors.text = StringUtils.join(errors, System.lineSeparator())
            omnipod_errors.setTextColor(Color.RED)
        }
    }

    private fun updateLastConnection() {
        if (podStateManager.isPodInitialized && podStateManager.lastSuccessfulCommunication != null) {
            omnipod_last_connection.text = readableDuration(podStateManager.lastSuccessfulCommunication)
            val lastConnectionColor =
                if (omnipodPumpPlugin.isUnreachableAlertTimeoutExceeded(getPumpUnreachableTimeout().millis)) {
                    Color.RED
                } else {
                    Color.WHITE
                }
            omnipod_last_connection.setTextColor(lastConnectionColor)
        } else {
            omnipod_last_connection.setTextColor(Color.WHITE)
            omnipod_last_connection.text = if (podStateManager.hasPodState() && podStateManager.lastSuccessfulCommunication != null) {
                readableDuration(podStateManager.lastSuccessfulCommunication)
            } else {
                PLACEHOLDER
            }
        }
    }

    private fun updatePodStatus() {
        omnipod_pod_status.text = if (!podStateManager.hasPodState()) {
            resourceHelper.gs(R.string.omnipod_pod_status_no_active_pod)
        } else if (!podStateManager.isPodActivationCompleted) {
            if (!podStateManager.isPodInitialized) {
                resourceHelper.gs(R.string.omnipod_pod_status_waiting_for_pair_and_prime)
            } else {
                if (PodProgressStatus.ACTIVATION_TIME_EXCEEDED == podStateManager.podProgressStatus) {
                    resourceHelper.gs(R.string.omnipod_pod_status_activation_time_exceeded)
                } else if (podStateManager.podProgressStatus.isBefore(PodProgressStatus.PRIMING_COMPLETED)) {
                    resourceHelper.gs(R.string.omnipod_pod_status_waiting_for_pair_and_prime)
                } else {
                    resourceHelper.gs(R.string.omnipod_pod_status_waiting_for_cannula_insertion)
                }
            }
        } else {
            if (podStateManager.podProgressStatus.isRunning) {
                if (podStateManager.isSuspended) {
                    resourceHelper.gs(R.string.omnipod_pod_status_suspended)
                } else {
                    resourceHelper.gs(R.string.omnipod_pod_status_running)
                }
            } else if (podStateManager.podProgressStatus == PodProgressStatus.FAULT_EVENT_OCCURRED) {
                resourceHelper.gs(R.string.omnipod_pod_status_pod_fault)
            } else if (podStateManager.podProgressStatus == PodProgressStatus.INACTIVE) {
                resourceHelper.gs(R.string.omnipod_pod_status_inactive)
            } else {
                podStateManager.podProgressStatus.toString()
            }
        }

        val podStatusColor = if (!podStateManager.isPodActivationCompleted || podStateManager.isPodDead || podStateManager.isSuspended) {
            Color.RED
        } else {
            Color.WHITE
        }
        omnipod_pod_status.setTextColor(podStatusColor)
    }

    private fun updateLastBolus() {
        if (podStateManager.isPodActivationCompleted && podStateManager.hasLastBolus()) {
            var text = resourceHelper.gs(R.string.omnipod_last_bolus, omnipodPumpPlugin.model().determineCorrectBolusSize(podStateManager.lastBolusAmount), resourceHelper.gs(R.string.insulin_unit_shortname), readableDuration(podStateManager.lastBolusStartTime))
            val textColor: Int

            if (podStateManager.isLastBolusCertain) {
                textColor = Color.WHITE
            } else {
                textColor = Color.RED
                text += " (" + resourceHelper.gs(R.string.omnipod_uncertain) + ")"
            }

            omnipod_last_bolus.text = text;
            omnipod_last_bolus.setTextColor(textColor)

        } else {
            omnipod_last_bolus.text = PLACEHOLDER
            omnipod_last_bolus.setTextColor(Color.WHITE)
        }
    }

    private fun updateTempBasal() {
        if (podStateManager.isPodActivationCompleted && podStateManager.isTempBasalRunning) {
            val now = DateTime.now()

            val startTime = podStateManager.tempBasalStartTime;
            val amount = podStateManager.tempBasalAmount
            val duration = podStateManager.tempBasalDuration;

            val minutesRunning = Duration(startTime, now).standardMinutes

            var text: String
            val textColor: Int
            text = resourceHelper.gs(R.string.omnipod_temp_basal, amount, dateUtil.timeString(startTime.millis), minutesRunning, duration.standardMinutes)
            if (podStateManager.isTempBasalCertain) {
                textColor = Color.WHITE
            } else {
                textColor = Color.RED
                text += " (" + resourceHelper.gs(R.string.omnipod_uncertain) + ")"
            }

            omnipod_temp_basal.text = text;
            omnipod_temp_basal.setTextColor(textColor)
        } else {
            omnipod_temp_basal.text = PLACEHOLDER
            omnipod_temp_basal.setTextColor(Color.WHITE)
        }
    }

    private fun updateQueueStatus() {
        if (isQueueEmpty()) {
            omnipod_queue.visibility = View.GONE
        } else {
            omnipod_queue.visibility = View.VISIBLE
            omnipod_queue.text = commandQueue.spannedStatus().toString()
        }
    }

    private fun updatePodActionButtons() {
        updateRefreshStatusButton()
        updateResumeDeliveryButton()
        updateAcknowledgeAlertsButton()
        updateSuspendDeliveryButton()
        updatePulseLogButton()
    }

    private fun disablePodActionButtons() {
        omnipod_button_acknowledge_active_alerts.isEnabled = false
        omnipod_button_resume_delivery.isEnabled = false
        omnipod_button_refresh_status.isEnabled = false
        omnipod_button_pulse_log.isEnabled = false
    }

    private fun updateRefreshStatusButton() {
        omnipod_button_refresh_status.isEnabled = podStateManager.isPodInitialized && podStateManager.podProgressStatus.isAtLeast(PodProgressStatus.PAIRING_COMPLETED)
            && rileyLinkServiceData.rileyLinkServiceState.isReady && isQueueEmpty()
    }

    private fun updateResumeDeliveryButton() {
        val queueEmptyOrStartingPump = isQueueEmpty() || commandQueue.isRunning(Command.CommandType.START_PUMP)
        if (podStateManager.isPodActivationCompleted && podStateManager.isSuspended && queueEmptyOrStartingPump) {
            omnipod_button_resume_delivery.visibility = View.VISIBLE
            omnipod_button_resume_delivery.isEnabled = rileyLinkServiceData.rileyLinkServiceState.isReady && isQueueEmpty()
        } else {
            omnipod_button_resume_delivery.visibility = View.GONE
        }
    }

    private fun updateAcknowledgeAlertsButton() {
        omnipod_button_acknowledge_active_alerts.isEnabled = podStateManager.isPodActivationCompleted && podStateManager.hasActiveAlerts()
            && !podStateManager.isPodDead && rileyLinkServiceData.rileyLinkServiceState.isReady && isQueueEmpty()
    }

    private fun updateSuspendDeliveryButton() {
        // If the Pod is currently suspended, we show the Resume delivery button instead.
        if (omnipodManager.isSuspendDeliveryButtonEnabled && !(podStateManager.isPodRunning && podStateManager.isSuspended)) {
            omnipod_button_suspend_delivery.visibility = View.VISIBLE
            omnipod_button_suspend_delivery.isEnabled = podStateManager.isPodRunning && !podStateManager.isSuspended && rileyLinkServiceData.rileyLinkServiceState.isReady && isQueueEmpty()
        } else {
            omnipod_button_suspend_delivery.visibility = View.GONE
        }
    }

    private fun updatePulseLogButton() {
        if (omnipodManager.isPulseLogButtonEnabled) {
            omnipod_button_pulse_log.visibility = View.VISIBLE
            omnipod_button_pulse_log.isEnabled = podStateManager.isPodActivationCompleted && rileyLinkServiceData.rileyLinkServiceState.isReady && isQueueEmpty()
        } else {
            omnipod_button_pulse_log.visibility = View.GONE
        }
    }

    private fun displayNotConfiguredDialog() {
        context?.let {
            OKDialog.show(it, resourceHelper.gs(R.string.omnipod_warning),
                resourceHelper.gs(R.string.omnipod_error_operation_not_possible_no_configuration), null)
        }
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
        return Duration.standardMinutes(sp.getInt(resourceHelper.gs(R.string.key_pump_unreachable_threshold_minutes), Constants.DEFAULT_PUMP_UNREACHABLE_THRESHOLD_MINUTES).toLong())
    }

}

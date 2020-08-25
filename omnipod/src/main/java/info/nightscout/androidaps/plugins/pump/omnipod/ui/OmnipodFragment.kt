package info.nightscout.androidaps.plugins.pump.omnipod.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
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
    private val REFRESH_INTERVAL_MILLIS = 15 * 1000L; // 15 seconds

    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var omnipodPumpPlugin: OmnipodPumpPlugin
    @Inject lateinit var warnColors: WarnColors
    @Inject lateinit var podStateManager: PodStateManager
    @Inject lateinit var sp: SP
    @Inject lateinit var aapsOmnipodUtil: AapsOmnipodUtil
    @Inject lateinit var rileyLinkServiceData: RileyLinkServiceData
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsOmnipodManager: AapsOmnipodManager
    @Inject lateinit var protectionCheck: ProtectionCheck

    // TODO somehow obtain the pumpUnreachableThreshold in order to display last connection time red or white
    // @Inject lateinit var localAlertUtils: LocalAlertUtils

    private var disposables: CompositeDisposable = CompositeDisposable()

    private val loopHandler = Handler()
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

        omnipod_resume_delivery.setOnClickListener {
            disablePodActionButtons()
            commandQueue.startPump(null)
        }

        omnipod_pod_mgmt.setOnClickListener {
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

        omnipod_refresh.setOnClickListener {
            disablePodActionButtons()
            omnipodPumpPlugin.addPodStatusRequest(OmnipodStatusRequestType.GetPodState);
            commandQueue.readStatus("Clicked Refresh", null)
        }

        omnipod_rileylink_stats.setOnClickListener {
            if (omnipodPumpPlugin.rileyLinkService?.verifyConfiguration() == true) {
                startActivity(Intent(context, RileyLinkStatusActivity::class.java))
            } else {
                displayNotConfiguredDialog()
            }
        }

        omnipod_pod_active_alerts_ack.setOnClickListener {
            if (omnipodPumpPlugin.rileyLinkService?.verifyConfiguration() != true) {
                displayNotConfiguredDialog()
            } else {
                disablePodActionButtons()
                omnipodPumpPlugin.addPodStatusRequest(OmnipodStatusRequestType.AcknowledgeAlerts);
                commandQueue.readStatus("Clicked Alert Ack", null)
            }
        }

        omnipod_pod_debug.setOnClickListener {
            if (omnipodPumpPlugin.rileyLinkService?.verifyConfiguration() != true) {
                displayNotConfiguredDialog()
            } else {
                disablePodActionButtons()
                omnipodPumpPlugin.addPodStatusRequest(OmnipodStatusRequestType.GetPodPulseLog);
                commandQueue.readStatus("Clicked Pulse Log", null)
            }
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
                updateOmipodStatus()
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
        updateOmipodStatus()
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

    private fun updateOmipodStatus() {
        updateLastConnection()
        updatePodStatus()

        val errors = ArrayList<String>();
        val rileyLinkErrorDescription = omnipodPumpPlugin.rileyLinkService.errorDescription
        if (StringUtils.isNotEmpty(rileyLinkErrorDescription)) {
            errors.add(rileyLinkErrorDescription)
        }

        if (!podStateManager.hasPodState() || !podStateManager.isPodInitialized) {
            if (podStateManager.hasPodState()) {
                omnipod_pod_address.text = podStateManager.address.toString()
            } else {
                omnipod_pod_address.text = "-"
            }
            omnipod_pod_lot.text = "-"
            omnipod_pod_tid.text = "-"
            omnipod_pod_firmware_version.text = "-"
            omnipod_pod_expiry.text = "-"
            omnipod_base_basal_rate.text = "-"
            omnipod_total_delivered.text = "-"
            omnipod_reservoir.text = "-"
            omnipod_tempbasal.text = "-"
            omnipod_lastbolus.text = "-"
            omnipod_pod_active_alerts.text = "-"
        } else {
            omnipod_pod_address.text = podStateManager.address.toString()
            omnipod_pod_lot.text = podStateManager.lot.toString()
            omnipod_pod_tid.text = podStateManager.tid.toString()
            omnipod_pod_firmware_version.text = resourceHelper.gs(R.string.omnipod_pod_firmware_version_value, podStateManager.pmVersion.toString(), podStateManager.piVersion.toString())
            val expiresAt = podStateManager.expiresAt
            omnipod_pod_expiry.text = if (expiresAt == null) "???" else dateUtil.dateAndTimeString(expiresAt.toDate())

            if (podStateManager.hasFaultEvent()) {
                val faultEventCode = podStateManager.faultEvent.faultEventCode
                errors.add(resourceHelper.gs(R.string.omnipod_pod_status_pod_fault_description, faultEventCode.value, faultEventCode.name))
            }

            // last bolus
            if (podStateManager.lastBolusStartTime != null && podStateManager.lastBolusAmount != null) {
                val ago = readableDuration(podStateManager.lastBolusStartTime)
                omnipod_lastbolus.text = resourceHelper.gs(R.string.omnipod_last_bolus, omnipodPumpPlugin.model().determineCorrectBolusSize(podStateManager.lastBolusAmount), resourceHelper.gs(R.string.insulin_unit_shortname), ago)
            } else {
                omnipod_lastbolus.text = "-"
            }

            val now = DateTime.now()

            // base basal rate
            omnipod_base_basal_rate.text = resourceHelper.gs(R.string.pump_basebasalrate, omnipodPumpPlugin.model().determineCorrectBasalSize(podStateManager.basalSchedule.rateAt(Duration(now.withTimeAtStartOfDay(), now))))

            omnipod_tempbasal.text = activePlugin.activeTreatments
                .getTempBasalFromHistory(System.currentTimeMillis())?.toStringFull() ?: "-"

            // total delivered
            omnipod_total_delivered.text = if (podStateManager.isPodActivationCompleted && podStateManager.totalInsulinDelivered != null) { // Null check for backwards compatibility
                resourceHelper.gs(R.string.omnipod_total_delivered, podStateManager.totalInsulinDelivered - OmnipodConstants.POD_SETUP_UNITS);
            } else {
                "-"
            }

            // reservoir
            if (podStateManager.reservoirLevel == null) {
                omnipod_reservoir.text = resourceHelper.gs(R.string.omnipod_reservoir_over50)
                omnipod_reservoir.setTextColor(Color.WHITE)
            } else {
                omnipod_reservoir.text = resourceHelper.gs(R.string.omnipod_reservoir_left, podStateManager.reservoirLevel)
                warnColors.setColorInverse(omnipod_reservoir, podStateManager.reservoirLevel, 50.0, 20.0)
            }

            omnipod_pod_active_alerts.text = if (podStateManager.hasActiveAlerts()) TextUtils.join(System.lineSeparator(), aapsOmnipodUtil.getTranslatedActiveAlerts(podStateManager)) else "-"
        }

        if (errors.size == 0) {
            omnipod_errors.text = "-"
            omnipod_errors.setTextColor(Color.WHITE)
        } else {
            omnipod_errors.text = StringUtils.join(errors, System.lineSeparator())
            omnipod_errors.setTextColor(Color.RED)
        }
    }

    private fun updateLastConnection() {
        if (podStateManager.isPodInitialized && podStateManager.lastSuccessfulCommunication != null) { // Null check for backwards compatibility
            omnipod_lastconnection.text = readableDuration(podStateManager.lastSuccessfulCommunication)
            omnipod_lastconnection.setTextColor(Color.WHITE)
            /*
            // TODO
            if (omnipodPumpPlugin.isUnreachableAlertTimeoutExceeded(localAlertUtils.pumpUnreachableThreshold())) {
                omnipod_lastconnection.setTextColor(Color.RED)
            } else {
                omnipod_lastconnection.setTextColor(Color.WHITE)
            }
             */
        } else {
            omnipod_lastconnection.setTextColor(Color.WHITE)
            if (podStateManager.hasPodState() && podStateManager.lastSuccessfulCommunication != null) {
                omnipod_lastconnection.text = readableDuration(podStateManager.lastSuccessfulCommunication)
            } else {
                omnipod_lastconnection.text = "-"
            }
        }
    }

    private fun updatePodStatus() {
        if (!podStateManager.hasPodState()) {
            omnipod_pod_status.text = resourceHelper.gs(R.string.omnipod_pod_status_no_active_pod)
        } else if (!podStateManager.isPodActivationCompleted) {
            if (!podStateManager.isPodInitialized) {
                omnipod_pod_status.text = resourceHelper.gs(R.string.omnipod_pod_status_waiting_for_pair_and_prime)
            } else {
                if (PodProgressStatus.ACTIVATION_TIME_EXCEEDED == podStateManager.podProgressStatus) {
                    omnipod_pod_status.text = resourceHelper.gs(R.string.omnipod_pod_status_activation_time_exceeded)
                } else if (podStateManager.podProgressStatus.isBefore(PodProgressStatus.PRIMING_COMPLETED)) {
                    omnipod_pod_status.text = resourceHelper.gs(R.string.omnipod_pod_status_waiting_for_pair_and_prime)
                } else {
                    omnipod_pod_status.text = resourceHelper.gs(R.string.omnipod_pod_status_waiting_for_cannula_insertion)
                }
            }
        } else {
            if (podStateManager.podProgressStatus.isRunning) {
                if (podStateManager.isSuspended) {
                    omnipod_pod_status.text = resourceHelper.gs(R.string.omnipod_pod_status_suspended)
                } else {
                    omnipod_pod_status.text = resourceHelper.gs(R.string.omnipod_pod_status_running)
                }
            } else if (podStateManager.podProgressStatus == PodProgressStatus.FAULT_EVENT_OCCURRED) {
                omnipod_pod_status.text = resourceHelper.gs(R.string.omnipod_pod_status_pod_fault)
            } else if (podStateManager.podProgressStatus == PodProgressStatus.INACTIVE) {
                omnipod_pod_status.text = resourceHelper.gs(R.string.omnipod_pod_status_inactive)
            } else {
                omnipod_pod_status.text = podStateManager.podProgressStatus.toString()
            }
        }

        if (!podStateManager.isPodActivationCompleted || podStateManager.isPodDead || podStateManager.isSuspended) {
            omnipod_pod_status.setTextColor(Color.RED)
        } else {
            omnipod_pod_status.setTextColor(Color.WHITE)
        }
    }

    private fun updateQueueStatus() {
        val status = commandQueue.spannedStatus()
        if (status.toString() == "") {
            omnipod_queue.visibility = View.GONE
        } else {
            omnipod_queue.visibility = View.VISIBLE
            omnipod_queue.text = status
        }
    }

    private fun updatePodActionButtons() {
        updateRefreshStatusButton()
        updateAcknowledgeAlertsButton()
        updatePulseLogButton()
        updateResumeDeliveryButton()
    }

    private fun disablePodActionButtons() {
        omnipod_pod_active_alerts_ack.isEnabled = false
        omnipod_refresh.isEnabled = false
        omnipod_pod_debug.isEnabled = false
        omnipod_resume_delivery.isEnabled = false
    }

    private fun updateRefreshStatusButton() {
        omnipod_refresh.isEnabled = podStateManager.isPodInitialized && podStateManager.podProgressStatus.isAtLeast(PodProgressStatus.PAIRING_COMPLETED)
            && rileyLinkServiceData.rileyLinkServiceState.isReady && isQueueEmpty()
    }

    private fun updateAcknowledgeAlertsButton() {
        if (podStateManager.isPodInitialized && podStateManager.hasActiveAlerts() && !podStateManager.isPodDead) {
            omnipod_pod_active_alerts_ack.isEnabled = rileyLinkServiceData.rileyLinkServiceState.isReady && isQueueEmpty()
        } else {
            omnipod_pod_active_alerts_ack.isEnabled = false
        }
    }

    private fun updatePulseLogButton() {
        if (aapsOmnipodManager.isPodDebuggingOptionsEnabled) {
            omnipod_pod_debug.visibility = View.VISIBLE
            omnipod_pod_debug.isEnabled = podStateManager.isPodActivationCompleted && rileyLinkServiceData.rileyLinkServiceState.isReady && isQueueEmpty()
        } else {
            omnipod_pod_debug.visibility = View.GONE
        }
    }

    private fun updateResumeDeliveryButton() {
        val queueEmptyOrStartingPump = isQueueEmpty() || commandQueue.isRunning(Command.CommandType.START_PUMP)
        if (podStateManager.isPodActivationCompleted && podStateManager.isSuspended && queueEmptyOrStartingPump) {
            omnipod_resume_delivery.visibility = View.VISIBLE
            omnipod_resume_delivery.isEnabled = rileyLinkServiceData.rileyLinkServiceState.isReady && isQueueEmpty()
        } else {
            omnipod_resume_delivery.visibility = View.GONE
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

}

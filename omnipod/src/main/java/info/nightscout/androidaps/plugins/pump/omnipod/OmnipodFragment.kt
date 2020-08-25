package info.nightscout.androidaps.plugins.pump.omnipod

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
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.dialog.RileyLinkStatusActivity
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodStatusRequest
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodProgressStatus
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.PodManagementActivity
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodPumpStatus
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodAcknowledgeAlertsChanged
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodPumpValuesChanged
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodRefreshButtonState
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
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

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var omnipodPumpPlugin: OmnipodPumpPlugin
    @Inject lateinit var warnColors: WarnColors
    @Inject lateinit var omnipodPumpStatus: OmnipodPumpStatus
    @Inject lateinit var podStateManager: PodStateManager
    @Inject lateinit var sp: SP
    @Inject lateinit var omnipodUtil: OmnipodUtil
    @Inject lateinit var rileyLinkServiceData: RileyLinkServiceData
    @Inject lateinit var dateUtil: DateUtil

    // TODO somehow obtain the pumpUnreachableThreshold in order to display last connection time red or white
    // @Inject lateinit var localAlertUtils: LocalAlertUtils
    @Inject lateinit var protectionCheck: ProtectionCheck

    private var disposables: CompositeDisposable = CompositeDisposable()

    private val loopHandler = Handler()
    private lateinit var refreshLoop: Runnable

    init {
        refreshLoop = Runnable {
            activity?.runOnUiThread { updateUi() }
            loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.omnipod_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
            if (omnipodPumpPlugin.rileyLinkService?.verifyConfiguration() != true) {
                displayNotConfiguredDialog()
            } else {
                omnipod_refresh.isEnabled = false
                omnipodPumpPlugin.addPodStatusRequest(OmnipodStatusRequest.GetPodState);
                commandQueue.readStatus("Clicked Refresh", object : Callback() {
                    override fun run() {
                        activity?.runOnUiThread { omnipod_refresh.isEnabled = true }
                    }
                })
            }
        }

        omnipod_stats.setOnClickListener {
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
                omnipod_pod_active_alerts_ack.isEnabled = false
                omnipodPumpPlugin.addPodStatusRequest(OmnipodStatusRequest.AcknowledgeAlerts);
                commandQueue.readStatus("Clicked Alert Ack", null)
            }
        }

        omnipod_pod_debug.setOnClickListener {
            if (omnipodPumpPlugin.rileyLinkService?.verifyConfiguration() != true) {
                displayNotConfiguredDialog()
            } else {
                omnipod_pod_debug.isEnabled = false
                omnipodPumpPlugin.addPodStatusRequest(OmnipodStatusRequest.GetPodPulseLog);
                commandQueue.readStatus("Clicked Refresh", object : Callback() {
                    override fun run() {
                        activity?.runOnUiThread { omnipod_pod_debug.isEnabled = true }
                    }
                })
            }
        }

        omnipod_lastconnection.setTextColor(Color.WHITE)
    }

    override fun onResume() {
        super.onResume()
        loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())
        disposables += rxBus
            .toObservable(EventOmnipodRefreshButtonState::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ omnipod_refresh.isEnabled = it.newState }, { fabricPrivacy.logException(it) })
        disposables += rxBus
            .toObservable(EventRileyLinkDeviceStatusChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateRileyLinkUiElements() }, { fabricPrivacy.logException(it) })
        disposables += rxBus
            .toObservable(EventOmnipodPumpValuesChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateOmipodUiElements() }, { fabricPrivacy.logException(it) })
        disposables += rxBus
            .toObservable(EventOmnipodAcknowledgeAlertsChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateAcknowledgeAlertsUiElements() }, { fabricPrivacy.logException(it) })
        disposables += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({
                setVisibilityOfPodDebugButton()
            }, { fabricPrivacy.logException(it) })
        updateUi()
    }

    fun setVisibilityOfPodDebugButton() {
        val isEnabled = sp.getBoolean(OmnipodConst.Prefs.PodDebuggingOptionsEnabled, false)

        if (isEnabled)
            omnipod_pod_debug.visibility = View.VISIBLE
        else
            omnipod_pod_debug.visibility = View.GONE
    }

    private fun displayNotConfiguredDialog() {
        context?.let {
            OKDialog.show(it, resourceHelper.gs(R.string.omnipod_warning),
                resourceHelper.gs(R.string.omnipod_error_operation_not_possible_no_configuration), null)
        }
    }

    override fun onPause() {
        super.onPause()
        disposables.clear()
        loopHandler.removeCallbacks(refreshLoop)
    }

    fun updateUi() {
        updateRileyLinkUiElements()
        updateOmipodUiElements()
    }

    @Synchronized
    private fun updateRileyLinkUiElements() {
        aapsLogger.info(LTag.PUMP, "OmnipodFragment.setDeviceStatus")

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

    fun updateOmipodUiElements() {
        updateLastConnectionUiElements()
        updateAcknowledgeAlertsUiElements()
        setVisibilityOfPodDebugButton()

        val errors = ArrayList<String>();
        val rileyLinkErrorInfo = omnipodPumpStatus.errorInfo
        if (rileyLinkErrorInfo != null) {
            errors.add(rileyLinkErrorInfo)
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
            omnipod_basabasalrate.text = "-"
            omnipod_reservoir.text = "-"
            omnipod_tempbasal.text = "-"
            omnipod_lastbolus.text = "-"
            omnipod_lastconnection.setTextColor(Color.WHITE)
            if (podStateManager.hasPodState()) {
                omnipod_pod_status.text = resourceHelper.gs(R.string.omnipod_pod_status_not_initalized)
            } else {
                omnipod_pod_status.text = resourceHelper.gs(R.string.omnipod_pod_status_no_pod_connected)
            }
        } else {
            omnipod_pod_address.text = podStateManager.address.toString()
            omnipod_pod_lot.text = podStateManager.lot.toString()
            omnipod_pod_tid.text = podStateManager.tid.toString()
            omnipod_pod_firmware_version.text = resourceHelper.gs(R.string.omnipod_pod_firmware_version_value, podStateManager.pmVersion.toString(), podStateManager.piVersion.toString())
            val expiresAt = podStateManager.expiresAt
            omnipod_pod_expiry.text = if (expiresAt == null) "???" else dateUtil.dateAndTimeString(expiresAt.toDate())

            val stateText: String
            when {
                podStateManager.hasFaultEvent() -> {
                    val faultEventCode = podStateManager.faultEvent.faultEventCode
                    stateText = resourceHelper.gs(R.string.omnipod_pod_status_pod_fault)
                    errors.add(resourceHelper.gs(R.string.omnipod_pod_status_pod_fault_description, faultEventCode.value, faultEventCode.name))
                }

                podStateManager.isPodRunning    -> {
                    stateText = resourceHelper.gs(R.string.omnipod_pod_status_pod_running, if (podStateManager.lastDeliveryStatus == null) null else podStateManager.lastDeliveryStatus.name)
                }

                else                            -> {
                    stateText = resourceHelper.gs(R.string.omnipod_pod_setup_in_progress, podStateManager.podProgressStatus.name)
                }
            }
            omnipod_pod_status.text = stateText

            updateLastConnectionUiElements()

            // last bolus
            if (podStateManager.lastBolusStartTime != null && podStateManager.lastBolusAmount != null) {
                val ago = readableDuration(podStateManager.lastBolusStartTime)
                omnipod_lastbolus.text = resourceHelper.gs(R.string.omnipod_last_bolus, omnipodPumpPlugin.pumpType.determineCorrectBolusSize(podStateManager.lastBolusAmount), resourceHelper.gs(R.string.insulin_unit_shortname), ago)
            } else {
                omnipod_lastbolus.text = "-"
            }

            // base basal rate
            omnipod_basabasalrate.text = resourceHelper.gs(R.string.pump_basebasalrate, omnipodPumpPlugin.pumpType.determineCorrectBasalSize(omnipodPumpPlugin.baseBasalRate))

            omnipod_tempbasal.text = activePlugin.activeTreatments
                .getTempBasalFromHistory(System.currentTimeMillis())?.toStringFull() ?: "-"

            // reservoir
            if (podStateManager.reservoirLevel == null) {
                omnipod_reservoir.text = resourceHelper.gs(R.string.omnipod_reservoir_over50)
                omnipod_reservoir.setTextColor(Color.WHITE)
            } else {
                omnipod_reservoir.text = resourceHelper.gs(R.string.omnipod_reservoir_left, podStateManager.reservoirLevel)
                warnColors.setColorInverse(omnipod_reservoir, podStateManager.reservoirLevel, 50.0, 20.0)
            }
        }

        omnipod_pod_status.setTextColor(if (podStateManager.hasFaultEvent()) Color.RED else Color.WHITE)
        omnipod_errors.text = if (errors.size == 0) "-" else StringUtils.join(errors, System.lineSeparator())

        val status = commandQueue.spannedStatus()
        if (status.toString() == "") {
            omnipod_queue.visibility = View.GONE
        } else {
            omnipod_queue.visibility = View.VISIBLE
            omnipod_queue.text = status
        }

        omnipod_refresh.isEnabled = podStateManager.isPodInitialized && podStateManager.podProgressStatus.isAtLeast(PodProgressStatus.PAIRING_COMPLETED)
    }

    private fun updateLastConnectionUiElements() {
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

    private fun updateAcknowledgeAlertsUiElements() {
        if (podStateManager.isPodInitialized && podStateManager.hasActiveAlerts()) {
            omnipod_pod_active_alerts_ack.isEnabled = true
            omnipod_pod_active_alerts.text = TextUtils.join(System.lineSeparator(), omnipodUtil.getTranslatedActiveAlerts(podStateManager))
        } else {
            omnipod_pod_active_alerts_ack.isEnabled = false
            omnipod_pod_active_alerts.text = "-"
        }
    }

    private fun readableDuration(dateTime: DateTime): String {
        val minutes = Duration(dateTime, DateTime.now()).standardMinutes.toInt()
        when {
            minutes == 0   -> {
                return resourceHelper.gs(R.string.omnipod_moments_ago)
            }

            minutes < 60   -> {
                return resourceHelper.gs(R.string.omnipod_time_ago, resourceHelper.gq(R.plurals.omnipod_minutes, minutes, minutes))
            }

            minutes < 1440 -> {
                val hours = minutes / 60
                val minutesLeft = minutes % 60
                if (minutesLeft > 0)
                    return resourceHelper.gs(R.string.omnipod_time_ago,
                        resourceHelper.gs(R.string.omnipod_composite_time, resourceHelper.gq(R.plurals.omnipod_hours, hours, hours), resourceHelper.gq(R.plurals.omnipod_minutes, minutesLeft, minutesLeft)))
                return resourceHelper.gs(R.string.omnipod_time_ago, resourceHelper.gq(R.plurals.omnipod_hours, hours, hours))
            }

            else           -> {
                val hours = minutes / 60
                val days = hours / 24
                val hoursLeft = hours % 24
                if (hoursLeft > 0)
                    return resourceHelper.gs(R.string.omnipod_time_ago,
                        resourceHelper.gs(R.string.omnipod_composite_time, resourceHelper.gq(R.plurals.omnipod_days, days, days), resourceHelper.gq(R.plurals.omnipod_hours, hoursLeft, hoursLeft)))
                return resourceHelper.gs(R.string.omnipod_time_ago, resourceHelper.gq(R.plurals.omnipod_days, days, days))
            }
        }
    }

}

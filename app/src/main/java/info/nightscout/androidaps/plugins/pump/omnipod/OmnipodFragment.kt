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
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.dialog.RileyLinkStatusActivity
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodStatusRequest
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.PodManagementActivity
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodPumpStatus
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodAcknowledgeAlertsChanged
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodDeviceStatusChange
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodPumpValuesChanged
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodRefreshButtonState
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.LocalAlertUtils
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.WarnColors
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.omnipod_fragment.*
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import org.joda.time.Duration
import javax.inject.Inject

class OmnipodFragment : DaggerFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var mainApp: MainApp
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
    @Inject lateinit var localAlertUtils: LocalAlertUtils

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val loopHandler = Handler()
    private lateinit var refreshLoop: Runnable

    operator fun CompositeDisposable.plusAssign(disposable: Disposable) {
        add(disposable)
    }

    init {
        refreshLoop = Runnable {
            activity?.runOnUiThread { updateGUI() }
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
                startActivity(Intent(context, PodManagementActivity::class.java))
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
        disposable += rxBus
            .toObservable(EventOmnipodRefreshButtonState::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ omnipod_refresh.isEnabled = it.newState }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventOmnipodDeviceStatusChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                aapsLogger.info(LTag.PUMP, "onStatusEvent(EventOmnipodDeviceStatusChange): {}", it)
                setDeviceStatus()
            }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventOmnipodPumpValuesChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGUI() }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventOmnipodAcknowledgeAlertsChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateAcknowledgeAlerts() }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({
                setVisibilityOfPodDebugButton()
            }, { fabricPrivacy.logException(it) })
        updateGUI()
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
        disposable.clear()
        loopHandler.removeCallbacks(refreshLoop)
    }

    @Synchronized
    private fun setDeviceStatus() {
        aapsLogger.info(LTag.PUMP, "OmnipodFragment.setDeviceStatus")

        val errors = ArrayList<String>();

        val rileyLinkServiceState = rileyLinkServiceData.rileyLinkServiceState

        val resourceId = rileyLinkServiceState.getResourceId(RileyLinkTargetDevice.Omnipod)
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
            omnipod_pod_fw_version.text = "-"
            omnipod_pod_expiry.text = "-"
            if (podStateManager.hasPodState()) {
                omnipod_pod_status.text = resourceHelper.gs(R.string.omnipod_pod_status_not_initalized)
            } else {
                omnipod_pod_status.text = resourceHelper.gs(R.string.omnipod_pod_status_no_pod_connected)
            }
        } else {
            omnipod_pod_address.text = podStateManager.address.toString()
            omnipod_pod_lot.text = podStateManager.lot.toString()
            omnipod_pod_tid.text = podStateManager.tid.toString()
            omnipod_pod_fw_version.text = podStateManager.pmVersion.toString() + " / " + podStateManager.piVersion.toString()
            omnipod_pod_expiry.text = podStateManager.expiryDateAsString

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
    }

    // GUI functions
    fun updateGUI() {
        setDeviceStatus()

        if (podStateManager.isPodRunning) {
            updateLastConnection()

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
                .getTempBasalFromHistory(System.currentTimeMillis())?.toStringFull() ?: ""

            // reservoir
            if (podStateManager.reservoirLevel == null) {
                omnipod_reservoir.text = resourceHelper.gs(R.string.omnipod_reservoir_over50)
                omnipod_reservoir.setTextColor(Color.WHITE)
            } else {
                omnipod_reservoir.text = resourceHelper.gs(R.string.omnipod_reservoir_left, podStateManager.reservoirLevel)
                warnColors.setColorInverse(omnipod_reservoir, podStateManager.reservoirLevel, 50.0, 20.0)
            }
        } else {
            updateLastConnection()
            omnipod_basabasalrate.text = "-"
            omnipod_reservoir.text = "-"
            omnipod_tempbasal.text = "-"
            omnipod_lastbolus.text = "-"
            omnipod_lastconnection.setTextColor(Color.WHITE)
        }

        updateAcknowledgeAlerts()

        setVisibilityOfPodDebugButton()

        omnipod_refresh.isEnabled = podStateManager.isPodInitialized
    }

    private fun updateLastConnection() {
        if (podStateManager.isPodRunning && podStateManager.lastSuccessfulCommunication != null) { // Null check for backwards compatibility
            omnipod_lastconnection.text = readableDuration(podStateManager.lastSuccessfulCommunication)
            if (omnipodPumpPlugin.isUnreachableAlertTimeoutExceeded(localAlertUtils.pumpUnreachableThreshold())) {
                omnipod_lastconnection.setTextColor(Color.RED)
            } else {
                omnipod_lastconnection.setTextColor(Color.WHITE)
            }
        } else {
            omnipod_lastconnection.setTextColor(Color.WHITE)
            if (podStateManager.hasPodState() && podStateManager.lastSuccessfulCommunication != null) {
                omnipod_lastconnection.text = readableDuration(podStateManager.lastSuccessfulCommunication)
            } else {
                omnipod_lastconnection.text = "-"
            }
        }
    }

    private fun readableDuration(dateTime: DateTime): String {
        val min = Duration(dateTime, DateTime.now()).standardMinutes
        when {
            min == 0L  -> {
                return resourceHelper.gs(R.string.omnipod_connected_now)
            }

            min < 60   -> {
                return resourceHelper.gs(R.string.minago, min)
            }

            min < 1440 -> {
                val h = (min / 60).toInt()
                return resourceHelper.gq(R.plurals.objective_hours, h, h) + " " + resourceHelper.gs(R.string.ago)
            }

            else       -> {
                val h = (min / 60).toInt()
                val d = h / 24
                return resourceHelper.gq(R.plurals.objective_days, d, d) + " " + resourceHelper.gs(R.string.ago)
            }
        }
    }

    private fun updateAcknowledgeAlerts() {
        if (podStateManager.hasPodState() && podStateManager.hasActiveAlerts()) {
            omnipod_pod_active_alerts_ack.isEnabled = true
            omnipod_pod_active_alerts.text = TextUtils.join(System.lineSeparator(), omnipodUtil.getTranslatedActiveAlerts(podStateManager))
        } else {
            omnipod_pod_active_alerts_ack.isEnabled = false
            omnipod_pod_active_alerts.text = "-"
        }
    }

}

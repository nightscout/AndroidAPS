package info.nightscout.androidaps.plugins.pump.omnipod

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
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
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
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
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.Round
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

        omnipod_rl_status.text = resourceHelper.gs(RileyLinkServiceState.NotStarted.getResourceId(RileyLinkTargetDevice.Omnipod))

        omnipod_pod_status.setTextColor(Color.WHITE)
        omnipod_pod_status.text = "{fa-bed}"

        omnipod_pod_mgmt.setOnClickListener {
            if (omnipodPumpPlugin.rileyLinkService?.verifyConfiguration() == true) {
                startActivity(Intent(context, PodManagementActivity::class.java))
            } else {
                displayNotConfiguredDialog()
            }
        }

        omnipod_refresh.setOnClickListener {
            if (omnipodPumpPlugin.rileyLinkService?.verifyConfiguration() != true) {
                OmnipodUtil.displayNotConfiguredDialog(context)
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

        setVisibilityOfPodDebugButton()

        updateGUI()
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
            OKDialog.show(it, resourceHelper.gs(R.string.combo_warning),
                resourceHelper.gs(R.string.omnipod_error_operation_not_possible_no_configuration), null)
        }
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
        loopHandler.removeCallbacks(refreshLoop)
    }

    @Synchronized
    private fun setDeviceStatus(event: EventOmnipodDeviceStatusChange) {

    }

    @Synchronized
    private fun setDeviceStatus() {
        //val omnipodPumpStatus: OmnipodPumpStatus = OmnipodUtil.getPumpStatus()
        // omnipodPumpStatus.rileyLinkServiceState = checkStatusSet(omnipodPumpStatus.rileyLinkServiceState,
        //         RileyLinkUtil.getServiceState()) as RileyLinkServiceState?

        aapsLogger.info(LTag.PUMP, "setDeviceStatus: [pumpStatus={}]", omnipodPumpStatus)

        val resourceId = rileyLinkServiceData.rileyLinkServiceState.getResourceId(RileyLinkTargetDevice.Omnipod)
        val rileyLinkError = rileyLinkServiceData.rileyLinkError

        omnipod_rl_status.text =
            when {
                omnipodPumpStatus.rileyLinkServiceState == RileyLinkServiceState.NotStarted -> resourceHelper.gs(resourceId)
                omnipodPumpStatus.rileyLinkServiceState.isConnecting                        -> "{fa-bluetooth-b spin}   " + resourceHelper.gs(resourceId)
                omnipodPumpStatus.rileyLinkServiceState.isError && rileyLinkError == null   -> "{fa-bluetooth-b}   " + resourceHelper.gs(resourceId)
                omnipodPumpStatus.rileyLinkServiceState.isError && rileyLinkError != null   -> "{fa-bluetooth-b}   " + resourceHelper.gs(rileyLinkError.getResourceId(RileyLinkTargetDevice.MedtronicPump))
                else                                                                        -> "{fa-bluetooth-b}   " + resourceHelper.gs(resourceId)
            }
        omnipod_rl_status.setTextColor(if (rileyLinkError != null) Color.RED else Color.WHITE)

        // omnipodPumpStatus.rileyLinkError = checkStatusSet(omnipodPumpStatus.rileyLinkError,
        //     RileyLinkUtil.getError()) as RileyLinkError?

        omnipod_errors.text =
            omnipodPumpStatus.rileyLinkError?.let {
                resourceHelper.gs(it.getResourceId(RileyLinkTargetDevice.Omnipod))
            } ?: "-"

        val driverState = omnipodUtil.getDriverState();

        aapsLogger.info(LTag.PUMP, "getDriverState: [driverState={}]", driverState)

        if (!podStateManager.hasState() || !podStateManager.isPaired) {
            if (podStateManager.hasState()) {
                omnipod_pod_address.text = podStateManager.address.toString()
            } else {
                omnipod_pod_address.text = "-"
            }
            omnipod_pod_lot.text = "-"
            omnipod_pod_tid.text = "-"
            omnipod_pod_fw_version.text = "-"
            omnipod_pod_expiry.text = "-"
            if (podStateManager.hasState()) {
                omnipod_pod_status.text = resourceHelper.gs(R.string.omnipod_pod_status_not_initalized)
            } else {
                omnipod_pod_status.text = resourceHelper.gs(R.string.omnipod_pod_status_no_pod_connected)
            }
            omnipodPumpStatus.podAvailable = false
            omnipodPumpStatus.podNumber == null
        } else {
            omnipodPumpStatus.podLotNumber = "" + podStateManager.lot
            omnipodPumpStatus.podAvailable = podStateManager.isSetupCompleted
            omnipod_pod_address.text = podStateManager.address.toString()
            omnipod_pod_lot.text = podStateManager.lot.toString()
            omnipod_pod_tid.text = podStateManager.tid.toString()
            omnipod_pod_fw_version.text = podStateManager.pmVersion.toString() + " / " + podStateManager.piVersion.toString()
            omnipod_pod_expiry.text = podStateManager.expiryDateAsString
            omnipodPumpStatus.podNumber = podStateManager.address.toString()

            var podDeviceState = omnipodPumpStatus.podDeviceState

            var stateText: String?

            if(podStateManager.hasFaultEvent()) {
                val faultEventCode = podStateManager.faultEvent.faultEventCode
                stateText = resourceHelper.gs(R.string.omnipod_pod_status_pod_fault) + " ("+ faultEventCode.value +" "+ faultEventCode.name +")"
            } else if (podStateManager.isSetupCompleted) {
                stateText = resourceHelper.gs(R.string.omnipod_pod_status_pod_running)
                if (podStateManager.lastDeliveryStatus != null) {
                    stateText += " (last delivery status: " + podStateManager.lastDeliveryStatus.name + ")"
                }
            } else {
                stateText = resourceHelper.gs(R.string.omnipod_pod_setup_in_progress)
                stateText += " (setup progress: " + podStateManager.setupProgress.name + ")"
            }

            omnipod_pod_status.text = stateText
        }

        val status = commandQueue.spannedStatus()
        if (status.toString() == "") {
            omnipod_queue.visibility = View.GONE
        } else {
            omnipod_queue.visibility = View.VISIBLE
            omnipod_queue.text = status
        }
    }

    private fun checkStatusSet(object1: Any?, object2: Any?): Any? {
        return if (object1 == null) {
            object2
        } else {
            if (object1 != object2) {
                object2
            } else
                object1
        }
    }

    // GUI functions
    fun updateGUI() {
        val plugin = omnipodPumpPlugin
        //val omnipodPumpStatus = OmnipodUtil.getPumpStatus()
        var pumpType = omnipodPumpStatus.pumpType

        if (pumpType == null) {
            aapsLogger.warn(LTag.PUMP, "PumpType was not set, reseting to Omnipod.")
            pumpType = PumpType.Insulet_Omnipod;
        }

        setDeviceStatus()

        if (omnipodPumpStatus.podAvailable) {
            // last connection
            if (omnipodPumpStatus.lastConnection != 0L) {
                //val minAgo = DateUtil.minAgo(pumpStatus.lastConnection)
                val min = (System.currentTimeMillis() - omnipodPumpStatus.lastConnection) / 1000 / 60
                if (omnipodPumpStatus.lastConnection + 60 * 1000 > System.currentTimeMillis()) {
                    omnipod_lastconnection.setText(R.string.combo_pump_connected_now)
                    //omnipod_lastconnection.setTextColor(Color.WHITE)
                } else { //if (pumpStatus.lastConnection + 30 * 60 * 1000 < System.currentTimeMillis()) {

                    if (min < 60) {
                        omnipod_lastconnection.text = resourceHelper.gs(R.string.minago, min)
                    } else if (min < 1440) {
                        val h = (min / 60).toInt()
                        omnipod_lastconnection.text = (resourceHelper.gq(R.plurals.objective_hours, h, h) + " "
                            + resourceHelper.gs(R.string.ago))
                    } else {
                        val h = (min / 60).toInt()
                        val d = h / 24
                        // h = h - (d * 24);
                        omnipod_lastconnection.text = (resourceHelper.gq(R.plurals.objective_days, d, d) + " "
                            + resourceHelper.gs(R.string.ago))
                    }
                    //omnipod_lastconnection.setTextColor(Color.RED)
                }
//                } else {
//                    omnipod_lastconnection.text = minAgo
//                    //omnipod_lastconnection.setTextColor(Color.WHITE)
//                }
            }

            // last bolus
            val bolus = omnipodPumpStatus.lastBolusAmount
            val bolusTime = omnipodPumpStatus.lastBolusTime
            if (bolus != null && bolusTime != null && omnipodPumpStatus.podAvailable) {
                val agoMsc = System.currentTimeMillis() - omnipodPumpStatus.lastBolusTime.time
                val bolusMinAgo = agoMsc.toDouble() / 60.0 / 1000.0
                val unit = resourceHelper.gs(R.string.insulin_unit_shortname)
                val ago: String
                if (agoMsc < 60 * 1000) {
                    ago = resourceHelper.gs(R.string.combo_pump_connected_now)
                } else if (bolusMinAgo < 60) {
                    ago = DateUtil.minAgo(resourceHelper, omnipodPumpStatus.lastBolusTime.time)
                } else {
                    ago = DateUtil.hourAgo(omnipodPumpStatus.lastBolusTime.time, resourceHelper)
                }
                omnipod_lastbolus.text = resourceHelper.gs(R.string.omnipod_last_bolus, pumpType.determineCorrectBolusSize(bolus), unit, ago)
            } else {
                omnipod_lastbolus.text = ""
            }

            // base basal rate
            omnipod_basabasalrate.text = resourceHelper.gs(R.string.pump_basebasalrate, pumpType.determineCorrectBasalSize(plugin.baseBasalRate))

            omnipod_tempbasal.text = activePlugin.activeTreatments
                .getTempBasalFromHistory(System.currentTimeMillis())?.toStringFull() ?: ""

            // reservoir
            if (Round.isSame(omnipodPumpStatus.reservoirRemainingUnits, 75.0)) {
                omnipod_reservoir.text = resourceHelper.gs(R.string.omnipod_reservoir_over50)
            } else {
                omnipod_reservoir.text = resourceHelper.gs(R.string.omnipod_reservoir_left, omnipodPumpStatus.reservoirRemainingUnits)
            }
            warnColors.setColorInverse(omnipod_reservoir, omnipodPumpStatus.reservoirRemainingUnits, 50.0, 20.0)

        } else {
            omnipod_basabasalrate.text = ""
            omnipod_reservoir.text = ""
            omnipod_tempbasal.text = ""
            omnipod_lastbolus.text = ""
            omnipod_lastconnection.text = ""
            omnipod_lastconnection.setTextColor(Color.WHITE)
        }

        omnipod_errors.text = omnipodPumpStatus.errorInfo

        updateAcknowledgeAlerts()

        omnipod_refresh.isEnabled = omnipodPumpStatus.podAvailable

    }

    private fun updateAcknowledgeAlerts() {
        omnipod_pod_active_alerts_ack.isEnabled = omnipodPumpStatus.ackAlertsAvailable
        omnipod_pod_active_alerts.text = omnipodPumpStatus.ackAlertsText
    }

}

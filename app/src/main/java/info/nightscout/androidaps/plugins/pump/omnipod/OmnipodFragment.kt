package info.nightscout.androidaps.plugins.pump.omnipod

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.dialog.RileyLinkStatusActivity
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodStatusRequest
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodDeviceState
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.PodManagementActivity
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodDriverState
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodPumpStatus
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodAcknowledgeAlertsChanged
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodDeviceStatusChange
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodPumpValuesChanged
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodRefreshButtonState
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.omnipod_fragment.*
import org.slf4j.LoggerFactory


class OmnipodFragment : Fragment() {
    private val LOG = LoggerFactory.getLogger(L.PUMP)
    private var disposable: CompositeDisposable = CompositeDisposable()
    //private var podAvailable = false

    operator fun CompositeDisposable.plusAssign(disposable: Disposable) {
        add(disposable)
    }

    private val loopHandler = Handler()
    private lateinit var refreshLoop: Runnable

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

        omnipod_rl_status.text = MainApp.gs(RileyLinkServiceState.NotStarted.getResourceId(RileyLinkTargetDevice.Omnipod))

        omnipod_pod_status.setTextColor(Color.WHITE)
        omnipod_pod_status.text = "{fa-bed}"

        omnipod_pod_mgmt.setOnClickListener {
            if (OmnipodUtil.getPumpStatus().verifyConfiguration()) {
                startActivity(Intent(context, PodManagementActivity::class.java))
            } else {
                OmnipodUtil.displayNotConfiguredDialog(context)
            }
        }

        omnipod_refresh.setOnClickListener {
            if (!OmnipodUtil.getPumpStatus().verifyConfiguration()) {
                OmnipodUtil.displayNotConfiguredDialog(context)
            } else {
                omnipod_refresh.isEnabled = false
                OmnipodUtil.getPlugin().addPodStatusRequest(OmnipodStatusRequest.GetPodState);
                ConfigBuilderPlugin.getPlugin().commandQueue.readStatus("Clicked Refresh", object : Callback() {
                    override fun run() {
                        activity?.runOnUiThread { omnipod_refresh.isEnabled = true }
                    }
                })
            }
        }

        omnipod_stats.setOnClickListener {
            if (OmnipodUtil.getPumpStatus().verifyConfiguration()) {
                startActivity(Intent(context, RileyLinkStatusActivity::class.java))
            } else {
                OmnipodUtil.displayNotConfiguredDialog(context)
            }
        }

        omnipod_pod_active_alerts_ack.setOnClickListener {
            if (!OmnipodUtil.getPumpStatus().verifyConfiguration()) {
                OmnipodUtil.displayNotConfiguredDialog(context)
            } else {
                omnipod_pod_active_alerts_ack.isEnabled = false
                OmnipodUtil.getPlugin().addPodStatusRequest(OmnipodStatusRequest.AcknowledgeAlerts);
                ConfigBuilderPlugin.getPlugin().commandQueue.readStatus("Clicked Alert Ack", null)
            }
        }

        omnipod_pod_debug.setOnClickListener {
            if (!OmnipodUtil.getPumpStatus().verifyConfiguration()) {
                OmnipodUtil.displayNotConfiguredDialog(context)
            } else {
//                val readPulseLog = AapsOmnipodManager.getInstance().readPulseLog()
//
//                OKDialog.show(MainApp.instance().applicationContext, MainApp.gs(R.string.action),
//                        "Pulse Log:\n" + readPulseLog.toString(), null)
//

                omnipod_pod_debug.isEnabled = false
                OmnipodUtil.getPlugin().addPodStatusRequest(OmnipodStatusRequest.GetPodPulseLog);
                ConfigBuilderPlugin.getPlugin().commandQueue.readStatus("Clicked Refresh", object : Callback() {
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
        disposable += RxBus
                .toObservable(EventOmnipodRefreshButtonState::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ omnipod_refresh.isEnabled = it.newState }, { FabricPrivacy.logException(it) })
        disposable += RxBus
                .toObservable(EventOmnipodDeviceStatusChange::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (L.isEnabled(L.PUMP))
                        LOG.info("onStatusEvent(EventOmnipodDeviceStatusChange): {}", it)
                    setDeviceStatus()
                }, { FabricPrivacy.logException(it) })
        disposable += RxBus
                .toObservable(EventOmnipodPumpValuesChanged::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ updateGUI() }, { FabricPrivacy.logException(it) })
        disposable += RxBus
                .toObservable(EventOmnipodAcknowledgeAlertsChanged::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ updateAcknowledgeAlerts(OmnipodUtil.getPumpStatus()) }, { FabricPrivacy.logException(it) })
        disposable += RxBus
                .toObservable(EventPreferenceChange::class.java)
                .observeOn(Schedulers.io())
                .subscribe({ event ->
                    setVisibilityOfPodDebugButton()
                }, { FabricPrivacy.logException(it) })

    }


    fun setVisibilityOfPodDebugButton() {
        val isEnabled = SP.getBoolean(OmnipodConst.Prefs.PodDebuggingOptionsEnabled, false)

        if (isEnabled)
            omnipod_pod_debug.visibility = View.VISIBLE
        else
            omnipod_pod_debug.visibility = View.GONE
    }


    override fun onPause() {
        super.onPause()
        disposable.clear()
        loopHandler.removeCallbacks(refreshLoop)
    }


    @Synchronized
    private fun setDeviceStatus() {
        val pumpStatus: OmnipodPumpStatus = OmnipodUtil.getPumpStatus()
        pumpStatus.rileyLinkServiceState = checkStatusSet(pumpStatus.rileyLinkServiceState,
                RileyLinkUtil.getServiceState()) as RileyLinkServiceState?

        LOG.info("setDeviceStatus: [pumpStatus={}]", pumpStatus)

        val resourceId = pumpStatus.rileyLinkServiceState.getResourceId(RileyLinkTargetDevice.Omnipod)
        val rileyLinkError = RileyLinkUtil.getError()
        omnipod_rl_status.text =
                when {
                    pumpStatus.rileyLinkServiceState == RileyLinkServiceState.NotStarted -> MainApp.gs(resourceId)
                    pumpStatus.rileyLinkServiceState.isConnecting -> "{fa-bluetooth-b spin}   " + MainApp.gs(resourceId)
                    pumpStatus.rileyLinkServiceState.isError && rileyLinkError == null -> "{fa-bluetooth-b}   " + MainApp.gs(resourceId)
                    pumpStatus.rileyLinkServiceState.isError && rileyLinkError != null -> "{fa-bluetooth-b}   " + MainApp.gs(rileyLinkError.getResourceId(RileyLinkTargetDevice.MedtronicPump))
                    else -> "{fa-bluetooth-b}   " + MainApp.gs(resourceId)
                }
        omnipod_rl_status.setTextColor(if (rileyLinkError != null) Color.RED else Color.WHITE)

        pumpStatus.rileyLinkError = checkStatusSet(pumpStatus.rileyLinkError, RileyLinkUtil.getError()) as RileyLinkError?

        omnipod_errors.text =
                pumpStatus.rileyLinkError?.let {
                    MainApp.gs(it.getResourceId(RileyLinkTargetDevice.Omnipod))
                } ?: "-"

        val driverState = OmnipodUtil.getDriverState();

        LOG.info("getDriverState: [driverState={}]", driverState)

        if (driverState == OmnipodDriverState.NotInitalized) {
            omnipod_pod_address.text = MainApp.gs(R.string.omnipod_pod_name_no_info)
            omnipod_pod_expiry.text = "-"
            omnipod_pod_status.text = MainApp.gs(R.string.omnipod_pod_not_initalized)
            pumpStatus.podAvailable = false
            pumpStatus.podNumber == null
        } else if (driverState == OmnipodDriverState.Initalized_NoPod) {
            omnipod_pod_address.text = MainApp.gs(R.string.omnipod_pod_name_no_info)
            omnipod_pod_expiry.text = "-"
            omnipod_pod_status.text = MainApp.gs(R.string.omnipod_pod_no_pod_connected)
            pumpStatus.podAvailable = false
            pumpStatus.podNumber == null
        } else {
            pumpStatus.podLotNumber = "" + pumpStatus.podSessionState.lot
            pumpStatus.podAvailable = true
            omnipod_pod_address.text = pumpStatus.podSessionState.address.toString()
            omnipod_pod_expiry.text = pumpStatus.podSessionState.expiryDateAsString
            pumpStatus.podNumber = pumpStatus.podSessionState.address.toString()

            //pumpStatus.podSessionState = checkStatusSet(pumpStatus.podSessionState,
            //        OmnipodUtil.getPodSessionState()) as PodSessionState?

            var podDeviceState = pumpStatus.podDeviceState

            when (podDeviceState) {
                null,
                PodDeviceState.Sleeping -> omnipod_pod_status.text = "{fa-bed}   " // + pumpStatus.pumpDeviceState.name());
                PodDeviceState.NeverContacted,
                PodDeviceState.WakingUp,
                PodDeviceState.PumpUnreachable,
                PodDeviceState.ErrorWhenCommunicating,
                PodDeviceState.TimeoutWhenCommunicating,
                PodDeviceState.InvalidConfiguration -> omnipod_pod_status.text = " " + MainApp.gs(podDeviceState.resourceId)
                PodDeviceState.Active -> {

                    omnipod_pod_status.text = "Active";
//                val cmd = OmnipodUtil.getCurrentCommand()
//                if (cmd == null)
//                    omnipod_pod_status.text = " " + MainApp.gs(pumpStatus.pumpDeviceState.resourceId)
//                else {
//                    log.debug("Command: " + cmd)
//                    val cmdResourceId = cmd.resourceId
//                    if (cmd == MedtronicCommandType.GetHistoryData) {
//                        omnipod_pod_status.text = OmnipodUtil.frameNumber?.let {
//                            MainApp.gs(cmdResourceId, OmnipodUtil.pageNumber, OmnipodUtil.frameNumber)
//                        }
//                                ?: MainApp.gs(R.string.medtronic_cmd_desc_get_history_request, OmnipodUtil.pageNumber)
//                    } else {
//                        omnipod_pod_status.text = " " + (cmdResourceId?.let { MainApp.gs(it) }
//                                ?: cmd.getCommandDescription())
//                    }
//                }
                }
                else -> LOG.warn("Unknown pump state: " + pumpStatus.podDeviceState)
            }


        }


//        pumpStatus.pumpDeviceState = checkStatusSet(pumpStatus.pumpDeviceState,
//                OmnipodUtil.getPumpDeviceState()) as PumpDeviceState?
//
//        when (pumpStatus.pumpDeviceState) {
//            null,
//            PumpDeviceState.Sleeping -> omnipod_pod_status.text = "{fa-bed}   " // + pumpStatus.pumpDeviceState.name());
//            PumpDeviceState.NeverContacted,
//            PumpDeviceState.WakingUp,
//            PumpDeviceState.PumpUnreachable,
//            PumpDeviceState.ErrorWhenCommunicating,
//            PumpDeviceState.TimeoutWhenCommunicating,
//            PumpDeviceState.InvalidConfiguration -> omnipod_pod_status.text = " " + MainApp.gs(pumpStatus.pumpDeviceState.resourceId)
//            PumpDeviceState.Active -> {
//                val cmd = OmnipodUtil.getCurrentCommand()
//                if (cmd == null)
//                    omnipod_pod_status.text = " " + MainApp.gs(pumpStatus.pumpDeviceState.resourceId)
//                else {
//                    log.debug("Command: " + cmd)
//                    val cmdResourceId = cmd.resourceId
//                    if (cmd == MedtronicCommandType.GetHistoryData) {
//                        omnipod_pod_status.text = OmnipodUtil.frameNumber?.let {
//                            MainApp.gs(cmdResourceId, OmnipodUtil.pageNumber, OmnipodUtil.frameNumber)
//                        }
//                                ?: MainApp.gs(R.string.medtronic_cmd_desc_get_history_request, OmnipodUtil.pageNumber)
//                    } else {
//                        omnipod_pod_status.text = " " + (cmdResourceId?.let { MainApp.gs(it) }
//                                ?: cmd.getCommandDescription())
//                    }
//                }
//            }
//            else -> log.warn("Unknown pump state: " + pumpStatus.pumpDeviceState)
//        }

        val status = ConfigBuilderPlugin.getPlugin().commandQueue.spannedStatus()
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
        val plugin = OmnipodPumpPlugin.getPlugin()
        val pumpStatus = OmnipodUtil.getPumpStatus()
        var pumpType = OmnipodUtil.getPumpType()

        if (pumpType==null) {
            LOG.warn("PumpType was not set, reseting to Omnipod.")
            pumpType = PumpType.Insulet_Omnipod;
        }

        setDeviceStatus()

        if (pumpStatus.podAvailable) {
            // last connection
            if (pumpStatus.lastConnection != 0L) {
                //val minAgo = DateUtil.minAgo(pumpStatus.lastConnection)
                val min = (System.currentTimeMillis() - pumpStatus.lastConnection) / 1000 / 60
                if (pumpStatus.lastConnection + 60 * 1000 > System.currentTimeMillis()) {
                    omnipod_lastconnection.setText(R.string.combo_pump_connected_now)
                    //omnipod_lastconnection.setTextColor(Color.WHITE)
                } else { //if (pumpStatus.lastConnection + 30 * 60 * 1000 < System.currentTimeMillis()) {

                    if (min < 60) {
                        omnipod_lastconnection.text = MainApp.gs(R.string.minago, min)
                    } else if (min < 1440) {
                        val h = (min / 60).toInt()
                        omnipod_lastconnection.text = (MainApp.gq(R.plurals.objective_hours, h, h) + " "
                                + MainApp.gs(R.string.ago))
                    } else {
                        val h = (min / 60).toInt()
                        val d = h / 24
                        // h = h - (d * 24);
                        omnipod_lastconnection.text = (MainApp.gq(R.plurals.objective_days, d, d) + " "
                                + MainApp.gs(R.string.ago))
                    }
                    //omnipod_lastconnection.setTextColor(Color.RED)
                }
//                } else {
//                    omnipod_lastconnection.text = minAgo
//                    //omnipod_lastconnection.setTextColor(Color.WHITE)
//                }
            }

            // last bolus
            val bolus = pumpStatus.lastBolusAmount
            val bolusTime = pumpStatus.lastBolusTime
            if (bolus != null && bolusTime != null && pumpStatus.podAvailable) {
                val agoMsc = System.currentTimeMillis() - pumpStatus.lastBolusTime.time
                val bolusMinAgo = agoMsc.toDouble() / 60.0 / 1000.0
                val unit = MainApp.gs(R.string.insulin_unit_shortname)
                val ago: String
                if (agoMsc < 60 * 1000) {
                    ago = MainApp.gs(R.string.combo_pump_connected_now)
                } else if (bolusMinAgo < 60) {
                    ago = DateUtil.minAgo(pumpStatus.lastBolusTime.time)
                } else {
                    ago = DateUtil.hourAgo(pumpStatus.lastBolusTime.time)
                }
                omnipod_lastbolus.text = MainApp.gs(R.string.omnipod_last_bolus, pumpType.determineCorrectBolusSize(bolus), unit, ago)
            } else {
                omnipod_lastbolus.text = ""
            }

            // base basal rate
            omnipod_basabasalrate.text = MainApp.gs(R.string.pump_basebasalrate, pumpType.determineCorrectBasalSize(plugin.baseBasalRate))

            omnipod_tempbasal.text = TreatmentsPlugin.getPlugin()
                    .getTempBasalFromHistory(System.currentTimeMillis())?.toStringFull() ?: ""

            // reservoir
            if (RileyLinkUtil.isSame(pumpStatus.reservoirRemainingUnits, 75.0)) {
                omnipod_reservoir.text = MainApp.gs(R.string.omnipod_reservoir_over50)
            } else {
                omnipod_reservoir.text = MainApp.gs(R.string.omnipod_reservoir_left, pumpStatus.reservoirRemainingUnits)
            }
            SetWarnColor.setColorInverse(omnipod_reservoir, pumpStatus.reservoirRemainingUnits, 50.0, 20.0)

        } else {
            omnipod_basabasalrate.text = ""
            omnipod_reservoir.text = ""
            omnipod_tempbasal.text = ""
            omnipod_lastbolus.text = ""
            omnipod_lastconnection.text = ""
            omnipod_lastconnection.setTextColor(Color.WHITE)
        }

        omnipod_errors.text = pumpStatus.errorInfo

        updateAcknowledgeAlerts(pumpStatus)

        omnipod_refresh.isEnabled = pumpStatus.podAvailable

    }


    private fun updateAcknowledgeAlerts(pumpStatus: OmnipodPumpStatus) {
        if (pumpStatus != null) {
            omnipod_pod_active_alerts_ack.isEnabled = pumpStatus.ackAlertsAvailable
            omnipod_pod_active_alerts.text = pumpStatus.ackAlertsText
        }
    }


}

package info.nightscout.androidaps.plugins.pump.medtronic

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
import info.nightscout.androidaps.events.EventExtendedBolusChange
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.androidaps.events.EventTempBasalChange
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.dialog.RileyLinkStatusActivity
import info.nightscout.androidaps.plugins.pump.medtronic.defs.BatteryType
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicCommandType
import info.nightscout.androidaps.plugins.pump.medtronic.defs.PumpDeviceState
import info.nightscout.androidaps.plugins.pump.medtronic.dialog.MedtronicHistoryActivity
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventMedtronicDeviceStatusChange
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventMedtronicPumpConfigurationChanged
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventMedtronicPumpValuesChanged
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventRefreshButtonState
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.queue.events.EventQueueChanged
import info.nightscout.androidaps.utils.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.medtronic_fragment.*
import org.slf4j.LoggerFactory


class MedtronicFragment : Fragment() {
    private val log = LoggerFactory.getLogger(L.PUMP)
    private var disposable: CompositeDisposable = CompositeDisposable()

    private val loopHandler = Handler()
    private lateinit var refreshLoop: Runnable

    init {
        refreshLoop = Runnable {
            activity?.runOnUiThread { updateGUI() }
            loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.medtronic_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        medtronic_pumpstatus.setBackgroundColor(MainApp.gc(R.color.colorInitializingBorder))

        medtronic_rl_status.text = MainApp.gs(RileyLinkServiceState.NotStarted.getResourceId(RileyLinkTargetDevice.MedtronicPump))

        medtronic_pump_status.setTextColor(Color.WHITE)
        medtronic_pump_status.text = "{fa-bed}"

        medtronic_history.setOnClickListener {
            if (MedtronicUtil.getPumpStatus().verifyConfiguration()) {
                startActivity(Intent(context, MedtronicHistoryActivity::class.java))
            } else {
                MedtronicUtil.displayNotConfiguredDialog(context)
            }
        }

        medtronic_refresh.setOnClickListener {
            if (!MedtronicUtil.getPumpStatus().verifyConfiguration()) {
                MedtronicUtil.displayNotConfiguredDialog(context)
            } else {
                medtronic_refresh.isEnabled = false
                MedtronicPumpPlugin.getPlugin().resetStatusState()
                ConfigBuilderPlugin.getPlugin().commandQueue.readStatus("Clicked refresh", object : Callback() {
                    override fun run() {
                        activity?.runOnUiThread { medtronic_refresh?.isEnabled = true }
                    }
                })
            }
        }

        medtronic_stats.setOnClickListener {
            if (MedtronicUtil.getPumpStatus().verifyConfiguration()) {
                startActivity(Intent(context, RileyLinkStatusActivity::class.java))
            } else {
                MedtronicUtil.displayNotConfiguredDialog(context)
            }
        }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())
        disposable += RxBus
                .toObservable(EventRefreshButtonState::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ medtronic_refresh.isEnabled = it.newState }, { FabricPrivacy.logException(it) })
        disposable += RxBus
                .toObservable(EventMedtronicDeviceStatusChange::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (L.isEnabled(L.PUMP))
                        log.info("onStatusEvent(EventMedtronicDeviceStatusChange): {}", it)
                    setDeviceStatus()
                }, { FabricPrivacy.logException(it) })
        disposable += RxBus
                .toObservable(EventMedtronicPumpValuesChanged::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ updateGUI() }, { FabricPrivacy.logException(it) })
        disposable += RxBus
                .toObservable(EventExtendedBolusChange::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ updateGUI() }, { FabricPrivacy.logException(it) })
        disposable += RxBus
                .toObservable(EventTempBasalChange::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ updateGUI() }, { FabricPrivacy.logException(it) })
        disposable += RxBus
                .toObservable(EventMedtronicPumpConfigurationChanged::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (L.isEnabled(L.PUMP))
                        log.debug("EventMedtronicPumpConfigurationChanged triggered")
                    MedtronicUtil.getPumpStatus().verifyConfiguration()
                    updateGUI()
                }, { FabricPrivacy.logException(it) })
        disposable += RxBus
                .toObservable(EventPumpStatusChanged::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ updateGUI() }, { FabricPrivacy.logException(it) })
        disposable += RxBus
                .toObservable(EventQueueChanged::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ updateGUI() }, { FabricPrivacy.logException(it) })

        updateGUI()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
        loopHandler.removeCallbacks(refreshLoop)
    }

    @Synchronized
    private fun setDeviceStatus() {
        val pumpStatus: MedtronicPumpStatus = MedtronicUtil.getPumpStatus()
        pumpStatus.rileyLinkServiceState = checkStatusSet(pumpStatus.rileyLinkServiceState,
                RileyLinkUtil.getServiceState()) as RileyLinkServiceState?

        val resourceId = pumpStatus.rileyLinkServiceState.getResourceId(RileyLinkTargetDevice.MedtronicPump)
        val rileyLinkError = RileyLinkUtil.getError()
        medtronic_rl_status.text =
                when {
                    pumpStatus.rileyLinkServiceState == RileyLinkServiceState.NotStarted -> MainApp.gs(resourceId)
                    pumpStatus.rileyLinkServiceState.isConnecting -> "{fa-bluetooth-b spin}   " + MainApp.gs(resourceId)
                    pumpStatus.rileyLinkServiceState.isError && rileyLinkError == null -> "{fa-bluetooth-b}   " + MainApp.gs(resourceId)
                    pumpStatus.rileyLinkServiceState.isError && rileyLinkError != null -> "{fa-bluetooth-b}   " + MainApp.gs(rileyLinkError.getResourceId(RileyLinkTargetDevice.MedtronicPump))
                    else -> "{fa-bluetooth-b}   " + MainApp.gs(resourceId)
                }
        medtronic_rl_status.setTextColor(if (rileyLinkError != null) Color.RED else Color.WHITE)

        pumpStatus.rileyLinkError = checkStatusSet(pumpStatus.rileyLinkError, RileyLinkUtil.getError()) as RileyLinkError?

        medtronic_errors.text =
                pumpStatus.rileyLinkError?.let {
                    MainApp.gs(it.getResourceId(RileyLinkTargetDevice.MedtronicPump))
                } ?: "-"

        pumpStatus.pumpDeviceState = checkStatusSet(pumpStatus.pumpDeviceState,
                MedtronicUtil.getPumpDeviceState()) as PumpDeviceState?

        when (pumpStatus.pumpDeviceState) {
            null,
            PumpDeviceState.Sleeping -> medtronic_pump_status.text = "{fa-bed}   " // + pumpStatus.pumpDeviceState.name());
            PumpDeviceState.NeverContacted,
            PumpDeviceState.WakingUp,
            PumpDeviceState.PumpUnreachable,
            PumpDeviceState.ErrorWhenCommunicating,
            PumpDeviceState.TimeoutWhenCommunicating,
            PumpDeviceState.InvalidConfiguration -> medtronic_pump_status.text = " " + MainApp.gs(pumpStatus.pumpDeviceState.resourceId)
            PumpDeviceState.Active -> {
                val cmd = MedtronicUtil.getCurrentCommand()
                if (cmd == null)
                    medtronic_pump_status.text = " " + MainApp.gs(pumpStatus.pumpDeviceState.resourceId)
                else {
                    log.debug("Command: " + cmd)
                    val cmdResourceId = cmd.resourceId
                    if (cmd == MedtronicCommandType.GetHistoryData) {
                        medtronic_pump_status.text = MedtronicUtil.frameNumber?.let {
                            MainApp.gs(cmdResourceId, MedtronicUtil.pageNumber, MedtronicUtil.frameNumber)
                        }
                                ?: MainApp.gs(R.string.medtronic_cmd_desc_get_history_request, MedtronicUtil.pageNumber)
                    } else {
                        medtronic_pump_status.text = " " + (cmdResourceId?.let { MainApp.gs(it) }
                                ?: cmd.getCommandDescription())
                    }
                }
            }
            else -> log.warn("Unknown pump state: " + pumpStatus.pumpDeviceState)
        }

        val status = ConfigBuilderPlugin.getPlugin().commandQueue.spannedStatus()
        if (status.toString() == "") {
            medtronic_queue.visibility = View.GONE
        } else {
            medtronic_queue.visibility = View.VISIBLE
            medtronic_queue.text = status
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
    @Synchronized
    fun updateGUI() {
        if (medtronic_rl_status == null) return
        val plugin = MedtronicPumpPlugin.getPlugin()
        val pumpStatus = MedtronicUtil.getPumpStatus()

        setDeviceStatus()

        // last connection
        if (pumpStatus.lastConnection != 0L) {
            val minAgo = DateUtil.minAgo(pumpStatus.lastConnection)
            val min = (System.currentTimeMillis() - pumpStatus.lastConnection) / 1000 / 60
            if (pumpStatus.lastConnection + 60 * 1000 > System.currentTimeMillis()) {
                medtronic_lastconnection.setText(R.string.combo_pump_connected_now)
                medtronic_lastconnection.setTextColor(Color.WHITE)
            } else if (pumpStatus.lastConnection + 30 * 60 * 1000 < System.currentTimeMillis()) {

                if (min < 60) {
                    medtronic_lastconnection.text = MainApp.gs(R.string.minago, min)
                } else if (min < 1440) {
                    val h = (min / 60).toInt()
                    medtronic_lastconnection.text = (MainApp.gq(R.plurals.objective_hours, h, h) + " "
                            + MainApp.gs(R.string.ago))
                } else {
                    val h = (min / 60).toInt()
                    val d = h / 24
                    // h = h - (d * 24);
                    medtronic_lastconnection.text = (MainApp.gq(R.plurals.objective_days, d, d) + " "
                            + MainApp.gs(R.string.ago))
                }
                medtronic_lastconnection.setTextColor(Color.RED)
            } else {
                medtronic_lastconnection.text = minAgo
                medtronic_lastconnection.setTextColor(Color.WHITE)
            }
        }

        // last bolus
        val bolus = pumpStatus.lastBolusAmount
        val bolusTime = pumpStatus.lastBolusTime
        if (bolus != null && bolusTime != null) {
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
            medtronic_lastbolus.text = MainApp.gs(R.string.combo_last_bolus, bolus, unit, ago)
        } else {
            medtronic_lastbolus.text = ""
        }

        // base basal rate
        medtronic_basabasalrate.text = ("(" + pumpStatus.activeProfileName + ")  "
                + MainApp.gs(R.string.pump_basebasalrate, plugin.baseBasalRate))

        medtronic_tempbasal.text = TreatmentsPlugin.getPlugin()
                .getTempBasalFromHistory(System.currentTimeMillis())?.toStringFull() ?: ""

        // battery
        if (MedtronicUtil.getBatteryType() == BatteryType.None || pumpStatus.batteryVoltage == null) {
            medtronic_pumpstate_battery.text = "{fa-battery-" + pumpStatus.batteryRemaining / 25 + "}  "
        } else {
            medtronic_pumpstate_battery.text = "{fa-battery-" + pumpStatus.batteryRemaining / 25 + "}  " + pumpStatus.batteryRemaining + "%" + String.format("  (%.2f V)", pumpStatus.batteryVoltage)
        }
        SetWarnColor.setColorInverse(medtronic_pumpstate_battery, pumpStatus.batteryRemaining.toDouble(), 25.0, 10.0)

        // reservoir
        medtronic_reservoir.text = MainApp.gs(R.string.reservoirvalue, pumpStatus.reservoirRemainingUnits, pumpStatus.reservoirFullUnits)
        SetWarnColor.setColorInverse(medtronic_reservoir, pumpStatus.reservoirRemainingUnits, 50.0, 20.0)

        medtronic_errors.text = pumpStatus.errorInfo
    }
}

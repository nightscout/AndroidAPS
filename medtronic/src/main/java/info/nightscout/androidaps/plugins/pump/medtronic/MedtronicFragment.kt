package info.nightscout.androidaps.plugins.pump.medtronic

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.events.EventExtendedBolusChange
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.androidaps.events.EventTempBasalChange
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState
import info.nightscout.androidaps.plugins.pump.medtronic.defs.BatteryType
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicCommandType
import info.nightscout.androidaps.plugins.pump.medtronic.dialog.MedtronicHistoryActivity
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus
import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventMedtronicPumpConfigurationChanged
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventMedtronicPumpValuesChanged
import info.nightscout.androidaps.plugins.pump.common.events.EventRefreshButtonState
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.dialog.RileyLinkStatusActivity
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.queue.events.EventQueueChanged
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.WarnColors
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.medtronic_fragment.*
import javax.inject.Inject

class MedtronicFragment : DaggerFragment() {
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var medtronicPumpPlugin: MedtronicPumpPlugin
    @Inject lateinit var warnColors: WarnColors
    @Inject lateinit var rileyLinkUtil: RileyLinkUtil
    @Inject lateinit var medtronicUtil: MedtronicUtil
    @Inject lateinit var medtronicPumpStatus: MedtronicPumpStatus
    @Inject lateinit var rileyLinkServiceData: RileyLinkServiceData

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

        medtronic_pumpstatus.setBackgroundColor(resourceHelper.gc(R.color.colorInitializingBorder))

        medtronic_rl_status.text = resourceHelper.gs(RileyLinkServiceState.NotStarted.resourceId)

        medtronic_pump_status.setTextColor(Color.WHITE)
        medtronic_pump_status.text = "{fa-bed}"

        medtronic_history.setOnClickListener {
            if (medtronicPumpPlugin.rileyLinkService?.verifyConfiguration() == true) {
                startActivity(Intent(context, MedtronicHistoryActivity::class.java))
            } else {
                displayNotConfiguredDialog()
            }
        }

        medtronic_refresh.setOnClickListener {
            if (medtronicPumpPlugin.rileyLinkService?.verifyConfiguration() != true) {
                displayNotConfiguredDialog()
            } else {
                medtronic_refresh.isEnabled = false
                medtronicPumpPlugin.resetStatusState()
                commandQueue.readStatus("Clicked refresh", object : Callback() {
                    override fun run() {
                        activity?.runOnUiThread { medtronic_refresh?.isEnabled = true }
                    }
                })
            }
        }

        medtronic_stats.setOnClickListener {
            if (medtronicPumpPlugin.rileyLinkService?.verifyConfiguration() == true) {
                startActivity(Intent(context, RileyLinkStatusActivity::class.java))
            } else {
                displayNotConfiguredDialog()
            }
        }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())
        disposable += rxBus
            .toObservable(EventRefreshButtonState::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ medtronic_refresh.isEnabled = it.newState }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventRileyLinkDeviceStatusChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                aapsLogger.debug(LTag.PUMP, "onStatusEvent(EventRileyLinkDeviceStatusChange): $it")
                setDeviceStatus()
            }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventMedtronicPumpValuesChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGUI() }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGUI() }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventTempBasalChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGUI() }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventMedtronicPumpConfigurationChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                aapsLogger.debug(LTag.PUMP, "EventMedtronicPumpConfigurationChanged triggered")
                medtronicPumpPlugin.rileyLinkService?.verifyConfiguration()
                updateGUI()
            }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGUI() }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventQueueChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGUI() }, { fabricPrivacy.logException(it) })

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
        val resourceId = rileyLinkServiceData.rileyLinkServiceState.resourceId
        val rileyLinkError = medtronicPumpPlugin.rileyLinkService?.error
        medtronic_rl_status.text =
            when {
                rileyLinkServiceData.rileyLinkServiceState == RileyLinkServiceState.NotStarted   -> resourceHelper.gs(resourceId)
                rileyLinkServiceData.rileyLinkServiceState.isConnecting                          -> "{fa-bluetooth-b spin}   " + resourceHelper.gs(resourceId)
                rileyLinkServiceData.rileyLinkServiceState.isError && rileyLinkError == null     -> "{fa-bluetooth-b}   " + resourceHelper.gs(resourceId)
                rileyLinkServiceData.rileyLinkServiceState.isError && rileyLinkError != null     -> "{fa-bluetooth-b}   " + resourceHelper.gs(rileyLinkError.getResourceId(RileyLinkTargetDevice.MedtronicPump))
                else                                                                             -> "{fa-bluetooth-b}   " + resourceHelper.gs(resourceId)
            }
        medtronic_rl_status.setTextColor(if (rileyLinkError != null) Color.RED else Color.WHITE)

        medtronic_errors.text =
            rileyLinkServiceData.rileyLinkError?.let {
                resourceHelper.gs(it.getResourceId(RileyLinkTargetDevice.MedtronicPump))
            } ?: "-"

        when (medtronicPumpStatus.pumpDeviceState) {
            null,
            PumpDeviceState.Sleeping             -> medtronic_pump_status.text = "{fa-bed}   " // + pumpStatus.pumpDeviceState.name());
            PumpDeviceState.NeverContacted,
            PumpDeviceState.WakingUp,
            PumpDeviceState.PumpUnreachable,
            PumpDeviceState.ErrorWhenCommunicating,
            PumpDeviceState.TimeoutWhenCommunicating,
            PumpDeviceState.InvalidConfiguration -> medtronic_pump_status.text = " " + resourceHelper.gs(medtronicPumpStatus.pumpDeviceState.resourceId)

            PumpDeviceState.Active               -> {
                val cmd = medtronicUtil.currentCommand
                if (cmd == null)
                    medtronic_pump_status.text = " " + resourceHelper.gs(medtronicPumpStatus.pumpDeviceState.resourceId)
                else {
                    aapsLogger.debug(LTag.PUMP, "Command: " + cmd)
                    val cmdResourceId = cmd.resourceId
                    if (cmd == MedtronicCommandType.GetHistoryData) {
                        medtronic_pump_status.text = medtronicUtil.frameNumber?.let {
                            resourceHelper.gs(cmdResourceId, medtronicUtil.pageNumber, medtronicUtil.frameNumber)
                        }
                            ?: resourceHelper.gs(R.string.medtronic_cmd_desc_get_history_request, medtronicUtil.pageNumber)
                    } else {
                        medtronic_pump_status.text = " " + (cmdResourceId?.let { resourceHelper.gs(it) }
                            ?: cmd.getCommandDescription())
                    }
                }
            }

            else   -> aapsLogger.warn(LTag.PUMP, "Unknown pump state: " + medtronicPumpStatus.pumpDeviceState)
        }

        val status = commandQueue.spannedStatus()
        if (status.toString() == "") {
            medtronic_queue.visibility = View.GONE
        } else {
            medtronic_queue.visibility = View.VISIBLE
            medtronic_queue.text = status
        }
    }

    private fun displayNotConfiguredDialog() {
        context?.let {
            OKDialog.show(it, resourceHelper.gs(R.string.medtronic_warning),
                resourceHelper.gs(R.string.medtronic_error_operation_not_possible_no_configuration), null)
        }
    }

    // GUI functions
    @Synchronized
    fun updateGUI() {
        if (medtronic_rl_status == null) return

        setDeviceStatus()

        // last connection
        if (medtronicPumpStatus.lastConnection != 0L) {
            val minAgo = DateUtil.minAgo(resourceHelper, medtronicPumpStatus.lastConnection)
            val min = (System.currentTimeMillis() - medtronicPumpStatus.lastConnection) / 1000 / 60
            if (medtronicPumpStatus.lastConnection + 60 * 1000 > System.currentTimeMillis()) {
                medtronic_lastconnection.setText(R.string.medtronic_pump_connected_now)
                medtronic_lastconnection.setTextColor(Color.WHITE)
            } else if (medtronicPumpStatus.lastConnection + 30 * 60 * 1000 < System.currentTimeMillis()) {

                if (min < 60) {
                    medtronic_lastconnection.text = resourceHelper.gs(R.string.minago, min)
                } else if (min < 1440) {
                    val h = (min / 60).toInt()
                    medtronic_lastconnection.text = (resourceHelper.gq(R.plurals.duration_hours, h, h) + " "
                        + resourceHelper.gs(R.string.ago))
                } else {
                    val h = (min / 60).toInt()
                    val d = h / 24
                    // h = h - (d * 24);
                    medtronic_lastconnection.text = (resourceHelper.gq(R.plurals.duration_days, d, d) + " "
                        + resourceHelper.gs(R.string.ago))
                }
                medtronic_lastconnection.setTextColor(Color.RED)
            } else {
                medtronic_lastconnection.text = minAgo
                medtronic_lastconnection.setTextColor(Color.WHITE)
            }
        }

        // last bolus
        val bolus = medtronicPumpStatus.lastBolusAmount
        val bolusTime = medtronicPumpStatus.lastBolusTime
        if (bolus != null && bolusTime != null) {
            val agoMsc = System.currentTimeMillis() - medtronicPumpStatus.lastBolusTime.time
            val bolusMinAgo = agoMsc.toDouble() / 60.0 / 1000.0
            val unit = resourceHelper.gs(R.string.insulin_unit_shortname)
            val ago: String
            if (agoMsc < 60 * 1000) {
                ago = resourceHelper.gs(R.string.medtronic_pump_connected_now)
            } else if (bolusMinAgo < 60) {
                ago = DateUtil.minAgo(resourceHelper, medtronicPumpStatus.lastBolusTime.time)
            } else {
                ago = DateUtil.hourAgo(medtronicPumpStatus.lastBolusTime.time, resourceHelper)
            }
            medtronic_lastbolus.text = resourceHelper.gs(R.string.mdt_last_bolus, bolus, unit, ago)
        } else {
            medtronic_lastbolus.text = ""
        }

        // base basal rate
        medtronic_basabasalrate.text = ("(" + medtronicPumpStatus.activeProfileName + ")  "
            + resourceHelper.gs(R.string.pump_basebasalrate, medtronicPumpPlugin.baseBasalRate))

        medtronic_tempbasal.text = activePlugin.activeTreatments.getTempBasalFromHistory(System.currentTimeMillis())?.toStringFull()
            ?: ""

        // battery
        if (medtronicPumpStatus.batteryType == BatteryType.None || medtronicPumpStatus.batteryVoltage == null) {
            medtronic_pumpstate_battery.text = "{fa-battery-" + medtronicPumpStatus.batteryRemaining / 25 + "}  "
        } else {
            medtronic_pumpstate_battery.text = "{fa-battery-" + medtronicPumpStatus.batteryRemaining / 25 + "}  " + medtronicPumpStatus.batteryRemaining + "%" + String.format("  (%.2f V)", medtronicPumpStatus.batteryVoltage)
        }
        warnColors.setColorInverse(medtronic_pumpstate_battery, medtronicPumpStatus.batteryRemaining.toDouble(), 25.0, 10.0)

        // reservoir
        medtronic_reservoir.text = resourceHelper.gs(R.string.reservoirvalue, medtronicPumpStatus.reservoirRemainingUnits, medtronicPumpStatus.reservoirFullUnits)
        warnColors.setColorInverse(medtronic_reservoir, medtronicPumpStatus.reservoirRemainingUnits, 50.0, 20.0)

        medtronicPumpPlugin.rileyLinkService?.verifyConfiguration()
        medtronic_errors.text = medtronicPumpStatus.errorInfo
    }
}

package info.nightscout.androidaps.plugins.pump.danaR


import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.TDDStatsActivity
import info.nightscout.androidaps.events.EventExtendedBolusChange
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.androidaps.events.EventTempBasalChange
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.interfaces.PumpInterface
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.pump.danaR.activities.DanaRHistoryActivity
import info.nightscout.androidaps.plugins.pump.danaR.activities.DanaRUserOptionsActivity
import info.nightscout.androidaps.plugins.pump.danaR.events.EventDanaRNewStatus
import info.nightscout.androidaps.plugins.pump.danaRKorean.DanaRKoreanPlugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.plugins.treatments.fragments.ProfileViewerDialog
import info.nightscout.androidaps.queue.events.EventQueueChanged
import info.nightscout.androidaps.utils.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.danar_fragment.*
import org.slf4j.LoggerFactory

class DanaRFragment : Fragment() {
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.danar_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dana_pumpstatus.setBackgroundColor(MainApp.gc(R.color.colorInitializingBorder))

        danar_history.setOnClickListener { startActivity(Intent(context, DanaRHistoryActivity::class.java)) }
        danar_viewprofile.setOnClickListener {
            fragmentManager?.let { fragmentManager ->
                val args = Bundle()
                args.putLong("time", DateUtil.now())
                args.putInt("mode", ProfileViewerDialog.Mode.PUMP_PROFILE.ordinal)
                val pvd = ProfileViewerDialog()
                pvd.arguments = args
                pvd.show(fragmentManager, "ProfileViewDialog")
            }
        }
        danar_stats.setOnClickListener { startActivity(Intent(context, TDDStatsActivity::class.java)) }
        danar_user_options.setOnClickListener { startActivity(Intent(context, DanaRUserOptionsActivity::class.java)) }
        danar_btconnection.setOnClickListener {
            if (L.isEnabled(L.PUMP))
                log.debug("Clicked connect to pump")
            DanaRPump.getInstance().lastConnection = 0
            ConfigBuilderPlugin.getPlugin().commandQueue.readStatus("Clicked connect to pump", null)
        }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())
        disposable += RxBus
                .toObservable(EventDanaRNewStatus::class.java)
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
                .toObservable(EventQueueChanged::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ updateGUI() }, { FabricPrivacy.logException(it) })
        disposable += RxBus
                .toObservable(EventPumpStatusChanged::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    when {
                        it.sStatus == EventPumpStatusChanged.Status.CONNECTING -> danar_btconnection?.text = "{fa-bluetooth-b spin} " + it.sSecondsElapsed + "s"
                        it.sStatus == EventPumpStatusChanged.Status.CONNECTED -> danar_btconnection?.text = "{fa-bluetooth}"
                        it.sStatus == EventPumpStatusChanged.Status.DISCONNECTED -> danar_btconnection?.text = "{fa-bluetooth-b}"
                    }
                    if (it.getStatus() != "") {
                        dana_pumpstatus?.text = it.getStatus()
                        dana_pumpstatuslayout?.visibility = View.VISIBLE
                    } else {
                        dana_pumpstatuslayout?.visibility = View.GONE
                    }
                }, { FabricPrivacy.logException(it) })
        updateGUI()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
        loopHandler.removeCallbacks(refreshLoop)
    }

    // GUI functions
    @Synchronized
    internal fun updateGUI() {
        if (danar_dailyunits == null) return
        val pump = DanaRPump.getInstance()
        val plugin: PumpInterface = ConfigBuilderPlugin.getPlugin().activePump ?: return
        if (pump.lastConnection != 0L) {
            val agoMsec = System.currentTimeMillis() - pump.lastConnection
            val agoMin = (agoMsec.toDouble() / 60.0 / 1000.0).toInt()
            danar_lastconnection.text = DateUtil.timeString(pump.lastConnection) + " (" + String.format(MainApp.gs(R.string.minago), agoMin) + ")"
            SetWarnColor.setColor(danar_lastconnection, agoMin.toDouble(), 16.0, 31.0)
        }
        if (pump.lastBolusTime != 0L) {
            val agoMsec = System.currentTimeMillis() - pump.lastBolusTime
            val agoHours = agoMsec.toDouble() / 60.0 / 60.0 / 1000.0
            if (agoHours < 6)
            // max 6h back
                danar_lastbolus.text = DateUtil.timeString(pump.lastBolusTime) + " " + DateUtil.sinceString(pump.lastBolusTime) + " " + MainApp.gs(R.string.formatinsulinunits, pump.lastBolusAmount)
            else
                danar_lastbolus.text = ""
        }

        danar_dailyunits.text = MainApp.gs(R.string.reservoirvalue, pump.dailyTotalUnits, pump.maxDailyTotalUnits)
        SetWarnColor.setColor(danar_dailyunits, pump.dailyTotalUnits, pump.maxDailyTotalUnits * 0.75, pump.maxDailyTotalUnits * 0.9)
        danar_basabasalrate.text = "( " + (pump.activeProfile + 1) + " )  " + MainApp.gs(R.string.pump_basebasalrate, plugin.baseBasalRate)
        // DanaRPlugin, DanaRKoreanPlugin
        if (ConfigBuilderPlugin.getPlugin().activePump!!.isFakingTempsByExtendedBoluses) {
            danar_tempbasal.text = TreatmentsPlugin.getPlugin()
                    .getRealTempBasalFromHistory(System.currentTimeMillis())?.toStringFull() ?: ""
        } else {
            // v2 plugin
            danar_tempbasal.text = TreatmentsPlugin.getPlugin()
                    .getTempBasalFromHistory(System.currentTimeMillis())?.toStringFull() ?: ""
        }
        danar_extendedbolus.text = TreatmentsPlugin.getPlugin()
                .getExtendedBolusFromHistory(System.currentTimeMillis())?.toString() ?: ""
        danar_reservoir.text = MainApp.gs(R.string.reservoirvalue, pump.reservoirRemainingUnits, 300)
        SetWarnColor.setColorInverse(danar_reservoir, pump.reservoirRemainingUnits, 50.0, 20.0)
        danar_battery.text = "{fa-battery-" + pump.batteryRemaining / 25 + "}"
        SetWarnColor.setColorInverse(danar_battery, pump.batteryRemaining.toDouble(), 51.0, 26.0)
        danar_iob.text = MainApp.gs(R.string.formatinsulinunits, pump.iob)
        if (pump.model != 0 || pump.protocol != 0 || pump.productCode != 0) {
            danar_firmware.text = String.format(MainApp.gs(R.string.danar_model), pump.model, pump.protocol, pump.productCode)
        } else {
            danar_firmware.text = "OLD"
        }
        danar_basalstep.text = pump.basalStep.toString()
        danar_bolusstep.text = pump.bolusStep.toString()
        danar_serialnumber.text = pump.serialNumber
        val status = ConfigBuilderPlugin.getPlugin().commandQueue.spannedStatus()
        if (status.toString() == "") {
            danar_queue.visibility = View.GONE
        } else {
            danar_queue.visibility = View.VISIBLE
            danar_queue.text = status
        }
        //hide user options button if not an RS pump or old firmware
        // also excludes pump with model 03 because of untested error
        val isKorean = DanaRKoreanPlugin.getPlugin().isEnabled(PluginType.PUMP)
        if (isKorean || danar_firmware.text === "OLD" || pump.model == 3) {
            danar_user_options.visibility = View.GONE
        }
    }
}

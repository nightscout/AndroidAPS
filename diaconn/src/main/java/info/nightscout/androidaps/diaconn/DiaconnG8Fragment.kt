package info.nightscout.androidaps.diaconn

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.activities.TDDStatsActivity
import info.nightscout.androidaps.diaconn.databinding.DiaconnG8FragmentBinding
import info.nightscout.androidaps.diaconn.events.EventDiaconnG8NewStatus
import info.nightscout.androidaps.events.EventExtendedBolusChange
import info.nightscout.androidaps.events.EventInitializationChanged
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.androidaps.events.EventTempBasalChange
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.Pump
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.queue.events.EventQueueChanged
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.WarnColors
import io.reactivex.rxkotlin.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

class DiaconnG8Fragment : DaggerFragment() {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var sp: SP
    @Inject lateinit var warnColors: WarnColors
    @Inject lateinit var dateUtil: DateUtil

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val loopHandler = Handler()
    private lateinit var refreshLoop: Runnable

    private var _binding: DiaconnG8FragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    init {
        refreshLoop = Runnable {
            activity?.runOnUiThread { updateGUI() }
            loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = DiaconnG8FragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.diaconnG8Pumpstatus.setBackgroundColor(resourceHelper.gc(R.color.colorInitializingBorder))
        binding.history.setOnClickListener { startActivity(Intent(context, info.nightscout.androidaps.diaconn.activities.DiaconnG8HistoryActivity::class.java)) }
        binding.stats.setOnClickListener { startActivity(Intent(context, TDDStatsActivity::class.java)) }
        binding.userOptions.setOnClickListener { startActivity(Intent(context, info.nightscout.androidaps.diaconn.activities.DiaconnG8UserOptionsActivity::class.java)) }
        binding.btconnection.setOnClickListener {
            aapsLogger.debug(LTag.PUMP, "Clicked connect to pump")
            diaconnG8Pump.lastConnection = 0
            commandQueue.readStatus("Clicked connect to pump", null)
        }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())
        disposable += rxBus
            .toObservable(EventInitializationChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventDiaconnG8NewStatus::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventTempBasalChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventQueueChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                when (it.status) {
                    EventPumpStatusChanged.Status.CONNECTING   ->
                        @Suppress("SetTextI18n")
                        binding.btconnection.text = "{fa-bluetooth-b spin} ${it.secondsElapsed}s"
                    EventPumpStatusChanged.Status.CONNECTED    ->
                        @Suppress("SetTextI18n")
                        binding.btconnection.text = "{fa-bluetooth}"
                    EventPumpStatusChanged.Status.DISCONNECTED ->
                        @Suppress("SetTextI18n")
                        binding.btconnection.text = "{fa-bluetooth-b}"
                    else                                       -> {}
                }
                if (it.getStatus(resourceHelper) != "") {
                    binding.diaconnG8Pumpstatus.text = it.getStatus(resourceHelper)
                    binding.diaconnG8Pumpstatuslayout.visibility = View.VISIBLE
                } else {
                    binding.diaconnG8Pumpstatuslayout.visibility = View.GONE
                }
            }, fabricPrivacy::logException)
        updateGUI()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
        loopHandler.removeCallbacks(refreshLoop)
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @SuppressLint("SetTextI18n")
    @Synchronized
    fun updateGUI() {
        if (_binding == null) return
        val pump = diaconnG8Pump
        val plugin: Pump = activePlugin.activePump
        if (pump.lastConnection != 0L) {
            val agoMsec = System.currentTimeMillis() - pump.lastConnection
            val agoMin = (agoMsec.toDouble() / 60.0 / 1000.0).toInt()
            binding.lastconnection.text = dateUtil.timeString(pump.lastConnection) + " (" + resourceHelper.gs(R.string.minago, agoMin) + ")"
            warnColors.setColor(binding.lastconnection, agoMin.toDouble(), 16.0, 31.0)
        }
        if (pump.lastBolusTime != 0L) {
            val agoMsec = System.currentTimeMillis() - pump.lastBolusTime
            val agoHours = agoMsec.toDouble() / 60.0 / 60.0 / 1000.0
            if (agoHours < 6)
            // max 6h back
                binding.lastbolus.text = dateUtil.timeString(pump.lastBolusTime) + " " + dateUtil.sinceString(pump.lastBolusTime, resourceHelper) + " " + resourceHelper.gs(R.string.formatinsulinunits, pump.lastBolusAmount)
            else
                binding.lastbolus.text = ""
        }

        val todayInsulinAmount = (pump.todayBaseAmount + pump.todaySnackAmount + pump.todayMealAmount)
        val todayInsulinLimitAmount = (pump.maxBasal.toInt() * 24) + pump.maxBolusePerDay.toInt()
        binding.dailyunits.text = resourceHelper.gs(R.string.reservoirvalue, todayInsulinAmount, todayInsulinLimitAmount)
        warnColors.setColor(binding.dailyunits, todayInsulinAmount, todayInsulinLimitAmount * 0.75, todayInsulinLimitAmount * 0.9)
        binding.basabasalrate.text = pump.baseInjAmount.toString() +" / "+ resourceHelper.gs(R.string.pump_basebasalrate, plugin.baseBasalRate)

        binding.tempbasal.text = diaconnG8Pump.temporaryBasalToString()
        binding.extendedbolus.text = diaconnG8Pump.extendedBolusToString()
        binding.reservoir.text = resourceHelper.gs(R.string.reservoirvalue, pump.systemRemainInsulin, 307)
        warnColors.setColorInverse(binding.reservoir, pump.systemRemainInsulin , 50.0, 20.0)
        binding.battery.text = "{fa-battery-" + pump.systemRemainBattery / 25  + "}" + " ("+ pump.systemRemainBattery + " %)"
        warnColors.setColorInverse(binding.battery, pump.systemRemainBattery.toDouble(), 51.0, 26.0)
        binding.firmware.text = resourceHelper.gs(R.string.diaconn_g8_pump) + "\nVersion: " + pump.majorVersion.toString() + "." +  pump.minorVersion.toString() + "\nCountry: "+pump.country.toString() + "\nProductType: "+ pump.productType.toString() + "\nManufacture: " + pump.makeYear + "." + pump.makeMonth + "." + pump.makeDay
        binding.basalstep.text = pump.basalStep.toString()
        binding.bolusstep.text = pump.bolusStep.toString()
        binding.serialNumber.text = pump.serialNo.toString()
        val status = commandQueue.spannedStatus()
        if (status.toString() == "") {
            binding.queue.visibility = View.GONE
        } else {
            binding.queue.visibility = View.VISIBLE
            binding.queue.text = status
        }

        binding.userOptions.visibility = View.VISIBLE
    }
}

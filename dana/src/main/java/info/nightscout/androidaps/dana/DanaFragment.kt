package info.nightscout.androidaps.dana

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.activities.TDDStatsActivity
import info.nightscout.androidaps.dana.databinding.DanarFragmentBinding
import info.nightscout.androidaps.dialogs.ProfileViewerDialog
import info.nightscout.androidaps.events.EventExtendedBolusChange
import info.nightscout.androidaps.events.EventInitializationChanged
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.androidaps.events.EventTempBasalChange
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.PumpInterface
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.queue.events.EventQueueChanged
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.WarnColors
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.extensions.toVisibility
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

class DanaFragment : DaggerFragment() {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var danaPump: DanaPump
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var sp: SP
    @Inject lateinit var warnColors: WarnColors
    @Inject lateinit var dateUtil: DateUtil

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val loopHandler = Handler()
    private lateinit var refreshLoop: Runnable

    private var _binding: DanarFragmentBinding? = null

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
        _binding = DanarFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.danaPumpstatus.setBackgroundColor(resourceHelper.gc(R.color.colorInitializingBorder))

        binding.history.setOnClickListener { startActivity(Intent(context, info.nightscout.androidaps.dana.activities.DanaHistoryActivity::class.java)) }
        binding.viewprofile.setOnClickListener {
            val profile = danaPump.createConvertedProfile()?.getDefaultProfile()
                ?: return@setOnClickListener
            val profileName = danaPump.createConvertedProfile()?.getDefaultProfileName()
                ?: return@setOnClickListener
            val args = Bundle()
            args.putLong("time", DateUtil.now())
            args.putInt("mode", ProfileViewerDialog.Mode.CUSTOM_PROFILE.ordinal)
            args.putString("customProfile", profile.data.toString())
            args.putString("customProfileUnits", profile.units)
            args.putString("customProfileName", profileName)
            val pvd = ProfileViewerDialog()
            pvd.arguments = args
            pvd.show(childFragmentManager, "ProfileViewDialog")
        }
        binding.stats.setOnClickListener { startActivity(Intent(context, TDDStatsActivity::class.java)) }
        binding.userOptions.setOnClickListener { startActivity(Intent(context, info.nightscout.androidaps.dana.activities.DanaUserOptionsActivity::class.java)) }
        binding.btconnection.setOnClickListener {
            aapsLogger.debug(LTag.PUMP, "Clicked connect to pump")
            danaPump.lastConnection = 0
            commandQueue.readStatus("Clicked connect to pump", null)
        }
        if (activePlugin.activePump.pumpDescription.pumpType == PumpType.DanaRS)
            binding.btconnection.setOnLongClickListener {
                activity?.let {
                    OKDialog.showConfirmation(it, resourceHelper.gs(R.string.resetpairing)) {
                        aapsLogger.error("USER ENTRY: Clearing pairing keys !!!")
                        (activePlugin.activePump as DanaPumpInterface).clearPairing()
                    }
                }
                true
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
            .toObservable(info.nightscout.androidaps.dana.events.EventDanaRNewStatus::class.java)
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
                when {
                    it.status == EventPumpStatusChanged.Status.CONNECTING   ->
                        @Suppress("SetTextI18n")
                        binding.btconnection.text = "{fa-bluetooth-b spin} ${it.secondsElapsed}s"
                    it.status == EventPumpStatusChanged.Status.CONNECTED    ->
                        @Suppress("SetTextI18n")
                        binding.btconnection.text = "{fa-bluetooth}"
                    it.status == EventPumpStatusChanged.Status.DISCONNECTED ->
                        @Suppress("SetTextI18n")
                        binding.btconnection.text = "{fa-bluetooth-b}"
                }
                if (it.getStatus(resourceHelper) != "") {
                    binding.danaPumpstatus.text = it.getStatus(resourceHelper)
                    binding.danaPumpstatuslayout.visibility = View.VISIBLE
                } else {
                    binding.danaPumpstatuslayout.visibility = View.GONE
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

    @Synchronized
    fun updateGUI() {
        if (_binding == null) return
        val pump = danaPump
        val plugin: PumpInterface = activePlugin.activePump
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
                binding.lastbolus.text = dateUtil.timeString(pump.lastBolusTime) + " " + DateUtil.sinceString(pump.lastBolusTime, resourceHelper) + " " + resourceHelper.gs(R.string.formatinsulinunits, pump.lastBolusAmount)
            else
                binding.lastbolus.text = ""
        }

        binding.dailyunits.text = resourceHelper.gs(R.string.reservoirvalue, pump.dailyTotalUnits, pump.maxDailyTotalUnits)
        warnColors.setColor(binding.dailyunits, pump.dailyTotalUnits, pump.maxDailyTotalUnits * 0.75, pump.maxDailyTotalUnits * 0.9)
        binding.basabasalrate.text = "( " + (pump.activeProfile + 1) + " )  " + resourceHelper.gs(R.string.pump_basebasalrate, plugin.baseBasalRate)
        // DanaRPlugin, DanaRKoreanPlugin
        if (activePlugin.activePump.isFakingTempsByExtendedBoluses == true) {
            binding.tempbasal.text = activePlugin.activeTreatments.getRealTempBasalFromHistory(System.currentTimeMillis())?.toStringFull()
                ?: ""
        } else {
            // v2 plugin
            binding.tempbasal.text = activePlugin.activeTreatments.getTempBasalFromHistory(System.currentTimeMillis())?.toStringFull()
                ?: ""
        }
        binding.extendedbolus.text = activePlugin.activeTreatments.getExtendedBolusFromHistory(System.currentTimeMillis())?.toString()
            ?: ""
        binding.reservoir.text = resourceHelper.gs(R.string.reservoirvalue, pump.reservoirRemainingUnits, 300)
        warnColors.setColorInverse(binding.reservoir, pump.reservoirRemainingUnits, 50.0, 20.0)
        binding.battery.text = "{fa-battery-" + pump.batteryRemaining / 25 + "}"
        warnColors.setColorInverse(binding.battery, pump.batteryRemaining.toDouble(), 51.0, 26.0)
        binding.iob.text = resourceHelper.gs(R.string.formatinsulinunits, pump.iob)
        binding.firmware.text = resourceHelper.gs(R.string.dana_model, pump.modelFriendlyName(), pump.hwModel, pump.protocol, pump.productCode)
        binding.basalstep.text = pump.basalStep.toString()
        binding.bolusstep.text = pump.bolusStep.toString()
        binding.serialNumber.text = pump.serialNumber
        val status = commandQueue.spannedStatus()
        if (status.toString() == "") {
            binding.queue.visibility = View.GONE
        } else {
            binding.queue.visibility = View.VISIBLE
            binding.queue.text = status
        }
        //hide user options button if not an RS pump or old firmware
        // also excludes pump with model 03 because of untested error
        binding.userOptions.visibility = (pump.hwModel != 1 && pump.protocol != 0x00).toVisibility()
    }
}

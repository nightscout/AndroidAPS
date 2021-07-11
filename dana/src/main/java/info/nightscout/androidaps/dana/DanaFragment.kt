package info.nightscout.androidaps.dana

import android.annotation.SuppressLint
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
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.Pump
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.queue.events.EventQueueChanged
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.userEntry.UserEntryMapper.Action
import info.nightscout.androidaps.utils.userEntry.UserEntryMapper.Sources
import info.nightscout.androidaps.utils.WarnColors
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.interfaces.Dana
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import javax.inject.Inject

class DanaFragment : DaggerFragment() {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var danaPump: DanaPump
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var sp: SP
    @Inject lateinit var warnColors: WarnColors
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uel: UserEntryLogger

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
            val profile = danaPump.createConvertedProfile()?.getDefaultProfileJson()
                ?: return@setOnClickListener
            val profileName = danaPump.createConvertedProfile()?.getDefaultProfileName()
                ?: return@setOnClickListener
            ProfileViewerDialog().also { pvd ->
                pvd.arguments = Bundle().also { args ->
                    args.putLong("time", dateUtil.now())
                    args.putInt("mode", ProfileViewerDialog.Mode.CUSTOM_PROFILE.ordinal)
                    args.putString("customProfile", profile.toString())
                    args.putString("customProfileName", profileName)
                }

            }.show(childFragmentManager, "ProfileViewDialog")
        }
        binding.stats.setOnClickListener { startActivity(Intent(context, TDDStatsActivity::class.java)) }
        binding.userOptions.setOnClickListener { startActivity(Intent(context, info.nightscout.androidaps.dana.activities.DanaUserOptionsActivity::class.java)) }
        binding.btconnection.setOnClickListener {
            aapsLogger.debug(LTag.PUMP, "Clicked connect to pump")
            danaPump.reset()
            commandQueue.readStatus("Clicked connect to pump", null)
        }
        if (activePlugin.activePump.pumpDescription.pumpType == PumpType.DANA_RS)
            binding.btconnection.setOnLongClickListener {
                activity?.let {
                    OKDialog.showConfirmation(it, resourceHelper.gs(R.string.resetpairing)) {
                        uel.log(Action.CLEAR_PAIRING_KEYS, Sources.Dana)
                        (activePlugin.activePump as Dana).clearPairing()
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
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(info.nightscout.androidaps.dana.events.EventDanaRNewStatus::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventTempBasalChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventQueueChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(aapsSchedulers.main)
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

                    else                                       -> {
                    }
                }
                if (it.getStatus(resourceHelper) != "") {
                    binding.danaPumpstatus.text = it.getStatus(resourceHelper)
                    binding.danaPumpstatuslayout.visibility = View.VISIBLE
                } else {
                    binding.danaPumpstatuslayout.visibility = View.GONE
                }
            }, fabricPrivacy::logException)
        binding.danaPumpstatus.text = ""
        binding.danaPumpstatuslayout.visibility = View.GONE
        @Suppress("SetTextI18n")
        binding.btconnection.text = "{fa-bluetooth-b}"
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
        val pump = danaPump
        val plugin: Pump = activePlugin.activePump
        if (pump.lastConnection != 0L) {
            val agoMilliseconds = System.currentTimeMillis() - pump.lastConnection
            val agoMin = (agoMilliseconds.toDouble() / 60.0 / 1000.0).toInt()
            binding.lastconnection.text = dateUtil.timeString(pump.lastConnection) + " (" + resourceHelper.gs(R.string.minago, agoMin) + ")"
            warnColors.setColor(binding.lastconnection, agoMin.toDouble(), 16.0, 31.0)
        }
        if (pump.lastBolusTime != 0L) {
            val agoMilliseconds = System.currentTimeMillis() - pump.lastBolusTime
            val agoHours = agoMilliseconds.toDouble() / 60.0 / 60.0 / 1000.0
            if (agoHours < 6)
            // max 6h back
                binding.lastbolus.text = dateUtil.timeString(pump.lastBolusTime) + " " + dateUtil.sinceString(pump.lastBolusTime, resourceHelper) + " " + resourceHelper.gs(R.string.formatinsulinunits, pump.lastBolusAmount)
            else
                binding.lastbolus.text = ""
        }

        binding.dailyunits.text = resourceHelper.gs(R.string.reservoirvalue, pump.dailyTotalUnits, pump.maxDailyTotalUnits)
        warnColors.setColor(binding.dailyunits, pump.dailyTotalUnits, pump.maxDailyTotalUnits * 0.75, pump.maxDailyTotalUnits * 0.9)
        binding.basabasalrate.text = "( " + (pump.activeProfile + 1) + " )  " + resourceHelper.gs(R.string.pump_basebasalrate, plugin.baseBasalRate)
        // DanaRPlugin, DanaRKoreanPlugin
        binding.tempbasal.text = danaPump.temporaryBasalToString()
        binding.extendedbolus.text = danaPump.extendedBolusToString()
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

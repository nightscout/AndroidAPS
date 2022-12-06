package info.nightscout.pump.dana

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import dagger.android.support.DaggerFragment
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.pump.Dana
import info.nightscout.interfaces.pump.Pump
import info.nightscout.interfaces.pump.WarnColors
import info.nightscout.interfaces.pump.defs.PumpType
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.interfaces.userEntry.UserEntryMapper.Action
import info.nightscout.interfaces.userEntry.UserEntryMapper.Sources
import info.nightscout.pump.dana.activities.DanaHistoryActivity
import info.nightscout.pump.dana.activities.DanaUserOptionsActivity
import info.nightscout.pump.dana.databinding.DanarFragmentBinding
import info.nightscout.pump.dana.events.EventDanaRNewStatus
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventExtendedBolusChange
import info.nightscout.rx.events.EventInitializationChanged
import info.nightscout.rx.events.EventPumpStatusChanged
import info.nightscout.rx.events.EventQueueChanged
import info.nightscout.rx.events.EventTempBasalChange
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.extensions.toVisibility
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class DanaFragment : DaggerFragment() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var danaPump: DanaPump
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var sp: SP
    @Inject lateinit var warnColors: WarnColors
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var uiInteraction: UiInteraction

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var refreshLoop: Runnable
    private var pumpStatus = ""
    private var pumpStatusIcon = "{fa-bluetooth-b}"

    private var _binding: DanarFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    init {
        refreshLoop = Runnable {
            activity?.runOnUiThread { updateGUI() }
            handler.postDelayed(refreshLoop, T.mins(1).msecs())
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DanarFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.history.setOnClickListener { startActivity(Intent(context, DanaHistoryActivity::class.java)) }
        binding.viewProfile.setOnClickListener {
            val profile = danaPump.createConvertedProfile()?.getDefaultProfileJson()
                ?: return@setOnClickListener
            val profileName = danaPump.createConvertedProfile()?.getDefaultProfileName()
                ?: return@setOnClickListener
            uiInteraction.runProfileViewerDialog(
                fragmentManager = childFragmentManager,
                time = dateUtil.now(),
                mode = UiInteraction.Mode.CUSTOM_PROFILE,
                customProfile = profile.toString(),
                customProfileName = profileName
            )
        }
        binding.stats.setOnClickListener { startActivity(Intent(context, uiInteraction.tddStatsActivity)) }
        binding.userOptions.setOnClickListener { startActivity(Intent(context, DanaUserOptionsActivity::class.java)) }
        binding.btConnectionLayout.setOnClickListener {
            aapsLogger.debug(LTag.PUMP, "Clicked connect to pump")
            danaPump.reset()
            commandQueue.readStatus(rh.gs(info.nightscout.core.ui.R.string.clicked_connect_to_pump), null)
        }
        if (activePlugin.activePump.pumpDescription.pumpType == PumpType.DANA_RS ||
            activePlugin.activePump.pumpDescription.pumpType == PumpType.DANA_I
        )
            binding.btConnectionLayout.setOnLongClickListener {
                activity?.let {
                    OKDialog.showConfirmation(it, rh.gs(R.string.resetpairing)) {
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
        handler.postDelayed(refreshLoop, T.mins(1).msecs())
        disposable += rxBus
            .toObservable(EventInitializationChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventDanaRNewStatus::class.java)
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
                           pumpStatusIcon = when (it.status) {
                               EventPumpStatusChanged.Status.CONNECTING   ->
                                   "{fa-bluetooth-b spin} ${it.secondsElapsed}s"

                               EventPumpStatusChanged.Status.CONNECTED    ->
                                   "{fa-bluetooth}"

                               EventPumpStatusChanged.Status.DISCONNECTED ->
                                   "{fa-bluetooth-b}"

                               else                                       ->
                                   "{fa-bluetooth-b}"
                           }
                           binding.btConnection.text = pumpStatusIcon
                           pumpStatus = it.getStatus(requireContext())
                           binding.pumpStatus.text = pumpStatus
                           binding.pumpStatusLayout.visibility = (pumpStatus != "").toVisibility()
                       }, fabricPrivacy::logException)

        pumpStatus = ""
        pumpStatusIcon = "{fa-bluetooth-b}"
        updateGUI()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
        handler.removeCallbacks(refreshLoop)
        pumpStatus = ""
        pumpStatusIcon = "{fa-bluetooth-b}"
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
        binding.btConnection.text = pumpStatusIcon
        binding.pumpStatus.text = pumpStatus
        binding.pumpStatusLayout.visibility = (pumpStatus != "").toVisibility()
        binding.queue.text = commandQueue.spannedStatus()
        binding.queueStatusLayout.visibility = (commandQueue.spannedStatus().toString() != "").toVisibility()
        val pump = danaPump
        val plugin: Pump = activePlugin.activePump
        if (pump.lastConnection != 0L) {
            val agoMilliseconds = System.currentTimeMillis() - pump.lastConnection
            val agoMin = (agoMilliseconds.toDouble() / 60.0 / 1000.0).toInt()
            binding.lastConnection.text = dateUtil.timeString(pump.lastConnection) + " (" + rh.gs(info.nightscout.shared.R.string.minago, agoMin) + ")"
            warnColors.setColor(binding.lastConnection, agoMin.toDouble(), 16.0, 31.0)
        }
        if (pump.lastBolusTime != 0L) {
            val agoMilliseconds = System.currentTimeMillis() - pump.lastBolusTime
            val agoHours = agoMilliseconds.toDouble() / 60.0 / 60.0 / 1000.0
            if (agoHours < 6)
            // max 6h back
                binding.lastBolus.text = dateUtil.timeString(pump.lastBolusTime) + " " + dateUtil.sinceString(pump.lastBolusTime, rh) + " " + rh.gs(info.nightscout.interfaces.R.string.format_insulin_units, pump.lastBolusAmount)
            else
                binding.lastBolus.text = ""
        }

        binding.dailyUnits.text = rh.gs(info.nightscout.core.ui.R.string.reservoir_value, pump.dailyTotalUnits, pump.maxDailyTotalUnits)
        warnColors.setColor(binding.dailyUnits, pump.dailyTotalUnits, pump.maxDailyTotalUnits * 0.75, pump.maxDailyTotalUnits * 0.9)
        binding.baseBasalRate.text = "( " + (pump.activeProfile + 1) + " )  " + rh.gs(info.nightscout.core.ui.R.string.pump_base_basal_rate, plugin.baseBasalRate)
        // DanaRPlugin, DanaRKoreanPlugin
        binding.tempbasal.text = danaPump.temporaryBasalToString()
        binding.extendedbolus.text = danaPump.extendedBolusToString()
        binding.reservoir.text = rh.gs(info.nightscout.core.ui.R.string.reservoir_value, pump.reservoirRemainingUnits, 300)
        warnColors.setColorInverse(binding.reservoir, pump.reservoirRemainingUnits, 50.0, 20.0)
        binding.battery.text = "{fa-battery-" + pump.batteryRemaining / 25 + "}"
        warnColors.setColorInverse(binding.battery, pump.batteryRemaining.toDouble(), 51.0, 26.0)
        binding.firmware.text = rh.gs(R.string.dana_model, pump.modelFriendlyName(), pump.hwModel, pump.protocol, pump.productCode)
        binding.basalBolusStep.text = pump.basalStep.toString() + "/" + pump.bolusStep.toString()
        binding.serialNumber.text = pump.serialNumber
        val icon = if (danaPump.pumpType() == PumpType.DANA_I) R.drawable.ic_dana_i else R.drawable.ic_dana_rs
        binding.danaIcon.setImageDrawable(context?.let { ContextCompat.getDrawable(it, icon) })
        //hide user options button if not an RS pump or old firmware
        // also excludes pump with model 03 because of untested error
        binding.userOptions.visibility = (pump.hwModel != 1 && pump.protocol != 0x00).toVisibility()
    }
}

package app.aaps.pump.medtronic

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.WarnColors
import app.aaps.core.interfaces.pump.defs.PumpDeviceState
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventExtendedBolusChange
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventQueueChanged
import app.aaps.core.interfaces.rx.events.EventRefreshButtonState
import app.aaps.core.interfaces.rx.events.EventTempBasalChange
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.pump.common.events.EventRileyLinkDeviceStatusChange
import app.aaps.pump.common.extensions.stringResource
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkServiceState
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import app.aaps.pump.common.hw.rileylink.dialog.RileyLinkStatusActivity
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import app.aaps.pump.medtronic.databinding.MedtronicFragmentBinding
import app.aaps.pump.medtronic.defs.BatteryType
import app.aaps.pump.medtronic.defs.MedtronicCommandType
import app.aaps.pump.medtronic.dialog.MedtronicHistoryActivity
import app.aaps.pump.medtronic.driver.MedtronicPumpStatus
import app.aaps.pump.medtronic.events.EventMedtronicPumpConfigurationChanged
import app.aaps.pump.medtronic.events.EventMedtronicPumpValuesChanged
import app.aaps.pump.medtronic.util.MedtronicUtil
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.Locale
import javax.inject.Inject

class MedtronicFragment : DaggerFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var medtronicPumpPlugin: MedtronicPumpPlugin
    @Inject lateinit var warnColors: WarnColors
    @Inject lateinit var medtronicUtil: MedtronicUtil
    @Inject lateinit var medtronicPumpStatus: MedtronicPumpStatus
    @Inject lateinit var rileyLinkServiceData: RileyLinkServiceData
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var pumpSync: PumpSync

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private var refreshLoop: Runnable

    init {
        refreshLoop = Runnable {
            activity?.runOnUiThread { updateGUI() }
            handler.postDelayed(refreshLoop, T.mins(1).msecs())
        }
    }

    private var _binding: MedtronicFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        MedtronicFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rlStatus.text = rh.gs(RileyLinkServiceState.NotStarted.resourceId)

        binding.pumpStatusIcon.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor))
        @SuppressLint("SetTextI18n")
        binding.pumpStatusIcon.text = "{fa-bed}"

        binding.history.setOnClickListener {
            if (medtronicPumpPlugin.rileyLinkService?.verifyConfiguration() == true) {
                startActivity(Intent(context, MedtronicHistoryActivity::class.java))
            } else {
                displayNotConfiguredDialog()
            }
        }

        binding.refresh.setOnClickListener {
            if (medtronicPumpPlugin.rileyLinkService?.verifyConfiguration() != true) {
                displayNotConfiguredDialog()
            } else {
                binding.refresh.isEnabled = false
                medtronicPumpPlugin.resetStatusState()
                commandQueue.readStatus(rh.gs(R.string.clicked_refresh), object : Callback() {
                    override fun run() {
                        activity?.runOnUiThread { if (_binding != null) binding.refresh.isEnabled = true }
                    }
                })
            }
        }

        binding.stats.setOnClickListener {
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
        handler.postDelayed(refreshLoop, T.mins(1).msecs())
        disposable += rxBus
            .toObservable(EventRefreshButtonState::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ binding.refresh.isEnabled = it.newState }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventRileyLinkDeviceStatusChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           aapsLogger.debug(LTag.PUMP, "onStatusEvent(EventRileyLinkDeviceStatusChange): $it")
                           setDeviceStatus()
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventMedtronicPumpValuesChanged::class.java)
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
            .toObservable(EventMedtronicPumpConfigurationChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           aapsLogger.debug(LTag.PUMP, "EventMedtronicPumpConfigurationChanged triggered")
                           medtronicPumpPlugin.rileyLinkService?.verifyConfiguration()
                           updateGUI()
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventQueueChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI() }, fabricPrivacy::logException)

        updateGUI()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
        handler.removeCallbacks(refreshLoop)
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @SuppressLint("SetTextI18n")
    @Synchronized
    private fun setDeviceStatus() {
        val resourceId = rileyLinkServiceData.rileyLinkServiceState.resourceId
        val rileyLinkError = rileyLinkServiceData.rileyLinkError
        binding.rlStatus.text =
            when {
                rileyLinkServiceData.rileyLinkServiceState == RileyLinkServiceState.NotStarted -> rh.gs(resourceId)
                rileyLinkServiceData.rileyLinkServiceState.isConnecting()                      -> "{fa-bluetooth-b spin}   " + rh.gs(resourceId)
                rileyLinkServiceData.rileyLinkServiceState.isError() && rileyLinkError == null -> "{fa-bluetooth-b}   " + rh.gs(resourceId)
                rileyLinkServiceData.rileyLinkServiceState.isError() && rileyLinkError != null -> "{fa-bluetooth-b}   " + rh.gs(rileyLinkError.getResourceId(RileyLinkTargetDevice.MedtronicPump))
                else                                                                           -> "{fa-bluetooth-b}   " + rh.gs(resourceId)
            }
        binding.rlStatus.setTextColor(rh.gac(context, if (rileyLinkError != null) app.aaps.core.ui.R.attr.warningColor else app.aaps.core.ui.R.attr.defaultTextColor))

        binding.errors.text =
            rileyLinkServiceData.rileyLinkError?.let {
                rh.gs(it.getResourceId(RileyLinkTargetDevice.MedtronicPump))
            } ?: "-"

        when (medtronicPumpStatus.pumpDeviceState) {
            PumpDeviceState.Sleeping             ->
                binding.pumpStatusIcon.text = "{fa-bed}   " // + pumpStatus.pumpDeviceState.name());

            PumpDeviceState.NeverContacted,
            PumpDeviceState.WakingUp,
            PumpDeviceState.PumpUnreachable,
            PumpDeviceState.ErrorWhenCommunicating,
            PumpDeviceState.TimeoutWhenCommunicating,
            PumpDeviceState.InvalidConfiguration ->
                binding.pumpStatusIcon.text = " " + rh.gs(medtronicPumpStatus.pumpDeviceState.stringResource())

            PumpDeviceState.Active               -> {
                val cmd = medtronicUtil.getCurrentCommand()
                if (cmd == null)
                    binding.pumpStatusIcon.text = " " + rh.gs(medtronicPumpStatus.pumpDeviceState.stringResource())
                else {
                    aapsLogger.debug(LTag.PUMP, "Command: $cmd")
                    val cmdResourceId = cmd.resourceId //!!
                    if (cmd == MedtronicCommandType.GetHistoryData) {
                        binding.pumpStatusIcon.text = medtronicUtil.frameNumber?.let {
                            rh.gs(cmdResourceId!!, medtronicUtil.pageNumber, medtronicUtil.frameNumber)
                        }
                            ?: rh.gs(R.string.medtronic_cmd_desc_get_history_request, medtronicUtil.pageNumber)
                    } else {
                        binding.pumpStatusIcon.text = " " + (cmdResourceId?.let { rh.gs(it) }
                            ?: cmd.commandDescription)
                    }
                }
            }

            // else                                 ->
            //     aapsLogger.warn(LTag.PUMP, "Unknown pump state: " + medtronicPumpStatus.pumpDeviceState)
        }

        val status = commandQueue.spannedStatus()
        if (status.toString() == "") {
            binding.queue.visibility = View.GONE
        } else {
            binding.queue.visibility = View.VISIBLE
            binding.queue.text = status
        }
    }

    private fun displayNotConfiguredDialog() {
        context?.let {
            OKDialog.show(
                it, rh.gs(R.string.medtronic_warning),
                rh.gs(R.string.medtronic_error_operation_not_possible_no_configuration)
            )
        }
    }

    // GUI functions
    @SuppressLint("SetTextI18n")
    @Synchronized
    fun updateGUI() {
        if (_binding == null) return

        setDeviceStatus()

        // last connection
        if (medtronicPumpStatus.lastConnection != 0L) {
            val minAgo = dateUtil.minAgo(rh, medtronicPumpStatus.lastConnection)
            val min = (System.currentTimeMillis() - medtronicPumpStatus.lastConnection) / 1000 / 60
            if (medtronicPumpStatus.lastConnection + 60 * 1000 > System.currentTimeMillis()) {
                binding.lastConnection.setText(R.string.medtronic_pump_connected_now)
                binding.lastConnection.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor))
            } else if (medtronicPumpStatus.lastConnection + 30 * 60 * 1000 < System.currentTimeMillis()) {

                if (min < 60) {
                    binding.lastConnection.text = rh.gs(app.aaps.core.interfaces.R.string.minago, min)
                } else if (min < 1440) {
                    val h = (min / 60).toInt()
                    binding.lastConnection.text = (rh.gq(app.aaps.pump.common.hw.rileylink.R.plurals.duration_hours, h, h) + " "
                        + rh.gs(R.string.ago))
                } else {
                    val h = (min / 60).toInt()
                    val d = h / 24
                    // h = h - (d * 24);
                    binding.lastConnection.text = (rh.gq(app.aaps.pump.common.hw.rileylink.R.plurals.duration_days, d, d) + " "
                        + rh.gs(R.string.ago))
                }
                binding.lastConnection.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.warningColor))
            } else {
                binding.lastConnection.text = minAgo
                binding.lastConnection.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor))
            }
        }

        // last bolus
        val bolus = medtronicPumpStatus.lastBolusAmount
        val bolusTime = medtronicPumpStatus.lastBolusTime
        if (bolus != null && bolusTime != null) {
            val agoMsc = System.currentTimeMillis() - bolusTime.time
            val bolusMinAgo = agoMsc.toDouble() / 60.0 / 1000.0
            val unit = rh.gs(app.aaps.core.ui.R.string.insulin_unit_shortname)
            val ago = when {
                agoMsc < 60 * 1000 -> rh.gs(R.string.medtronic_pump_connected_now)
                bolusMinAgo < 60   -> dateUtil.minAgo(rh, bolusTime.time)
                else               -> dateUtil.hourAgo(bolusTime.time, rh)
            }
            binding.lastBolus.text = rh.gs(R.string.mdt_last_bolus, bolus, unit, ago)
        } else {
            binding.lastBolus.text = ""
        }

        // base basal rate
        binding.baseBasalRate.text = ("(" + medtronicPumpStatus.activeProfileName + ")  "
            + rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, medtronicPumpPlugin.baseBasalRate))

        // TBR
        var tbrStr = ""
        val tbrRemainingTime: Int? = medtronicPumpStatus.tbrRemainingTime

        if (tbrRemainingTime != null) {
            tbrStr = rh.gs(R.string.mdt_tbr_remaining, medtronicPumpStatus.tempBasalAmount, tbrRemainingTime)
        }
        binding.tempBasal.text = tbrStr

        // battery
        if (medtronicPumpStatus.batteryType == BatteryType.None || medtronicPumpStatus.batteryVoltage == null) {
            binding.pumpStateBattery.text = "{fa-battery-" + medtronicPumpStatus.batteryRemaining / 25 + "}  "
        } else {
            binding.pumpStateBattery.text =
                "{fa-battery-" + medtronicPumpStatus.batteryRemaining / 25 + "}  " + medtronicPumpStatus.batteryRemaining + "%" + String.format(Locale.getDefault(), "  (%.2f V)", medtronicPumpStatus.batteryVoltage)
        }
        warnColors.setColorInverse(binding.pumpStateBattery, medtronicPumpStatus.batteryRemaining.toDouble(), 25, 10)

        // reservoir
        binding.reservoir.text = rh.gs(app.aaps.core.ui.R.string.reservoir_value, medtronicPumpStatus.reservoirRemainingUnits, medtronicPumpStatus.reservoirFullUnits)
        warnColors.setColorInverse(binding.reservoir, medtronicPumpStatus.reservoirRemainingUnits, 50, 20)

        medtronicPumpPlugin.rileyLinkService?.verifyConfiguration()
        binding.errors.text = medtronicPumpStatus.errorInfo

        if (rileyLinkServiceData.showBatteryLevel) {
            binding.rlBatteryView.visibility = View.VISIBLE
            binding.rlBatteryLabel.visibility = View.VISIBLE
            binding.rlBatteryState.visibility = View.VISIBLE
            binding.rlBatteryLayout.visibility = View.VISIBLE
            binding.rlBatterySemicolon.visibility = View.VISIBLE
            binding.rlBatteryState.text =
                if (rileyLinkServiceData.batteryLevel == null) " ?"
                else "{fa-battery-${rileyLinkServiceData.batteryLevel!! / 25}}  ${rileyLinkServiceData.batteryLevel}%"
        } else {
            binding.rlBatteryView.visibility = View.GONE
            binding.rlBatteryLabel.visibility = View.GONE
            binding.rlBatteryState.visibility = View.GONE
            binding.rlBatteryLayout.visibility = View.GONE
            binding.rlBatterySemicolon.visibility = View.GONE
        }

    }
}

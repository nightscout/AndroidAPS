package info.nightscout.pump.combo

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.aaps.core.main.utils.fabric.FabricPrivacy
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventQueueChanged
import app.aaps.core.interfaces.utils.DateUtil
import dagger.android.support.DaggerFragment
import info.nightscout.pump.combo.data.ComboErrorUtil
import info.nightscout.pump.combo.data.ComboErrorUtil.DisplayType
import info.nightscout.pump.combo.databinding.CombopumpFragmentBinding
import info.nightscout.pump.combo.events.EventComboPumpUpdateGUI
import info.nightscout.pump.combo.ruffyscripter.PumpState
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class ComboFragment : DaggerFragment() {

    @Inject lateinit var comboPlugin: ComboPlugin
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var errorUtil: ComboErrorUtil

    private val disposable = CompositeDisposable()

    private var _binding: CombopumpFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        CombopumpFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    @Synchronized override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventComboPumpUpdateGUI::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventQueueChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        binding.comboRefreshButton.setOnClickListener {
            binding.comboRefreshButton.isEnabled = false
            commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.user_request), object : Callback() {
                override fun run() {
                    activity?.runOnUiThread { binding.comboRefreshButton.isEnabled = true }
                }
            })
        }
        updateGui()
    }

    @Synchronized override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @SuppressLint("SetTextI18n")
    fun updateGui() {
        _binding ?: return
        // state
        binding.comboState.text = comboPlugin.stateSummary
        val ps = comboPlugin.pump.state
        if (ps.insulinState == PumpState.EMPTY || ps.batteryState == PumpState.EMPTY || ps.activeAlert != null && ps.activeAlert.errorCode != null) {
            binding.comboState.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.warningColor))
            binding.comboState.setTypeface(null, Typeface.BOLD)
        } else if (comboPlugin.pump.state.suspended
            || ps.activeAlert != null && ps.activeAlert.warningCode != null
        ) {
            binding.comboState.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.omniYellowColor))
            binding.comboState.setTypeface(null, Typeface.BOLD)
        } else {
            binding.comboState.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor))
            binding.comboState.setTypeface(null, Typeface.NORMAL)
        }

        // activity
        val activity = comboPlugin.pump.activity
        when {
            activity != null            -> {
                binding.comboActivity.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor))
                binding.comboActivity.textSize = 14f
                binding.comboActivity.text = activity
            }

            commandQueue.size() > 0     -> {
                binding.comboActivity.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor))
                binding.comboActivity.textSize = 14f
                binding.comboActivity.text = ""
            }

            comboPlugin.isInitialized() -> {
                binding.comboActivity.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor))
                binding.comboActivity.textSize = 20f
                binding.comboActivity.text = "{fa-bed}"
            }

            else                        -> {
                binding.comboActivity.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.warningColor))
                binding.comboActivity.textSize = 14f
                binding.comboActivity.text = rh.gs(app.aaps.core.ui.R.string.pump_unreachable)
            }
        }
        if (comboPlugin.isInitialized()) {
            // battery
            binding.comboPumpstateBattery.textSize = 20f
            when (ps.batteryState) {
                PumpState.EMPTY -> {
                    binding.comboPumpstateBattery.text = "{fa-battery-empty}"
                    binding.comboPumpstateBattery.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.warningColor))
                }

                PumpState.LOW   -> {
                    binding.comboPumpstateBattery.text = "{fa-battery-quarter}"
                    binding.comboPumpstateBattery.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.omniYellowColor))
                }

                else            -> {
                    binding.comboPumpstateBattery.text = "{fa-battery-full}"
                    binding.comboPumpstateBattery.setTextColor(Color.WHITE)
                }
            }

            // reservoir
            val reservoirLevel = comboPlugin.pump.reservoirLevel
            when {
                reservoirLevel != -1               -> binding.comboInsulinstate.text = reservoirLevel.toString() + " " + rh.gs(app.aaps.core.ui.R.string.insulin_unit_shortname)
                ps.insulinState == PumpState.LOW   -> binding.comboInsulinstate.text = rh.gs(R.string.combo_reservoir_low)
                ps.insulinState == PumpState.EMPTY -> binding.comboInsulinstate.text = rh.gs(R.string.combo_reservoir_empty)
                else                               -> binding.comboInsulinstate.text = rh.gs(R.string.combo_reservoir_normal)
            }
            when (ps.insulinState) {
                PumpState.UNKNOWN -> {
                    binding.comboInsulinstate.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor))
                    binding.comboInsulinstate.setTypeface(null, Typeface.NORMAL)
                }

                PumpState.LOW     -> {
                    binding.comboInsulinstate.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.omniYellowColor))
                    binding.comboInsulinstate.setTypeface(null, Typeface.BOLD)
                }

                PumpState.EMPTY   -> {
                    binding.comboInsulinstate.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.warningColor))
                    binding.comboInsulinstate.setTypeface(null, Typeface.BOLD)
                }

                else              -> {
                    binding.comboInsulinstate.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor))
                    binding.comboInsulinstate.setTypeface(null, Typeface.NORMAL)
                }
            }

            // last connection
            val minAgo = dateUtil.minAgo(rh, comboPlugin.pump.lastSuccessfulCmdTime)
            val min = (System.currentTimeMillis() - comboPlugin.pump.lastSuccessfulCmdTime) / 1000 / 60
            when {
                comboPlugin.pump.lastSuccessfulCmdTime + 60 * 1000 > System.currentTimeMillis()      -> {
                    binding.comboLastconnection.setText(R.string.combo_pump_connected_now)
                    binding.comboLastconnection.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor))
                }

                comboPlugin.pump.lastSuccessfulCmdTime + 30 * 60 * 1000 < System.currentTimeMillis() -> {
                    binding.comboLastconnection.text = rh.gs(R.string.combo_no_pump_connection, min)
                    binding.comboLastconnection.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.warningColor))
                }

                else                                                                                 -> {
                    binding.comboLastconnection.text = minAgo
                    binding.comboLastconnection.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor))
                }
            }

            // last bolus
            val bolus = comboPlugin.pump.lastBolus
            if (bolus != null) {
                val agoMsc = System.currentTimeMillis() - bolus.timestamp
                val bolusMinAgo = agoMsc / 60.0 / 1000.0
                val unit = rh.gs(app.aaps.core.ui.R.string.insulin_unit_shortname)
                val ago: String = when {
                    agoMsc < 60 * 1000 -> rh.gs(R.string.combo_pump_connected_now)
                    bolusMinAgo < 60   -> dateUtil.minAgo(rh, bolus.timestamp)

                    else               -> dateUtil.hourAgo(bolus.timestamp, rh)
                }
                binding.comboLastBolus.text = rh.gs(R.string.combo_last_bolus, bolus.amount, unit, ago)
            } else {
                binding.comboLastBolus.text = ""
            }

            // base basal rate
            binding.comboBaseBasalRate.text = rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, comboPlugin.baseBasalRate)

            // TBR
            var tbrStr = ""
            if (ps.tbrPercent != -1 && ps.tbrPercent != 100) {
                val minSinceRead = (System.currentTimeMillis() - comboPlugin.pump.state.timestamp) / 1000 / 60
                val remaining = ps.tbrRemainingDuration - minSinceRead
                if (remaining >= 0) {
                    tbrStr = rh.gs(R.string.combo_tbr_remaining, ps.tbrPercent, remaining)
                }
            }
            binding.comboTempBasal.text = tbrStr

            // stats
            binding.comboBolusCount.text = comboPlugin.bolusesDelivered.toString()
            binding.comboTbrCount.text = comboPlugin.tbrsSet.toString()
            binding.serialNumber.text = comboPlugin.serialNumber()
            updateErrorDisplay(false)
        } else {
            updateErrorDisplay(true)
        }
    }

    private fun updateErrorDisplay(forceHide: Boolean) {
        var errorCount = -1
        if (!forceHide) {
            val displayType = errorUtil.displayType
            if (displayType === DisplayType.ON_ERROR || displayType === DisplayType.ALWAYS) {
                val errorCountInternal = errorUtil.errorCount
                if (errorCountInternal > 0) {
                    errorCount = errorCountInternal
                } else if (displayType === DisplayType.ALWAYS) {
                    errorCount = 0
                }
            }
        }
        if (errorCount >= 0) {
            binding.comboConnectionErrorValue.visibility = View.VISIBLE
            binding.comboConnectionErrorLayout.visibility = View.VISIBLE
            binding.comboConnectionErrorValue.visibility = View.VISIBLE
            binding.comboConnectionErrorValue.text = if (errorCount == 0) "-" else "" + errorCount
        } else {
            binding.comboConnectionErrorValue.visibility = View.GONE
            binding.comboConnectionErrorLayout.visibility = View.GONE
            binding.comboConnectionErrorValue.visibility = View.GONE
        }
    }
}
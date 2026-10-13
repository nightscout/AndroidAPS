package info.nightscout.pump.combov2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import dagger.android.support.DaggerFragment
import info.nightscout.comboctl.base.NullDisplayFrame
import info.nightscout.comboctl.parser.BatteryState
import info.nightscout.comboctl.parser.ReservoirState
import info.nightscout.pump.combov2.databinding.Combov2FragmentBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.math.max
import kotlin.time.ExperimentalTime
import info.nightscout.comboctl.base.Tbr as ComboCtlTbr
import info.nightscout.comboctl.main.Pump as ComboCtlPump

class ComboV2Fragment : DaggerFragment() {

    @Inject lateinit var combov2Plugin: ComboV2Plugin
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var commandQueue: CommandQueue

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding: Combov2FragmentBinding = DataBindingUtil.inflate(
            inflater, R.layout.combov2_fragment, container, false
        )
        val view = binding.root

        binding.combov2RefreshButton.setOnClickListener {
            binding.combov2RefreshButton.isEnabled = false
            combov2Plugin.clearPumpErrorObservedFlag()
            commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.user_request), null)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // Start all of these flows with repeatOnLifecycle()
            // which will automatically cancel the flows when
            // the lifecycle reaches the STOPPED stage and
            // re-runs the lambda (as a suspended function)
            // when the lifecycle reaches the STARTED stage.
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combov2Plugin.pairedStateUIFlow
                    .onEach { isPaired ->
                        binding.combov2FragmentUnpairedUi.visibility = if (isPaired) View.GONE else View.VISIBLE
                        binding.combov2FragmentMainUi.visibility = if (isPaired) View.VISIBLE else View.GONE
                    }
                    .launchIn(this)

                combov2Plugin.driverStateUIFlow
                    .onEach { connectionState ->
                        val text = when (connectionState) {
                            ComboV2Plugin.DriverState.NotInitialized      -> rh.gs(R.string.combov2_not_initialized)
                            ComboV2Plugin.DriverState.Disconnected        -> rh.gs(app.aaps.core.ui.R.string.disconnected)
                            ComboV2Plugin.DriverState.Connecting          -> rh.gs(app.aaps.core.ui.R.string.connecting)
                            ComboV2Plugin.DriverState.CheckingPump        -> rh.gs(R.string.combov2_checking_pump)
                            ComboV2Plugin.DriverState.Ready               -> rh.gs(R.string.combov2_ready)
                            ComboV2Plugin.DriverState.Suspended           -> rh.gs(R.string.combov2_suspended)
                            ComboV2Plugin.DriverState.Error               -> rh.gs(app.aaps.core.ui.R.string.error)
                            is ComboV2Plugin.DriverState.ExecutingCommand ->
                                when (val desc = connectionState.description) {
                                    is ComboCtlPump.GettingBasalProfileCommandDesc  ->
                                        rh.gs(R.string.combov2_getting_basal_profile_cmddesc)

                                    is ComboCtlPump.SettingBasalProfileCommandDesc  ->
                                        rh.gs(R.string.combov2_setting_basal_profile_cmddesc)

                                    is ComboCtlPump.SettingTbrCommandDesc           ->
                                        if (desc.percentage != 100)
                                            rh.gs(R.string.combov2_setting_tbr_cmddesc, desc.percentage, desc.durationInMinutes)
                                        else
                                            rh.gs(R.string.combov2_cancelling_tbr)

                                    is ComboCtlPump.DeliveringBolusCommandDesc      ->
                                        rh.gs(R.string.combov2_delivering_bolus_cmddesc, desc.immediateBolusAmount.cctlBolusToIU())

                                    is ComboCtlPump.FetchingTDDHistoryCommandDesc   ->
                                        rh.gs(R.string.combov2_fetching_tdd_history_cmddesc)

                                    is ComboCtlPump.UpdatingPumpDateTimeCommandDesc ->
                                        rh.gs(R.string.combov2_updating_pump_datetime_cmddesc)

                                    is ComboCtlPump.UpdatingPumpStatusCommandDesc   ->
                                        rh.gs(R.string.combov2_updating_pump_status_cmddesc)

                                    else                                            -> rh.gs(R.string.combov2_executing_command)
                                }
                        }
                        binding.combov2DriverState.text = text

                        binding.combov2RefreshButton.isEnabled = when (connectionState) {
                            // Enable the refresh button if:
                            // 1. Pump is not connected (to be able to manually initiate a pump status update)
                            // 2. Pump is suspended (in case the user resumed the pump and wants to update the status in AAPS)
                            // 3. When an error happened (to manually clear the pumpErrorObserved flag and unlock the loop after dealing with the error)
                            ComboV2Plugin.DriverState.Disconnected,
                            ComboV2Plugin.DriverState.Suspended,
                            ComboV2Plugin.DriverState.Error -> true

                            else                            -> false
                        }

                        binding.combov2DriverState.setTextColor(
                            when (connectionState) {
                                ComboV2Plugin.DriverState.Error     -> rh.gac(context, app.aaps.core.ui.R.attr.warningColor)
                                ComboV2Plugin.DriverState.Suspended -> rh.gac(context, app.aaps.core.ui.R.attr.urgentColor)
                                else                                -> rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor)
                            }
                        )
                    }
                    .launchIn(this)

                combov2Plugin.lastConnectionTimestampUIFlow
                    .onEach { lastConnectionTimestamp ->
                        updateLastConnectionField(lastConnectionTimestamp, binding)
                    }
                    .launchIn(this)

                // This "Activity" is not to be confused with Android's "Activity" class.
                combov2Plugin.currentActivityUIFlow
                    .onEach { currentActivity ->
                        if (currentActivity.description.isEmpty()) {
                            binding.combov2CurrentActivityDesc.text = rh.gs(R.string.combov2_no_activity)
                            binding.combov2CurrentActivityProgress.progress = 0
                        } else {
                            binding.combov2CurrentActivityDesc.text = currentActivity.description
                            binding.combov2CurrentActivityProgress.progress = (currentActivity.overallProgress * 100.0).toInt()
                        }
                    }
                    .launchIn(this)

                combov2Plugin.batteryStateUIFlow
                    .onEach { batteryState ->
                        when (batteryState) {
                            null                      -> binding.combov2Battery.text = ""

                            BatteryState.NO_BATTERY   -> {
                                binding.combov2Battery.text = rh.gs(R.string.combov2_battery_empty_indicator)
                                binding.combov2Battery.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.warningColor))
                            }

                            BatteryState.LOW_BATTERY  -> {
                                binding.combov2Battery.text = rh.gs(R.string.combov2_battery_low_indicator)
                                binding.combov2Battery.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.urgentColor))
                            }

                            BatteryState.FULL_BATTERY -> {
                                binding.combov2Battery.text = rh.gs(R.string.combov2_battery_full_indicator)
                                binding.combov2Battery.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor))
                            }
                        }
                    }
                    .launchIn(this)

                combov2Plugin.reservoirLevelUIFlow
                    .onEach { reservoirLevel ->
                        binding.combov2Reservoir.text = if (reservoirLevel != null)
                            "${reservoirLevel.availableUnits} ${rh.gs(app.aaps.core.ui.R.string.insulin_unit_shortname)}"
                        else
                            ""

                        binding.combov2Reservoir.setTextColor(
                            when (reservoirLevel?.state) {
                                null                 -> rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor)
                                ReservoirState.EMPTY -> rh.gac(context, app.aaps.core.ui.R.attr.warningColor)
                                ReservoirState.LOW   -> rh.gac(context, app.aaps.core.ui.R.attr.urgentColor)
                                ReservoirState.FULL  -> rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor)
                            }
                        )
                    }
                    .launchIn(this)

                combov2Plugin.lastBolusUIFlow
                    .onEach { lastBolus ->
                        updateLastBolusField(lastBolus, binding)
                    }
                    .launchIn(this)

                combov2Plugin.currentTbrUIFlow
                    .onEach { tbr ->
                        updateCurrentTbrField(tbr, binding)
                    }
                    .launchIn(this)

                combov2Plugin.baseBasalRateUIFlow
                    .onEach { baseBasalRate ->
                        binding.combov2BaseBasalRate.text = if (baseBasalRate != null)
                            rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, baseBasalRate)
                        else
                            ""
                    }
                    .launchIn(this)

                combov2Plugin.serialNumberUIFlow
                    .onEach { serialNumber ->
                        binding.combov2PumpId.text = serialNumber
                    }
                    .launchIn(this)

                combov2Plugin.bluetoothAddressUIFlow
                    .onEach { bluetoothAddress ->
                        binding.combov2BluetoothAddress.text = bluetoothAddress.uppercase(Locale.ROOT)
                    }
                    .launchIn(this)

                combov2Plugin.displayFrameUIFlow
                    .onEach { displayFrame ->
                        binding.combov2RtDisplayFrame.displayFrame = displayFrame ?: NullDisplayFrame
                    }
                    .launchIn(this)

                launch {
                    while (true) {
                        delay(30 * 1000L) // Wait for 30 seconds
                        updateLastConnectionField(combov2Plugin.lastConnectionTimestampUIFlow.value, binding)
                        updateLastBolusField(combov2Plugin.lastBolusUIFlow.value, binding)
                        updateCurrentTbrField(combov2Plugin.currentTbrUIFlow.value, binding)
                    }
                }
            }
        }

        return view
    }

    private fun updateLastConnectionField(lastConnectionTimestamp: Long?, binding: Combov2FragmentBinding) {
        val currentTimestamp = System.currentTimeMillis()

        // If the last connection is >= 30 minutes ago,
        // we display a different message, one that
        // warns the user that a long time passed
        when (val secondsPassed = lastConnectionTimestamp?.let { (currentTimestamp - it) / 1000 }) {
            null             ->
                binding.combov2LastConnection.text = ""

            in 0..60         -> {
                binding.combov2LastConnection.text = rh.gs(R.string.combov2_less_than_one_minute_ago)
                binding.combov2LastConnection.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor))
            }

            in 60..(30 * 60) -> {
                binding.combov2LastConnection.text = rh.gs(app.aaps.core.interfaces.R.string.minago, secondsPassed / 60)
                binding.combov2LastConnection.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor))
            }

            else             -> {
                binding.combov2LastConnection.text = rh.gs(R.string.combov2_no_connection_for_n_mins, secondsPassed / 60)
                binding.combov2LastConnection.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.warningColor))
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun updateLastBolusField(lastBolus: ComboCtlPump.LastBolus?, binding: Combov2FragmentBinding) {
        val currentTimestamp = System.currentTimeMillis()

        if (lastBolus == null) {
            binding.combov2LastBolus.text = ""
            return
        }

        // If the last bolus is >= 30 minutes ago,
        // we display a different message, one that
        // warns the user that a long time passed
        val bolusAgoText = when (val secondsPassed = (currentTimestamp - lastBolus.timestamp.toEpochMilliseconds()) / 1000) {
            in 0..59 ->
                rh.gs(R.string.combov2_less_than_one_minute_ago)

            else     ->
                rh.gs(app.aaps.core.interfaces.R.string.minago, secondsPassed / 60)
        }

        binding.combov2LastBolus.text =
            rh.gs(
                R.string.combov2_last_bolus,
                lastBolus.bolusAmount.cctlBolusToIU(),
                rh.gs(app.aaps.core.ui.R.string.insulin_unit_shortname),
                bolusAgoText
            )
    }

    @OptIn(ExperimentalTime::class)
    private fun updateCurrentTbrField(currentTbr: ComboCtlTbr?, binding: Combov2FragmentBinding) {
        val currentTimestamp = System.currentTimeMillis()

        if (currentTbr == null) {
            binding.combov2CurrentTbr.text = ""
            return
        }

        val remainingSeconds = max(
            currentTbr.durationInMinutes * 60 - (currentTimestamp - currentTbr.timestamp.toEpochMilliseconds()) / 1000,
            0
        )

        binding.combov2CurrentTbr.text =
            if (remainingSeconds >= 60)
                rh.gs(
                    R.string.combov2_current_tbr,
                    currentTbr.percentage,
                    remainingSeconds / 60
                )
            else
                rh.gs(
                    R.string.combov2_current_tbr_less_than_1min,
                    currentTbr.percentage
                )
    }
}

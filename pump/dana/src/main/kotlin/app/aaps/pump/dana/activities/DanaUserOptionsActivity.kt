package app.aaps.pump.dana.activities

import android.content.Context
import android.os.Bundle
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.R
import app.aaps.pump.dana.databinding.DanarUserOptionsActivityBinding
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.text.DecimalFormat
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

class DanaUserOptionsActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var context: Context
    @Inject lateinit var danaPump: DanaPump
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uiInteraction: UiInteraction

    private val disposable = CompositeDisposable()

    // This is for Dana pumps only
    private fun isRS() = activePlugin.activePump.pumpDescription.pumpType == PumpType.DANA_RS || activePlugin.activePump.pumpDescription.pumpType == PumpType.DANA_I
    private fun isDanaR() = activePlugin.activePump.pumpDescription.pumpType == PumpType.DANA_R
    private fun isDanaRv2() = activePlugin.activePump.pumpDescription.pumpType == PumpType.DANA_RV2

    private var minBacklight = 1

    private lateinit var binding: DanarUserOptionsActivityBinding

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventInitializationChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ setData() }, fabricPrivacy::logException)
    }

    @Synchronized
    override fun onPause() {
        disposable.clear()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.saveUserOptions.setOnClickListener(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DanarUserOptionsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = rh.gs(R.string.danar_pump_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        binding.saveUserOptions.setOnClickListener { onSaveClick() }

        minBacklight = if (danaPump.hwModel < 7) 1 else 0 // Dana-i allows zero

        aapsLogger.debug(
            LTag.PUMP,
            "UserOptionsLoaded:" + (System.currentTimeMillis() - danaPump.lastConnection) / 1000 + " s ago"
                + "\ntimeDisplayType24:" + danaPump.timeDisplayType24
                + "\nbuttonScroll:" + danaPump.buttonScrollOnOff
                + "\nbeepAndAlarm:" + danaPump.beepAndAlarm
                + "\nlcdOnTimeSec:" + danaPump.lcdOnTimeSec
                + "\nbackLight:" + danaPump.backlightOnTimeSec
                + "\npumpUnits:" + danaPump.units
                + "\nlowReservoir:" + danaPump.lowReservoirRate
        )

        binding.screenTimeout.setParams(danaPump.lcdOnTimeSec.toDouble(), 5.0, 240.0, 5.0, DecimalFormat("1"), false, binding.saveUserOptions)
        binding.backlight.setParams(danaPump.backlightOnTimeSec.toDouble(), minBacklight.toDouble(), 60.0, 1.0, DecimalFormat("1"), false, binding.saveUserOptions)
        binding.shutdown.setParams(danaPump.shutdownHour.toDouble(), 0.0, 24.0, 1.0, DecimalFormat("1"), true, binding.saveUserOptions)
        binding.lowReservoir.setParams(danaPump.lowReservoirRate.toDouble(), 10.0, 50.0, 10.0, DecimalFormat("10"), false, binding.saveUserOptions)
        when (danaPump.beepAndAlarm) {
            0b01  -> binding.pumpAlarmSound.isChecked = true
            0b10  -> binding.pumpAlarmVibrate.isChecked = true
            0b11  -> binding.pumpAlarmBoth.isChecked = true

            0b101 -> {
                binding.pumpAlarmSound.isChecked = true
                binding.beep.isChecked = true
            }

            0b110 -> {
                binding.pumpAlarmVibrate.isChecked = true
                binding.beep.isChecked = true
            }

            0b111 -> {
                binding.pumpAlarmBoth.isChecked = true
                binding.beep.isChecked = true
            }
        }
        if (danaPump.lastSettingsRead == 0L && danaPump.hwModel < 0x05) // RS+ doesn't use lastSettingsRead
            aapsLogger.error(LTag.PUMP, "No settings loaded from pump!")
        else
            setData()
    }

    private fun setData() {
        // in DanaRS timeDisplay values are reversed
        binding.timeFormat.isChecked = danaPump.timeDisplayType24
        binding.buttonScroll.isChecked = danaPump.buttonScrollOnOff
        binding.beep.isChecked = danaPump.beepAndAlarm > 4
        binding.screenTimeout.value = danaPump.lcdOnTimeSec.toDouble()
        binding.backlight.value = danaPump.backlightOnTimeSec.toDouble()
        binding.units.isChecked = danaPump.getUnits() == GlucoseUnit.MMOL.asText
        binding.shutdown.value = danaPump.shutdownHour.toDouble()
        binding.lowReservoir.value = danaPump.lowReservoirRate.toDouble()
    }

    private fun onSaveClick() {
        //exit if pump is not DanaRS, DanaR, or DanaR with upgraded firmware
        if (!isRS() && !isDanaR() && !isDanaRv2()) return

        danaPump.timeDisplayType24 = binding.timeFormat.isChecked

        danaPump.buttonScrollOnOff = binding.buttonScroll.isChecked
        danaPump.beepAndAlarm = when {
            binding.pumpAlarmSound.isChecked   -> 1
            binding.pumpAlarmVibrate.isChecked -> 2
            binding.pumpAlarmBoth.isChecked    -> 3
            else                               -> 1
        }
        if (binding.beep.isChecked) danaPump.beepAndAlarm += 4

        // step is 5 seconds, 5 to 240
        danaPump.lcdOnTimeSec = min(max(binding.screenTimeout.value.toInt() / 5 * 5, 5), 240)
        // 1 to 60
        danaPump.backlightOnTimeSec = min(max(binding.backlight.value.toInt(), minBacklight), 60)

        danaPump.units = if (binding.units.isChecked) 1 else 0

        danaPump.shutdownHour = min(binding.shutdown.value.toInt(), 24)

        // 10 to 50
        danaPump.lowReservoirRate = min(max(binding.lowReservoir.value.toInt() * 10 / 10, 10), 50)

        commandQueue.setUserOptions(object : Callback() {
            override fun run() {
                if (!result.success) {
                    uiInteraction.runAlarm(result.comment, rh.gs(R.string.pumperror), app.aaps.core.ui.R.raw.boluserror)
                }
            }
        })
        finish()
    }
}
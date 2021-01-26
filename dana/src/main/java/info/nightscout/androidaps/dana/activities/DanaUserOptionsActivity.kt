package info.nightscout.androidaps.dana.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.dana.R
import info.nightscout.androidaps.dana.databinding.DanarUserOptionsActivityBinding
import info.nightscout.androidaps.events.EventInitializationChanged
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.extensions.plusAssign
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import java.text.DecimalFormat
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

class DanaUserOptionsActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var context: Context
    @Inject lateinit var danaPump: DanaPump
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var commandQueue: CommandQueueProvider

    private val disposable = CompositeDisposable()

    // This is for Dana pumps only
    private fun isRS() = activePlugin.activePump.pumpDescription.pumpType == PumpType.DanaRS
    private fun isDanaR() = activePlugin.activePump.pumpDescription.pumpType == PumpType.DanaR
    private fun isDanaRv2() = activePlugin.activePump.pumpDescription.pumpType == PumpType.DanaRv2

    var minBacklight = 1

    private lateinit var binding: DanarUserOptionsActivityBinding

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventInitializationChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ setData() }) { fabricPrivacy.logException(it) }
    }

    @Synchronized
    override fun onPause() {
        disposable.clear()
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DanarUserOptionsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.saveUserOptions.setOnClickListener { onSaveClick() }

        minBacklight = if (danaPump.hwModel < 7) 1 else 0 // Dana-i allows zero

        aapsLogger.debug(LTag.PUMP,
            "UserOptionsLoaded:" + (System.currentTimeMillis() - danaPump.lastConnection) / 1000 + " s ago"
                + "\ntimeDisplayType24:" + danaPump.timeDisplayType24
                + "\nbuttonScroll:" + danaPump.buttonScrollOnOff
                + "\nbeepAndAlarm:" + danaPump.beepAndAlarm
                + "\nlcdOnTimeSec:" + danaPump.lcdOnTimeSec
                + "\nbackLight:" + danaPump.backlightOnTimeSec
                + "\npumpUnits:" + danaPump.units
                + "\nlowReservoir:" + danaPump.lowReservoirRate)

        binding.screentimeout.setParams(danaPump.lcdOnTimeSec.toDouble(), 5.0, 240.0, 5.0, DecimalFormat("1"), false, binding.saveUserOptions)
        binding.backlight.setParams(danaPump.backlightOnTimeSec.toDouble(), minBacklight.toDouble(), 60.0, 1.0, DecimalFormat("1"), false, binding.saveUserOptions)
        binding.shutdown.setParams(danaPump.shutdownHour.toDouble(), 0.0, 24.0, 1.0, DecimalFormat("1"), true, binding.saveUserOptions)
        binding.lowreservoir.setParams(danaPump.lowReservoirRate.toDouble(), 10.0, 50.0, 10.0, DecimalFormat("10"), false, binding.saveUserOptions)
        when (danaPump.beepAndAlarm) {
            0b01 -> binding.pumpalarmSound.isChecked = true
            0b10 -> binding.pumpalarmVibrate.isChecked = true
            0b11 -> binding.pumpalarmBoth.isChecked = true

            0b101 -> {
                binding.pumpalarmSound.isChecked = true
                binding.beep.isChecked = true
            }

            0b110 -> {
                binding.pumpalarmVibrate.isChecked = true
                binding.beep.isChecked = true
            }

            0b111 -> {
                binding.pumpalarmBoth.isChecked = true
                binding.beep.isChecked = true
            }
        }
        if (danaPump.lastSettingsRead == 0L && danaPump.hwModel < 0x05) // RS+ doesn't use lastSettingsRead
            aapsLogger.error(LTag.PUMP, "No settings loaded from pump!")
        else
            setData()
    }

    fun setData() {
        // in DanaRS timeDisplay values are reversed
        binding.timeformat.isChecked = danaPump.timeDisplayType24
        binding.buttonscroll.isChecked = danaPump.buttonScrollOnOff
        binding.beep.isChecked = danaPump.beepAndAlarm > 4
        binding.screentimeout.value = danaPump.lcdOnTimeSec.toDouble()
        binding.backlight.value = danaPump.backlightOnTimeSec.toDouble()
        binding.units.isChecked = danaPump.getUnits() == Constants.MMOL
        binding.shutdown.value = danaPump.shutdownHour.toDouble()
        binding.lowreservoir.value = danaPump.lowReservoirRate.toDouble()
    }

    private fun onSaveClick() {
        //exit if pump is not DanaRS, DanaR, or DanaR with upgraded firmware
        if (!isRS() && !isDanaR() && !isDanaRv2()) return

        danaPump.timeDisplayType24 = binding.timeformat.isChecked

        danaPump.buttonScrollOnOff = binding.buttonscroll.isChecked
        danaPump.beepAndAlarm = when {
            binding.pumpalarmSound.isChecked   -> 1
            binding.pumpalarmVibrate.isChecked -> 2
            binding.pumpalarmBoth.isChecked    -> 3
            else                               -> 1
        }
        if (binding.beep.isChecked) danaPump.beepAndAlarm += 4

        // step is 5 seconds, 5 to 240
        danaPump.lcdOnTimeSec = min(max(binding.screentimeout.value.toInt() / 5 * 5, 5), 240)
        // 1 to 60
        danaPump.backlightOnTimeSec = min(max(binding.backlight.value.toInt(), minBacklight), 60)

        danaPump.units = if (binding.units.isChecked) 1 else 0

        danaPump.shutdownHour = min(binding.shutdown.value.toInt(), 24)

        // 10 to 50
        danaPump.lowReservoirRate = min(max(binding.lowreservoir.value.toInt() * 10 / 10, 10), 50)

        commandQueue.setUserOptions(object : Callback() {
            override fun run() {
                if (!result.success) {
                    val i = Intent(context, ErrorHelperActivity::class.java)
                    i.putExtra("soundid", R.raw.boluserror)
                    i.putExtra("status", result.comment)
                    i.putExtra("title", resourceHelper.gs(R.string.pumperror))
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(i)
                }
            }
        })
        finish()
    }
}
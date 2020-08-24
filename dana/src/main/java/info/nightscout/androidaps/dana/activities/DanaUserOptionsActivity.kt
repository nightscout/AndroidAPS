package info.nightscout.androidaps.dana.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.dana.R
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
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.danar_user_options_activity.*
import java.text.DecimalFormat
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

class DanaUserOptionsActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var resourceHelper: ResourceHelper
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
        setContentView(R.layout.danar_user_options_activity)

        save_user_options.setOnClickListener { onSaveClick() }

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

        danar_screentimeout.setParams(danaPump.lcdOnTimeSec.toDouble(), 5.0, 240.0, 5.0, DecimalFormat("1"), false, save_user_options)
        danar_backlight.setParams(danaPump.backlightOnTimeSec.toDouble(), minBacklight.toDouble(), 60.0, 1.0, DecimalFormat("1"), false, save_user_options)
        danar_shutdown.setParams(danaPump.shutdownHour.toDouble(), 0.0, 24.0, 1.0, DecimalFormat("1"), true, save_user_options)
        danar_lowreservoir.setParams(danaPump.lowReservoirRate.toDouble(), 10.0, 50.0, 10.0, DecimalFormat("10"), false, save_user_options)
        when (danaPump.beepAndAlarm) {
            0b01 -> danar_pumpalarm_sound.isChecked = true
            0b10 -> danar_pumpalarm_vibrate.isChecked = true
            0b11 -> danar_pumpalarm_both.isChecked = true

            0b101 -> {
                danar_pumpalarm_sound.isChecked = true
                danar_beep.isChecked = true
            }

            0b110 -> {
                danar_pumpalarm_vibrate.isChecked = true
                danar_beep.isChecked = true
            }

            0b111 -> {
                danar_pumpalarm_both.isChecked = true
                danar_beep.isChecked = true
            }
        }
        if (danaPump.lastSettingsRead == 0L && danaPump.hwModel < 0x05) // RS+ doesn't use lastSettingsRead
            aapsLogger.error(LTag.PUMP, "No settings loaded from pump!")
        else
            setData()
    }

    fun setData() {
        // in DanaRS timeDisplay values are reversed
        danar_timeformat.isChecked = danaPump.timeDisplayType24
        danar_buttonscroll.isChecked = danaPump.buttonScrollOnOff
        danar_beep.isChecked = danaPump.beepAndAlarm > 4
        danar_screentimeout.value = danaPump.lcdOnTimeSec.toDouble()
        danar_backlight.value = danaPump.backlightOnTimeSec.toDouble()
        danar_units.isChecked = danaPump.getUnits() == Constants.MMOL
        danar_shutdown.value = danaPump.shutdownHour.toDouble()
        danar_lowreservoir.value = danaPump.lowReservoirRate.toDouble()
    }

    private fun onSaveClick() {
        //exit if pump is not DanaRS, DanaR, or DanaR with upgraded firmware
        if (!isRS() && !isDanaR() && !isDanaRv2()) return

        danaPump.timeDisplayType24 = danar_timeformat.isChecked

        danaPump.buttonScrollOnOff = danar_buttonscroll.isChecked
        danaPump.beepAndAlarm = when {
            danar_pumpalarm_sound.isChecked   -> 1
            danar_pumpalarm_vibrate.isChecked -> 2
            danar_pumpalarm_both.isChecked    -> 3
            else                              -> 1
        }
        if (danar_beep.isChecked) danaPump.beepAndAlarm += 4

        // step is 5 seconds, 5 to 240
        danaPump.lcdOnTimeSec = min(max(danar_screentimeout.value.toInt() / 5 * 5, 5), 240)
        // 1 to 60
        danaPump.backlightOnTimeSec = min(max(danar_backlight.value.toInt(), minBacklight), 60)

        danaPump.units = if (danar_units.isChecked) 1 else 0

        danaPump.shutdownHour = min(danar_shutdown.value.toInt(), 24)

        // 10 to 50
        danaPump.lowReservoirRate = min(max(danar_lowreservoir.value.toInt() * 10 / 10, 10), 50)

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
package info.nightscout.pump.diaconn.activities

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import info.nightscout.core.ui.activities.TranslatedDaggerAppCompatActivity
import info.nightscout.core.ui.toast.ToastUtils
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.pump.diaconn.DiaconnG8Pump
import info.nightscout.pump.diaconn.R
import info.nightscout.pump.diaconn.databinding.DiaconnG8UserOptionsActivityBinding
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.text.DecimalFormat
import javax.inject.Inject

class DiaconnG8UserOptionsActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var context: Context
    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var sp: SP
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper

    private val disposable = CompositeDisposable()

    private lateinit var binding: DiaconnG8UserOptionsActivityBinding

    @Synchronized
    override fun onResume() {
        super.onResume()
    }

    @Synchronized
    override fun onPause() {
        disposable.clear()
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DiaconnG8UserOptionsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.saveAlarm.setOnClickListener { onSaveAlarmClick() }
        binding.saveLcdOnTime.setOnClickListener { onSaveLcdOnTimeClick() }
        binding.saveLang.setOnClickListener { onSaveLangClick() }

        binding.saveBolusSpeed.setOnClickListener { onSaveBolusSpeedClick() }

        val spBolusSpeed = sp.getString(getString(R.string.key_diaconn_g8_bolusspeed), "5")

        binding.bolusSpeed.setParams(spBolusSpeed.toDouble(), 1.0, 8.0, 1.0, DecimalFormat("1"), true, binding.saveBolusSpeed)

        aapsLogger.debug(
            LTag.PUMP,
            "UserOptionsLoaded:" + (System.currentTimeMillis() - diaconnG8Pump.lastConnection) / 1000 + " s ago"
                + "\nbeepAndAlarm:" + diaconnG8Pump.beepAndAlarm
                + "\nalarmIntesity:" + diaconnG8Pump.alarmIntesity
                + "\nlanguage:" + diaconnG8Pump.selectedLanguage
                + "\nlcdOnTimeSec:" + diaconnG8Pump.lcdOnTimeSec
        )

        fillSoundCategory()
        fillSoundSubCategory()

        binding.beepAndAlarm.setSelection(diaconnG8Pump.beepAndAlarm - 1)
        binding.alarmIntesity.setSelection(diaconnG8Pump.alarmIntesity - 1)

        binding.beepAndAlarm.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                binding.alarmIntesity.visibility = if ("silent" == binding.beepAndAlarm.getItemAtPosition(position).toString()) View.GONE else View.VISIBLE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        when (diaconnG8Pump.lcdOnTimeSec) {
            1 -> binding.pumpscreentimeout10.isChecked = true
            2 -> binding.pumpscreentimeout20.isChecked = true
            3 -> binding.pumpscreentimeout30.isChecked = true
        }

        when (diaconnG8Pump.selectedLanguage) {
            1 -> binding.pumplangChiness.isChecked = true
            2 -> binding.pumplangKorean.isChecked = true
            3 -> binding.pumplangEnglish.isChecked = true
        }
    }

    private fun onSaveAlarmClick() {

        diaconnG8Pump.setUserOptionType = DiaconnG8Pump.ALARM

        diaconnG8Pump.beepAndAlarm = binding.beepAndAlarm.selectedItemPosition + 1
        diaconnG8Pump.alarmIntesity = binding.alarmIntesity.selectedItemPosition + 1

        onSaveClick()
    }

    private fun onSaveLcdOnTimeClick() {

        diaconnG8Pump.setUserOptionType = DiaconnG8Pump.LCD

        diaconnG8Pump.lcdOnTimeSec = when {
            binding.pumpscreentimeout10.isChecked -> 1
            binding.pumpscreentimeout20.isChecked -> 2
            binding.pumpscreentimeout30.isChecked -> 3
            else                                  -> 1
        }

        onSaveClick()
    }

    private fun onSaveLangClick() {

        diaconnG8Pump.setUserOptionType = DiaconnG8Pump.LANG

        diaconnG8Pump.selectedLanguage = when {
            binding.pumplangChiness.isChecked -> 1
            binding.pumplangKorean.isChecked  -> 2
            binding.pumplangEnglish.isChecked -> 3
            else                              -> 2
        }

        onSaveClick()
    }

    private fun onSaveBolusSpeedClick() {
        val intSpeed = binding.bolusSpeed.value.toInt()

        diaconnG8Pump.bolusSpeed = intSpeed
        diaconnG8Pump.speed = intSpeed
        diaconnG8Pump.setUserOptionType = DiaconnG8Pump.BOLUS_SPEED
        sp.putString(R.string.key_diaconn_g8_bolusspeed, intSpeed.toString())
        sp.putBoolean(R.string.key_diaconn_g8_is_bolus_speed_sync, false)

        ToastUtils.okToast(context, "Save Success!")
    }

    private fun onSaveClick() {
        commandQueue.setUserOptions(object : Callback() {
            override fun run() {
                if (!result.success) {
                    uiInteraction.runAlarm(result.comment, rh.gs(R.string.pumperror), info.nightscout.core.ui.R.raw.boluserror)
                }
            }
        })
        finish()
    }

    private fun fillSoundCategory() {
        val categories = ArrayList<String>()
        categories.add(rh.gs(R.string.diaconn_g8_pumpalarm_sound))
        categories.add(rh.gs(R.string.diaconn_g8_pumpalarm_vibrate))
        categories.add(rh.gs(R.string.diaconn_g8_pumpalarm_silent))
        context.let { context ->
            val adapterCategories = ArrayAdapter(context, info.nightscout.core.ui.R.layout.spinner_centered, categories)
            binding.beepAndAlarm.adapter = adapterCategories
        }
    }

    private fun fillSoundSubCategory() {
        val categories = ArrayList<String>()
        categories.add(rh.gs(R.string.diaconn_g8_pumpalarm_intensity_low))
        categories.add(rh.gs(R.string.diaconn_g8_pumpalarm_intensity_middle))
        categories.add(rh.gs(R.string.diaconn_g8_pumpalarm_intensity_high))
        context.let { context ->
            val adapterCategories = ArrayAdapter(context, info.nightscout.core.ui.R.layout.spinner_centered, categories)
            binding.alarmIntesity.adapter = adapterCategories
        }
    }

}
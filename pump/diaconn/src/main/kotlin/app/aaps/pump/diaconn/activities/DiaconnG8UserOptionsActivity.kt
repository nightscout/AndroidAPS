package app.aaps.pump.diaconn.activities

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.pump.diaconn.R
import app.aaps.pump.diaconn.databinding.DiaconnG8UserOptionsActivityBinding
import app.aaps.pump.diaconn.keys.DiaconnIntKey
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.text.DecimalFormat
import javax.inject.Inject

class DiaconnG8UserOptionsActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var context: Context
    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var preferences: Preferences
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

    override fun onDestroy() {
        super.onDestroy()
        binding.saveAlarm.setOnClickListener(null)
        binding.saveLcdOnTime.setOnClickListener(null)
        binding.saveLang.setOnClickListener(null)
        binding.saveBolusSpeed.setOnClickListener(null)
        binding.beepAndAlarm.adapter = null
        binding.alarmIntesity.adapter = null
        binding.beepAndAlarm.onItemSelectedListener = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DiaconnG8UserOptionsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = rh.gs(R.string.diaconng8_pump_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        binding.saveAlarm.setOnClickListener { onSaveAlarmClick() }
        binding.saveLcdOnTime.setOnClickListener { onSaveLcdOnTimeClick() }
        binding.saveLang.setOnClickListener { onSaveLangClick() }

        binding.saveBolusSpeed.setOnClickListener { onSaveBolusSpeedClick() }

        val spBolusSpeed = preferences.get(DiaconnIntKey.BolusSpeed)

        binding.bolusSpeed.setParams(spBolusSpeed.toDouble(), 1.0, 8.0, 1.0, DecimalFormat("1"), true, binding.saveBolusSpeed)

        aapsLogger.debug(
            LTag.PUMP,
            "UserOptionsLoaded:" + (System.currentTimeMillis() - diaconnG8Pump.lastConnection) / 1000 + " s ago"
                + "\nbeepAndAlarm:" + diaconnG8Pump.beepAndAlarm
                + "\nalarmIntensity:" + diaconnG8Pump.alarmIntensity
                + "\nlanguage:" + diaconnG8Pump.selectedLanguage
                + "\nlcdOnTimeSec:" + diaconnG8Pump.lcdOnTimeSec
        )

        fillSoundCategory()
        fillSoundSubCategory()

        binding.beepAndAlarm.setSelection(diaconnG8Pump.beepAndAlarm - 1)
        binding.alarmIntesity.setSelection(diaconnG8Pump.alarmIntensity - 1)

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
        diaconnG8Pump.alarmIntensity = binding.alarmIntesity.selectedItemPosition + 1

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
        preferences.put(DiaconnIntKey.BolusSpeed, intSpeed)
        ToastUtils.okToast(context, "Save Success!")
    }

    private fun onSaveClick() {
        commandQueue.setUserOptions(object : Callback() {
            override fun run() {
                if (!result.success) {
                    uiInteraction.runAlarm(result.comment, rh.gs(R.string.pumperror), app.aaps.core.ui.R.raw.boluserror)
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
            val adapterCategories = ArrayAdapter(context, app.aaps.core.ui.R.layout.spinner_centered, categories)
            binding.beepAndAlarm.adapter = adapterCategories
        }
    }

    private fun fillSoundSubCategory() {
        val categories = ArrayList<String>()
        categories.add(rh.gs(R.string.diaconn_g8_pumpalarm_intensity_low))
        categories.add(rh.gs(R.string.diaconn_g8_pumpalarm_intensity_middle))
        categories.add(rh.gs(R.string.diaconn_g8_pumpalarm_intensity_high))
        context.let { context ->
            val adapterCategories = ArrayAdapter(context, app.aaps.core.ui.R.layout.spinner_centered, categories)
            binding.alarmIntesity.adapter = adapterCategories
        }
    }

}
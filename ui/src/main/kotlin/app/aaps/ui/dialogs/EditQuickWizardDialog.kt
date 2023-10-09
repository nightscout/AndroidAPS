package app.aaps.ui.dialogs

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.SeekBar
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.interfaces.utils.T
import app.aaps.core.main.wizard.QuickWizard
import app.aaps.core.main.wizard.QuickWizardEntry
import app.aaps.core.ui.extensions.selectedItemPosition
import app.aaps.core.ui.extensions.setSelection
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.ui.R
import app.aaps.ui.databinding.DialogEditQuickwizardBinding
import app.aaps.ui.events.EventQuickWizardChange
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dagger.android.support.DaggerDialogFragment
import org.json.JSONException
import java.text.DecimalFormat
import javax.inject.Inject

class EditQuickWizardDialog : DaggerDialogFragment(), View.OnClickListener {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var quickWizard: QuickWizard
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var sp: SP
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var ctx: Context

    var position = -1
    private var fromSeconds: Int = 0
    private var toSeconds: Int = 0

    private var _binding: DialogEditQuickwizardBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(false)
        _binding = DialogEditQuickwizardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (arguments ?: savedInstanceState)?.let { bundle ->
            position = bundle.getInt("position", -1)
        }
        val entry = if (position == -1) quickWizard.newEmptyItem() else quickWizard[position]
        if (sp.getBoolean(app.aaps.core.utils.R.string.key_wear_control, false)) {
            binding.deviceLabel.visibility = View.VISIBLE
            binding.device.visibility = View.VISIBLE
        } else {
            binding.deviceLabel.visibility = View.GONE
            binding.device.visibility = View.GONE
        }

        binding.okcancel.ok.setOnClickListener {
            try {
                entry.storage.put("buttonText", binding.buttonEdit.text.toString())
                entry.storage.put("carbs", SafeParse.stringToInt(binding.carbsEdit.text))
                entry.storage.put("validFrom", fromSeconds)
                entry.storage.put("validTo", toSeconds)
                entry.storage.put("useBG", binding.useBg.isChecked)
                entry.storage.put("useCOB", booleanToInt(binding.useCob.isChecked))
                entry.storage.put("useBolusIOB", booleanToInt(binding.useBolusIob.isChecked))
                entry.storage.put("device", binding.device.selectedItemPosition)
                entry.storage.put("useBasalIOB", binding.useBasalIob.selectedItemPosition)
                entry.storage.put("useTrend", binding.useTrend.selectedItemPosition)
                entry.storage.put("useSuperBolus", booleanToInt(binding.useSuperBolus.isChecked))
                entry.storage.put("useTempTarget", booleanToInt(binding.useTempTarget.isChecked))
                entry.storage.put("usePercentage", booleanToInt(binding.usePercentage.isChecked))
                //modified so you can only chose every 5% on Seekbar so 30 option from 0 to 150%.
                val customPercentage = binding.customPercentageSeekbar.progress * 5
                entry.storage.put("percentage", customPercentage)

                entry.storage.put("useEcarbs", booleanToInt(binding.useEcarbs.isChecked))
                entry.storage.put("time", binding.time.value.toInt())
                entry.storage.put("duration", SafeParse.stringToInt(binding.duration.text))
                entry.storage.put("carbs2", SafeParse.stringToInt(binding.carbs2.text))
            } catch (e: JSONException) {
                aapsLogger.error("Unhandled exception", e)
            }

            quickWizard.addOrUpdate(entry)
            rxBus.send(EventQuickWizardChange())
            dismiss()
        }
        binding.okcancel.cancel.setOnClickListener { dismiss() }

        binding.from.setOnClickListener {
            val clockFormat = if (DateFormat.is24HourFormat(context)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(clockFormat)
                .setHour(T.secs(fromSeconds.toLong()).hours().toInt())
                .setMinute(T.secs((fromSeconds % 3600).toLong()).mins().toInt())
                .build()
            timePicker.addOnPositiveButtonClickListener {
                fromSeconds = (T.hours(timePicker.hour.toLong()).secs() + T.mins(timePicker.minute.toLong()).secs()).toInt()
                binding.from.text = dateUtil.timeString(dateUtil.secondsOfTheDayToMilliseconds(fromSeconds))
            }
            timePicker.show(parentFragmentManager, "event_time_time_picker")
        }

        fromSeconds = entry.validFrom()
        binding.from.text = dateUtil.timeString(dateUtil.secondsOfTheDayToMilliseconds(fromSeconds))

        binding.to.setOnClickListener {
            val clockFormat = if (DateFormat.is24HourFormat(context)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(clockFormat)
                .setHour(T.secs(toSeconds.toLong()).hours().toInt())
                .setMinute(T.secs((toSeconds % 3600).toLong()).mins().toInt())
                .build()
            timePicker.addOnPositiveButtonClickListener {
                toSeconds = (T.hours(timePicker.hour.toLong()).secs() + T.mins(timePicker.minute.toLong()).secs()).toInt()
                binding.to.text = dateUtil.timeString(dateUtil.secondsOfTheDayToMilliseconds(toSeconds))
            }
            timePicker.show(parentFragmentManager, "event_time_time_picker")
        }

        fun usePercentage(custom: Boolean) {
            if (custom) {
                binding.defaultPercentageTextview.visibility = View.GONE
                binding.customPercentageSeekbar.visibility = View.VISIBLE
                val customPercentage = binding.customPercentageSeekbar.progress * 5
                binding.usePercentage.text = customPercentage.toString() + "%"
            } else {
                binding.customPercentageSeekbar.visibility = View.GONE
                binding.defaultPercentageTextview.visibility = View.VISIBLE
                binding.usePercentage.text = context?.getString(R.string.overview_edit_quickwizard_custom) + " %"
            }
        }

        fun useECarbs(custom: Boolean) {
            if (custom) {
                binding.timeLabel.visibility = View.VISIBLE
                binding.time.visibility = View.VISIBLE
                binding.durationLabel.visibility = View.VISIBLE
                binding.duration.visibility = View.VISIBLE
                binding.carbs2Label.visibility = View.VISIBLE
                binding.carbs2.visibility = View.VISIBLE
                binding.minLabel.visibility = View.VISIBLE
                binding.hLabel.visibility = View.VISIBLE
                binding.gLabel.visibility = View.VISIBLE
            } else {
                binding.timeLabel.visibility = View.GONE
                binding.time.visibility = View.GONE
                binding.durationLabel.visibility = View.GONE
                binding.duration.visibility = View.GONE
                binding.carbs2Label.visibility = View.GONE
                binding.carbs2.visibility = View.GONE
                binding.minLabel.visibility = View.GONE
                binding.hLabel.visibility = View.GONE
                binding.gLabel.visibility = View.GONE
            }
        }

        binding.usePercentage.setOnCheckedChangeListener { _, checkedId ->
            usePercentage(checkedId)
        }

        binding.useEcarbs.setOnCheckedChangeListener { _, checkedId ->
            useECarbs(checkedId)
        }

        binding.customPercentageSeekbar.setOnSeekBarChangeListener (object :
                                                                        SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar,
                                           progress: Int, fromUser: Boolean) {
                usePercentage(true)
            }

            override fun onStartTrackingTouch(seek: SeekBar) {
                // write custom code for progress is started
            }

            override fun onStopTrackingTouch(seek: SeekBar) {
                // write custom code for progress is stopped
            }
        })

        //ecarb - checker - based on CarbDialog code
        val maxCarbs = constraintChecker.getMaxCarbsAllowed().value().toDouble()
        binding.time.setParams(
            savedInstanceState?.getDouble("time")
                ?: 0.0, -7 * 24 * 60.0, 12 * 60.0, 5.0, DecimalFormat("0"), false, binding.okcancel.ok, textWatcher
        )

        binding.duration.setParams(
            savedInstanceState?.getDouble("duration")
                ?: 0.0, 0.0, 10.0, 1.0, DecimalFormat("0"), false, binding.okcancel.ok, textWatcher
        )

        binding.carbs2.setParams(
            savedInstanceState?.getDouble("carbs2")
                ?: 0.0, 0.0, maxCarbs, 1.0, DecimalFormat("0"), false, binding.okcancel.ok, textWatcher
        )

        binding.carbsEdit.setParams(
            savedInstanceState?.getDouble("carbs2")
                ?: 0.0, 0.0, maxCarbs, 1.0, DecimalFormat("0"), false, binding.okcancel.ok, textWatcher
        )
        //EOF ecarb - checker - based on CarbDialog code

        toSeconds = entry.validTo()
        binding.to.text = dateUtil.timeString(dateUtil.secondsOfTheDayToMilliseconds(toSeconds))

        binding.buttonEdit.setText(entry.buttonText())
        binding.carbsEdit.value = SafeParse.stringToDouble(entry.carbs().toString())

        binding.useBg.isChecked = intToBoolean(entry.useBG())
        binding.useCob.isChecked = intToBoolean(entry.useCOB())
        binding.useBolusIob.isChecked = intToBoolean(entry.useBolusIOB())
        binding.useBasalIob.setSelection(entry.useBasalIOB())
        binding.device.setSelection(entry.device())
        binding.useTrend.setSelection(entry.useTrend())
        binding.useSuperBolus.isChecked=intToBoolean(entry.useSuperBolus())
        binding.useTempTarget.isChecked=intToBoolean(entry.useTempTarget())
        binding.usePercentage.isChecked=intToBoolean(entry.usePercentage())
        val defaultPercentage = entry.percentage() / 5
        binding.customPercentageSeekbar.progress = defaultPercentage
        usePercentage(intToBoolean(entry.usePercentage()))

        binding.useEcarbs.isChecked = intToBoolean(entry.useEcarbs())
        binding.carbs2.value = SafeParse.stringToDouble(entry.carbs2().toString())
        binding.time.value = SafeParse.stringToDouble(entry.time().toString())
        binding.duration.value = SafeParse.stringToDouble(entry.duration().toString())
        useECarbs(intToBoolean(entry.useEcarbs()))

        binding.useCob.setOnClickListener(this)
        //binding.useEcarbs.setOnClickListener(this)
        processCob()
    }

    override fun onClick(v: View?) {
        processCob()
    }

    override fun onResume() {
        super.onResume()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("position", position)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun processCob() {
        if (binding.useCob.isChecked) {
            binding.useBolusIob.setEnabled(false)
            binding.useBasalIob.setEnabled(false)
            binding.useBolusIob.isChecked = true
            binding.useBasalIob.setSelection(QuickWizardEntry.YES)
        } else {
            binding.useBolusIob.setEnabled(true)
            binding.useBasalIob.setEnabled(true)
        }
    }

    private fun validateInputs() {
        val maxCarbs = constraintChecker.getMaxCarbsAllowed().value().toDouble()
        val time = binding.time.value.toInt()
        if (time > 12 * 60 || time < -7 * 24 * 60) {
            binding.time.value = 0.0
            ToastUtils.warnToast(ctx, app.aaps.core.ui.R.string.constraint_applied)
        }
        if (binding.duration.value > 10) {
            binding.duration.value = 0.0
            ToastUtils.warnToast(ctx, app.aaps.core.ui.R.string.constraint_applied)
        }
        if (binding.carbs2.value.toInt() > maxCarbs) {
            binding.carbs2.value = 0.0
            ToastUtils.warnToast(ctx, R.string.carbs_constraint_applied)
        }

        //validate input for normal carbs
        if (binding.carbsEdit.value.toInt() > maxCarbs) {
            binding.carbsEdit.value = 0.0
            ToastUtils.warnToast(ctx, R.string.carbs_constraint_applied)
        }

    }

    private val textWatcher: TextWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {
            _binding?.let {
                validateInputs()
            }
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    }

    fun booleanToInt(bool: Boolean):Int{
        return if (bool==true) 0 else 1
    }

    fun intToBoolean(theInt: Int):Boolean{
        return theInt==0
    }
}

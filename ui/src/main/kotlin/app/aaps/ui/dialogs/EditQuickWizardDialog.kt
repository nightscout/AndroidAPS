package app.aaps.ui.dialogs

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.core.objects.wizard.QuickWizardEntry
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
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var ctx: Context
    @Inject lateinit var preferences: Preferences

    var position = -1
    private var fromSeconds: Int = 0
    private var toSeconds: Int = 0

    companion object {

        const val MIN_PERCENTAGE: Double = 10.0
        const val MAX_PERCENTAGE: Double = 200.0
        const val DEFAULT_PERCENTAGE: Double = 100.0
    }

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
        if (preferences.get(BooleanKey.WearControl)) {
            binding.devicePhoneCheckbox.visibility = View.VISIBLE
            binding.deviceWatchCheckbox.visibility = View.VISIBLE
            binding.devicePhoneImage.visibility = View.VISIBLE
            binding.deviceWatchImage.visibility = View.VISIBLE
        } else {
            binding.devicePhoneCheckbox.visibility = View.GONE
            binding.deviceWatchCheckbox.visibility = View.GONE
            binding.devicePhoneImage.visibility = View.GONE
            binding.deviceWatchImage.visibility = View.GONE
        }

        if (preferences.get(BooleanKey.OverviewUseSuperBolus)) {
            binding.useSuperBolus.visibility = View.VISIBLE
        } else {
            binding.useSuperBolus.visibility = View.GONE
        }

        binding.okcancel.ok.setOnClickListener {
            val carbs = SafeParse.stringToInt(binding.carbsInput.text)
            val carbs2 = SafeParse.stringToInt(binding.carbs2.text)
            val useECarbs = binding.useEcarbs.isChecked

            if (carbs > 0 || (useECarbs && carbs2 > 0)) {
                try {
                    entry.storage.put("buttonText", binding.buttonEdit.text.toString())
                    entry.storage.put("carbs", carbs)
                    entry.storage.put("carbTime", SafeParse.stringToInt(binding.carbTimeInput.text))
                    entry.storage.put("useAlarm", checkBoxToRadioNumbers(binding.alarm.isChecked))
                    entry.storage.put("validFrom", fromSeconds)
                    entry.storage.put("validTo", toSeconds)
                    entry.storage.put("useBG", checkBoxToRadioNumbers(binding.useBg.isChecked))
                    entry.storage.put("useCOB", checkBoxToRadioNumbers(binding.useCob.isChecked))
                    entry.storage.put("useIOB", checkBoxToRadioNumbers(binding.useIob.isChecked))
                    entry.storage.put("usePositiveIOBOnly", checkBoxToRadioNumbers(binding.usePositiveIobOnly.isChecked))
                    entry.storage.put("useTrend", useTrendToInt())
                    entry.storage.put("useSuperBolus", checkBoxToRadioNumbers(binding.useSuperBolus.isChecked))
                    entry.storage.put("useTempTarget", checkBoxToRadioNumbers(binding.useTempTarget.isChecked))
                    entry.storage.put("percentage", binding.correctionInput.value)
                    if (binding.devicePhoneCheckbox.isChecked && binding.deviceWatchCheckbox.isChecked) {
                        entry.storage.put("device", QuickWizardEntry.DEVICE_ALL)
                    } else if (binding.devicePhoneCheckbox.isChecked) {
                        entry.storage.put("device", QuickWizardEntry.DEVICE_PHONE)
                    } else if (binding.deviceWatchCheckbox.isChecked) {
                        entry.storage.put("device", QuickWizardEntry.DEVICE_WATCH)
                    }
                    entry.storage.put("useEcarbs", checkBoxToRadioNumbers(useECarbs))
                    entry.storage.put("time", binding.time.value.toInt())
                    entry.storage.put("duration", SafeParse.stringToInt(binding.duration.text))
                    entry.storage.put("carbs2", carbs2)
                } catch (e: JSONException) {
                    aapsLogger.error("Unhandled exception", e)
                }

                quickWizard.addOrUpdate(entry)
                rxBus.send(EventQuickWizardChange())
                dismiss()
            } else {
                ToastUtils.warnToast(context, R.string.change_your_input)
            }
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

        fun useECarbs(yes: Boolean) {
            if (yes) {
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

        binding.useEcarbs.setOnCheckedChangeListener { _, checkedId ->
            useECarbs(checkedId)
        }

        binding.useIob.setOnCheckedChangeListener { _, _ -> processIOB() }

        val maxCarbs = constraintChecker.getMaxCarbsAllowed().value().toDouble()

        binding.correctionInput.setParams(
            savedInstanceState?.getDouble("percentage")
                ?: DEFAULT_PERCENTAGE, MIN_PERCENTAGE, MAX_PERCENTAGE, 5.0, DecimalFormat("0"), false, binding.okcancel.ok, textWatcher
        )

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

        binding.carbsInput.setParams(
            savedInstanceState?.getDouble("carbs2")
                ?: 0.0, 0.0, maxCarbs, 1.0, DecimalFormat("0"), false, binding.okcancel.ok, textWatcher
        )

        binding.carbTimeInput.setParams(
            savedInstanceState?.getDouble("carb_time_input")
                ?: 0.0, -60.0, 60.0, 5.0, DecimalFormat("0"), false, binding.okcancel.ok, timeTextWatcher
        )

        binding.correctionInput.value = entry.percentage().toDouble()

        toSeconds = entry.validTo()
        binding.to.text = dateUtil.timeString(dateUtil.secondsOfTheDayToMilliseconds(toSeconds))

        binding.buttonEdit.setText(entry.buttonText())
        when (entry.device()) {
            QuickWizardEntry.DEVICE_ALL   -> {
                binding.devicePhoneCheckbox.isChecked = true
                binding.deviceWatchCheckbox.isChecked = true
            }

            QuickWizardEntry.DEVICE_PHONE -> {
                binding.devicePhoneCheckbox.isChecked = true
                binding.deviceWatchCheckbox.isChecked = false
            }

            QuickWizardEntry.DEVICE_WATCH -> {
                binding.devicePhoneCheckbox.isChecked = false
                binding.deviceWatchCheckbox.isChecked = true
            }
        }
        binding.carbsInput.value = SafeParse.stringToDouble(entry.carbs().toString())
        binding.carbTimeInput.value = SafeParse.stringToDouble(entry.carbTime().toString())
        binding.alarm.isChecked = radioNumbersToCheckBox(entry.useAlarm())

        binding.useBg.isChecked = radioNumbersToCheckBox(entry.useBG())
        binding.useCob.isChecked = radioNumbersToCheckBox(entry.useCOB())
        binding.useIob.isChecked = radioNumbersToCheckBox(entry.useIOB())
        binding.usePositiveIobOnly.isChecked = radioNumbersToCheckBox(entry.usePositiveIOBOnly())
        processIntToTrendSelection(entry.useTrend())
        binding.useSuperBolus.isChecked = radioNumbersToCheckBox(entry.useSuperBolus())
        binding.useTempTarget.isChecked = radioNumbersToCheckBox(entry.useTempTarget())

        binding.useEcarbs.isChecked = radioNumbersToCheckBox(entry.useEcarbs())
        binding.carbs2.value = SafeParse.stringToDouble(entry.carbs2().toString())
        binding.time.value = SafeParse.stringToDouble(entry.time().toString())
        binding.duration.value = SafeParse.stringToDouble(entry.duration().toString())
        useECarbs(radioNumbersToCheckBox(entry.useEcarbs()))

        binding.useCob.setOnCheckedChangeListener { _, _ -> processCob() }

        binding.useTrendCheckbox.setOnCheckedChangeListener { _, _ -> processTrend() }

        processCob()
        processIOB()
    }

    override fun onClick(v: View?) {
        //
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
            binding.useIob.isChecked = true
        }
    }

    private fun processIOB() {
        if (!binding.useIob.isChecked) {
            binding.useCob.isChecked = false
            binding.usePositiveIobOnly.isChecked = false
            binding.usePositiveIobOnly.visibility = View.GONE
        } else {
            binding.usePositiveIobOnly.visibility = View.VISIBLE
        }
    }

    private fun processTrend() {
        if (binding.useTrendCheckbox.isChecked) {
            binding.useTrend.setSelection(0)
            binding.useTrend.visibility = View.VISIBLE
            binding.useTrendSubline.visibility = View.VISIBLE
        } else {
            binding.useTrend.visibility = View.GONE
            binding.useTrendSubline.visibility = View.GONE
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
        if (binding.carbsInput.value.toInt() > maxCarbs) {
            binding.carbsInput.value = 0.0
            ToastUtils.warnToast(ctx, R.string.carbs_constraint_applied)
        }

        //validate input for percentage
        if (binding.correctionInput.value.toInt() > MAX_PERCENTAGE) {
            binding.carbsInput.value = MAX_PERCENTAGE
            ToastUtils.warnToast(ctx, R.string.overview_edit_quickwizard_percentage_too_high)
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

    private val timeTextWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {
            _binding?.let { binding ->
                binding.alarm.isChecked = binding.carbTimeInput.value > 0
            }
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {

        }
    }

    //Radio Group to CheckBox transition - backward JSON compatibility
    private fun checkBoxToRadioNumbers(bool: Boolean): Int {
        return if (bool) 0 else 1
    }

    //Radio Group to CheckBox transition - backward JSON compatibility
    private fun radioNumbersToCheckBox(theInt: Int): Boolean {
        return theInt == 0
    }

    private fun useTrendToInt(): Int {
        if (binding.useTrendCheckbox.isChecked) {
            when (binding.useTrend.selectedItemPosition) {
                0 -> return QuickWizardEntry.YES
                1 -> return QuickWizardEntry.POSITIVE_ONLY
                2 -> return QuickWizardEntry.NEGATIVE_ONLY
            }
        } else {
            return QuickWizardEntry.NO
        }
        return QuickWizardEntry.NO
    }

    private fun processIntToTrendSelection(theInt: Int) {
        when (theInt) {
            QuickWizardEntry.YES           -> {
                binding.useTrendCheckbox.isChecked = true
                processTrend()
            }

            QuickWizardEntry.NO            -> {
                binding.useTrendCheckbox.isChecked = false
                processTrend()
            }

            QuickWizardEntry.POSITIVE_ONLY -> {
                binding.useTrendCheckbox.isChecked = true
                processTrend()
                binding.useTrend.setSelection(1)
            }

            QuickWizardEntry.NEGATIVE_ONLY -> {
                binding.useTrendCheckbox.isChecked = true
                processTrend()
                binding.useTrend.setSelection(2)
            }
        }
    }

}

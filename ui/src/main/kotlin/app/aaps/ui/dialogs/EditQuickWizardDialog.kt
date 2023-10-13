package app.aaps.ui.dialogs

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
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
    private var seekBarMoving = false
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

        binding.okcancel.ok.setOnClickListener {
            try {
                entry.storage.put("buttonText", binding.buttonEdit.text.toString())
                entry.storage.put("carbs", SafeParse.stringToInt(binding.carbsEdit.text))
                entry.storage.put("validFrom", fromSeconds)
                entry.storage.put("validTo", toSeconds)
                entry.storage.put("useBG", binding.useBg.isChecked)
                entry.storage.put("useCOB", booleanToInt(binding.useCob.isChecked))
                entry.storage.put("useIOB", booleanToInt(binding.useIob.isChecked))
                entry.storage.put("usePositiveIOBOnly", booleanToInt(binding.usePositiveIobOnly.isChecked))
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
                binding.usePercentage.text = ""
                binding.customPercentageEdittext.setText("$customPercentage")
                binding.customPercentageEdittext.visibility = View.VISIBLE
            } else {
                binding.customPercentageSeekbar.visibility = View.GONE
                binding.defaultPercentageTextview.visibility = View.VISIBLE
                binding.usePercentage.text = context?.getString(R.string.overview_edit_quickwizard_custom) + " %"
                binding.customPercentageEdittext.visibility = View.GONE
            }
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

        fun useIOB(checked: Boolean) {
            if (checked) {
                binding.usePositiveIobOnly.isEnabled = true
            } else {
                binding.usePositiveIobOnly.isChecked = false
                binding.usePositiveIobOnly.isEnabled = false
            }
        }

        binding.usePercentage.setOnCheckedChangeListener { _, checkedId ->
            usePercentage(checkedId)
        }

        binding.useEcarbs.setOnCheckedChangeListener { _, checkedId ->
            useECarbs(checkedId)
        }

        binding.useIob.setOnCheckedChangeListener { _, checkedId ->
            useIOB(checkedId)
        }

        binding.customPercentageSeekbar.setOnSeekBarChangeListener(object :
                                                                       SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seek: SeekBar,
                progress: Int, fromUser: Boolean
            ) {
                if (seekBarMoving) {
                    usePercentage(true)
                }
            }

            override fun onStartTrackingTouch(seek: SeekBar) {
                // write custom code for progress is started
                seekBarMoving = true
            }

            override fun onStopTrackingTouch(seek: SeekBar) {
                // write custom code for progress is stopped
                seekBarMoving = false
            }
        })

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

        toSeconds = entry.validTo()
        binding.to.text = dateUtil.timeString(dateUtil.secondsOfTheDayToMilliseconds(toSeconds))

        binding.buttonEdit.setText(entry.buttonText())
        binding.carbsEdit.value = SafeParse.stringToDouble(entry.carbs().toString())

        binding.useBg.isChecked = intToBoolean(entry.useBG())
        binding.useCob.isChecked = intToBoolean(entry.useCOB())
        binding.useIob.isChecked = intToBoolean(entry.useIOB())
        binding.usePositiveIobOnly.isChecked = intToBoolean(entry.usePositiveIOBOnly())
        binding.useTrend.setSelection(entry.useTrend())
        binding.useSuperBolus.isChecked = intToBoolean(entry.useSuperBolus())
        binding.useTempTarget.isChecked = intToBoolean(entry.useTempTarget())
        binding.usePercentage.isChecked = intToBoolean(entry.usePercentage())
        val defaultPercentage = entry.percentage() / 5
        binding.customPercentageSeekbar.progress = defaultPercentage
        usePercentage(intToBoolean(entry.usePercentage()))

        binding.useEcarbs.isChecked = intToBoolean(entry.useEcarbs())
        binding.carbs2.value = SafeParse.stringToDouble(entry.carbs2().toString())
        binding.time.value = SafeParse.stringToDouble(entry.time().toString())
        binding.duration.value = SafeParse.stringToDouble(entry.duration().toString())
        useECarbs(intToBoolean(entry.useEcarbs()))

        binding.useCob.setOnCheckedChangeListener { _, checkedId ->
            processCob()
        }

        binding.useTrendCheckbox.setOnCheckedChangeListener { _, checkedId ->
            processTrend()
        }

        //set hard limits for custom percentage edittext + update seekbar position
        binding.customPercentageEdittext.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                val customPercentageInt: Int = SafeParse.stringToInt(s.toString())

                if (customPercentageInt > 200) {
                    binding.customPercentageEdittext.setText("200")
                }
                if (!binding.customPercentageEdittext.text.isEmpty()) {
                    binding.customPercentageSeekbar.progress = SafeParse.stringToInt(binding.customPercentageEdittext.text.toString()) / 5
                }
            }

            override fun beforeTextChanged(
                s: CharSequence, start: Int,
                count: Int, after: Int
            ) {
            }

            override fun onTextChanged(
                s: CharSequence, start: Int,
                before: Int, count: Int
            ) {
            }
        })

        binding.customPercentageEdittext.setOnFocusChangeListener(OnFocusChangeListener { v, hasFocus ->
            if (!hasFocus) {
                // code to execute when EditText loses focus
                if (!binding.customPercentageEdittext.text.isEmpty()) {
                    val customPercentageInt: Int = SafeParse.stringToInt(binding.customPercentageEdittext.text.toString())
                    if (customPercentageInt < 10) {
                        binding.customPercentageEdittext.setText("10")
                    }
                } else {
                    binding.customPercentageEdittext.setText("10")
                }
            }
        })

        processCob()

        binding.useTrend.setOnItemSelectedListener(object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                binding.useTrendCheckbox.isChecked = binding.useTrend.selectedItemPosition != QuickWizardEntry.NO
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        })
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
            binding.useIob.setEnabled(false)
            binding.useIob.isChecked = true
        } else {
            binding.useIob.setEnabled(true)
        }
    }

    private fun processTrend() {
        if (binding.useTrendCheckbox.isChecked) {
            if (binding.useTrend.selectedItemPosition == QuickWizardEntry.NO) binding.useTrend.setSelection(QuickWizardEntry.YES)
        } else {
            binding.useTrend.setSelection(QuickWizardEntry.NO)
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

    //Radio Group to CheckBox transition - backward JSON compatibility
    private fun booleanToInt(bool: Boolean): Int {
        return if (bool == true) 0 else 1
    }

    //Radio Group to CheckBox transition - backward JSON compatibility
    private fun intToBoolean(theInt: Int): Boolean {
        return theInt == 0
    }
}

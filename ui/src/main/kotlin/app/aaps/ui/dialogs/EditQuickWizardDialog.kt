package app.aaps.ui.dialogs

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.interfaces.utils.T
import app.aaps.core.main.wizard.QuickWizard
import app.aaps.core.main.wizard.QuickWizardEntry
import app.aaps.core.ui.extensions.selectedItemPosition
import app.aaps.core.ui.extensions.setEnableForChildren
import app.aaps.core.ui.extensions.setSelection
import app.aaps.ui.R
import app.aaps.ui.databinding.DialogEditQuickwizardBinding
import app.aaps.ui.events.EventQuickWizardChange
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dagger.android.support.DaggerDialogFragment
import org.json.JSONException
import javax.inject.Inject

class EditQuickWizardDialog : DaggerDialogFragment(), View.OnClickListener {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var quickWizard: QuickWizard
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var sp: SP

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
                entry.storage.put("carbs", SafeParse.stringToInt(binding.carbsEdit.text.toString()))
                entry.storage.put("validFrom", fromSeconds)
                entry.storage.put("validTo", toSeconds)
                entry.storage.put("useBG", binding.useBg.selectedItemPosition)
                entry.storage.put("useCOB", binding.useCob.selectedItemPosition)
                entry.storage.put("useBolusIOB", binding.useBolusIob.selectedItemPosition)
                entry.storage.put("device", binding.device.selectedItemPosition)
                entry.storage.put("useBasalIOB", binding.useBasalIob.selectedItemPosition)
                entry.storage.put("useTrend", binding.useTrend.selectedItemPosition)
                entry.storage.put("useSuperBolus", binding.useSuperBolus.selectedItemPosition)
                entry.storage.put("useTempTarget", binding.useTempTarget.selectedItemPosition)
                entry.storage.put("usePercentage", binding.usePercentage.selectedItemPosition)
                val percentage = SafeParse.stringToInt(binding.percentage.text.toString())
                entry.storage.put("percentage", percentage)
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
                binding.percentageLabel.visibility = View.VISIBLE
                binding.percentage.visibility = View.VISIBLE
            } else {
                binding.percentageLabel.visibility = View.GONE
                binding.percentage.visibility = View.GONE
            }
        }

        binding.usePercentage.setOnCheckedChangeListener { _, checkedId ->
            usePercentage(checkedId == R.id.use_percentage_custom)
        }

        toSeconds = entry.validTo()
        binding.to.text = dateUtil.timeString(dateUtil.secondsOfTheDayToMilliseconds(toSeconds))

        binding.buttonEdit.setText(entry.buttonText())
        binding.carbsEdit.setText(entry.carbs().toString())

        binding.useBg.setSelection(entry.useBG())
        binding.useCob.setSelection(entry.useCOB())
        binding.useBolusIob.setSelection(entry.useBolusIOB())
        binding.useBasalIob.setSelection(entry.useBasalIOB())
        binding.device.setSelection(entry.device())
        binding.useTrend.setSelection(entry.useTrend())
        binding.useSuperBolus.setSelection(entry.useSuperBolus())
        binding.useTempTarget.setSelection(entry.useTempTarget())
        binding.usePercentage.setSelection(entry.usePercentage())
        usePercentage(entry.usePercentage() == QuickWizardEntry.CUSTOM)
        binding.percentage.setText(entry.percentage().toString())
        binding.useCobYes.setOnClickListener(this)
        binding.useCobNo.setOnClickListener(this)
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
        if (binding.useCob.selectedItemPosition == QuickWizardEntry.YES) {
            binding.useBolusIob.setEnableForChildren(false)
            binding.useBasalIob.setEnableForChildren(false)
            binding.useBolusIob.setSelection(QuickWizardEntry.YES)
            binding.useBasalIob.setSelection(QuickWizardEntry.YES)
        } else {
            binding.useBolusIob.setEnableForChildren(true)
            binding.useBasalIob.setEnableForChildren(true)
        }
    }
}

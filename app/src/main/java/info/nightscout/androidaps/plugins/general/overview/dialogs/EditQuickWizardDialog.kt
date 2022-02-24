package info.nightscout.androidaps.plugins.general.overview.dialogs

import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import dagger.android.support.DaggerDialogFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.OverviewEditquickwizardDialogBinding
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.overview.events.EventQuickWizardChange
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.shared.SafeParse
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.extensions.selectedItemPosition
import info.nightscout.androidaps.utils.extensions.setEnableForChildren
import info.nightscout.androidaps.utils.extensions.setSelection
import info.nightscout.androidaps.utils.wizard.QuickWizard
import info.nightscout.androidaps.utils.wizard.QuickWizardEntry
import info.nightscout.shared.sharedPreferences.SP
import org.json.JSONException
import javax.inject.Inject

class EditQuickWizardDialog : DaggerDialogFragment(), View.OnClickListener {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var quickWizard: QuickWizard
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var sp: SP

    var position = -1
    var fromSeconds: Int = 0
    var toSeconds: Int = 0

    private var _binding: OverviewEditquickwizardDialogBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(false)
        _binding = OverviewEditquickwizardDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (arguments ?: savedInstanceState)?.let { bundle ->
            position = bundle.getInt("position", -1)
        }
        val entry = if (position == -1) quickWizard.newEmptyItem() else quickWizard[position]
        if (sp.getBoolean(R.string.key_wear_control, false)) {
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

        // create an OnTimeSetListener
        val fromTimeSetListener = TimePickerDialog.OnTimeSetListener { _, hour, minute ->
            fromSeconds = (T.hours(hour.toLong()).secs() + T.mins(minute.toLong()).secs()).toInt()
            binding.from.text = dateUtil.timeString(dateUtil.secondsOfTheDayToMilliseconds(fromSeconds))
        }

        binding.from.setOnClickListener {
            context?.let {
                TimePickerDialog(
                    it, R.style.MaterialPickerTheme,
                    fromTimeSetListener,
                    T.secs(fromSeconds.toLong()).hours().toInt(),
                    T.secs((fromSeconds % 3600).toLong()).mins().toInt(),
                    DateFormat.is24HourFormat(context)
                ).show()
            }
        }
        fromSeconds = entry.validFrom()
        binding.from.text = dateUtil.timeString(dateUtil.secondsOfTheDayToMilliseconds(fromSeconds))

        val toTimeSetListener = TimePickerDialog.OnTimeSetListener { _, hour, minute ->
            toSeconds = (T.hours(hour.toLong()).secs() + T.mins(minute.toLong()).secs()).toInt()
            binding.to.text = dateUtil.timeString(dateUtil.secondsOfTheDayToMilliseconds(toSeconds))
        }

        binding.to.setOnClickListener {
            context?.let {
                TimePickerDialog(
                    it, R.style.MaterialPickerTheme,
                    toTimeSetListener,
                    T.secs(toSeconds.toLong()).hours().toInt(),
                    T.secs((toSeconds % 3600).toLong()).mins().toInt(),
                    DateFormat.is24HourFormat(context)
                ).show()
            }
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

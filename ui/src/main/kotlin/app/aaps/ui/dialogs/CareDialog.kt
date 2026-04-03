package app.aaps.ui.dialogs

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.keys.BooleanKey
import app.aaps.ui.R
import app.aaps.ui.databinding.DialogCareBinding
import com.google.common.base.Joiner
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.util.LinkedList
import javax.inject.Inject

class CareDialog(val fm: FragmentManager) : DialogFragmentWithDate() {

    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var translator: Translator
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var uiInteraction: UiInteraction

    private var options: UiInteraction.EventType = UiInteraction.EventType.BGCHECK

    //private var valuesWithUnit = mutableListOf<XXXValueWithUnit?>()
    private var valuesWithUnit = mutableListOf<ValueWithUnit?>()

    @StringRes
    private var event: Int = app.aaps.core.ui.R.string.none

    private var _binding: DialogCareBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putInt("event", event)
        savedInstanceState.putInt("options", options.ordinal)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        onCreateViewGeneral()
        _binding = DialogCareBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (savedInstanceState ?: arguments)?.let {
            event = it.getInt("event", app.aaps.core.ui.R.string.error)
            options = UiInteraction.EventType.entries.toTypedArray()[it.getInt("options", 0)]
        }

        binding.icon.setImageResource(
            when (options) {
                UiInteraction.EventType.BGCHECK        -> app.aaps.core.objects.R.drawable.ic_cp_bgcheck
                UiInteraction.EventType.SENSOR_INSERT  -> app.aaps.core.objects.R.drawable.ic_cp_cgm_insert
                UiInteraction.EventType.BATTERY_CHANGE -> app.aaps.core.objects.R.drawable.ic_cp_pump_battery
                UiInteraction.EventType.NOTE           -> app.aaps.core.objects.R.drawable.ic_cp_note
                UiInteraction.EventType.EXERCISE       -> app.aaps.core.objects.R.drawable.ic_cp_exercise
                UiInteraction.EventType.QUESTION       -> app.aaps.core.objects.R.drawable.ic_cp_question
                UiInteraction.EventType.ANNOUNCEMENT   -> app.aaps.core.objects.R.drawable.ic_cp_announcement
            }
        )
        binding.title.text = rh.gs(
            when (options) {
                UiInteraction.EventType.BGCHECK        -> app.aaps.core.ui.R.string.careportal_bgcheck
                UiInteraction.EventType.SENSOR_INSERT  -> app.aaps.core.ui.R.string.cgm_sensor_insert
                UiInteraction.EventType.BATTERY_CHANGE -> app.aaps.core.ui.R.string.pump_battery_change
                UiInteraction.EventType.NOTE           -> app.aaps.core.ui.R.string.careportal_note
                UiInteraction.EventType.EXERCISE       -> app.aaps.core.ui.R.string.careportal_exercise
                UiInteraction.EventType.QUESTION       -> app.aaps.core.ui.R.string.careportal_question
                UiInteraction.EventType.ANNOUNCEMENT   -> app.aaps.core.ui.R.string.careportal_announcement
            }
        )

        when (options) {
            UiInteraction.EventType.QUESTION,
            UiInteraction.EventType.ANNOUNCEMENT,
            UiInteraction.EventType.BGCHECK        -> {
                binding.durationLayout.visibility = View.GONE
            }

            UiInteraction.EventType.SENSOR_INSERT,
            UiInteraction.EventType.BATTERY_CHANGE -> {
                binding.bgLayout.visibility = View.GONE
                binding.bgsource.visibility = View.GONE
                binding.durationLayout.visibility = View.GONE
            }

            UiInteraction.EventType.NOTE,
            UiInteraction.EventType.EXERCISE       -> {
                binding.bgLayout.visibility = View.GONE
                binding.bgsource.visibility = View.GONE
            }
        }

        val bg = profileUtil.fromMgdlToUnits(glucoseStatusProvider.glucoseStatusData?.glucose ?: 0.0)
        val bgTextWatcher: TextWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable) {}
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (binding.sensor.isChecked) binding.meter.isChecked = true
            }
        }

        if (profileFunction.getUnits() == GlucoseUnit.MMOL) {
            binding.bgUnits.text = rh.gs(app.aaps.core.ui.R.string.mmol)
            binding.bg.setParams(
                savedInstanceState?.getDouble("bg")
                    ?: bg, 2.0, 30.0, 0.1, DecimalFormat("0.0"), false, binding.okcancel.ok, bgTextWatcher
            )
        } else {
            binding.bgUnits.text = rh.gs(app.aaps.core.ui.R.string.mgdl)
            binding.bg.setParams(
                savedInstanceState?.getDouble("bg")
                    ?: bg, 36.0, 500.0, 1.0, DecimalFormat("0"), false, binding.okcancel.ok, bgTextWatcher
            )
        }
        binding.duration.setParams(
            savedInstanceState?.getDouble("duration")
                ?: 0.0, 0.0, Constants.MAX_PROFILE_SWITCH_DURATION, 10.0, DecimalFormat("0"), false, binding.okcancel.ok
        )
        if (options == UiInteraction.EventType.NOTE || options == UiInteraction.EventType.QUESTION || options == UiInteraction.EventType.ANNOUNCEMENT || options == UiInteraction.EventType.EXERCISE)
            binding.notesLayout.root.visibility = View.VISIBLE // independent to preferences
        binding.bgLabel.labelFor = binding.bg.editTextId
        binding.durationLabel.labelFor = binding.duration.editTextId
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun submit(): Boolean {
        val enteredBy = "AAPS"
        val unitResId = if (profileFunction.getUnits() == GlucoseUnit.MGDL) app.aaps.core.ui.R.string.mgdl else app.aaps.core.ui.R.string.mmol

        eventTime -= eventTime % 1000

        val therapyEvent = TE(
            timestamp = eventTime,
            type = when (options) {
                UiInteraction.EventType.BGCHECK        -> TE.Type.FINGER_STICK_BG_VALUE
                UiInteraction.EventType.SENSOR_INSERT  -> TE.Type.SENSOR_CHANGE
                UiInteraction.EventType.BATTERY_CHANGE -> TE.Type.PUMP_BATTERY_CHANGE
                UiInteraction.EventType.NOTE           -> TE.Type.NOTE
                UiInteraction.EventType.EXERCISE       -> TE.Type.EXERCISE
                UiInteraction.EventType.QUESTION       -> TE.Type.QUESTION
                UiInteraction.EventType.ANNOUNCEMENT   -> TE.Type.ANNOUNCEMENT
            },
            glucoseUnit = profileFunction.getUnits()
        )

        val actions: LinkedList<String> = LinkedList()
        actions.add(rh.gs(R.string.confirm_treatment))
        if (options == UiInteraction.EventType.BGCHECK || options == UiInteraction.EventType.QUESTION || options == UiInteraction.EventType.ANNOUNCEMENT) {
            val meterType =
                when {
                    binding.meter.isChecked  -> TE.MeterType.FINGER
                    binding.sensor.isChecked -> TE.MeterType.SENSOR
                    else                     -> TE.MeterType.MANUAL
                }
            actions.add(rh.gs(R.string.glucose_type) + ": " + translator.translate(meterType))
            actions.add(rh.gs(app.aaps.core.ui.R.string.bg_label) + ": " + profileUtil.stringInCurrentUnitsDetect(binding.bg.value) + " " + rh.gs(unitResId))
            therapyEvent.glucoseType = meterType
            therapyEvent.glucose = binding.bg.value
            valuesWithUnit.add(ValueWithUnit.fromGlucoseUnit(binding.bg.value, profileFunction.getUnits()))
            valuesWithUnit.add(ValueWithUnit.TEMeterType(meterType))
        }
        if (options == UiInteraction.EventType.NOTE || options == UiInteraction.EventType.EXERCISE) {
            actions.add(rh.gs(app.aaps.core.ui.R.string.duration_label) + ": " + rh.gs(app.aaps.core.ui.R.string.format_mins, binding.duration.value.toInt()))
            therapyEvent.duration = T.mins(binding.duration.value.toLong()).msecs()
            valuesWithUnit.add(ValueWithUnit.Minute(binding.duration.value.toInt()).takeIf { binding.duration.value != 0.0 })
        }
        val notes = binding.notesLayout.notes.text.toString()
        if (notes.isNotEmpty()) {
            actions.add(rh.gs(app.aaps.core.ui.R.string.notes_label) + ": " + notes)
            therapyEvent.note = notes
        }

        if (eventTimeChanged) actions.add(rh.gs(app.aaps.core.ui.R.string.time) + ": " + dateUtil.dateAndTimeString(eventTime))

        therapyEvent.enteredBy = enteredBy

        val source = when (options) {
            UiInteraction.EventType.BGCHECK        -> Sources.BgCheck
            UiInteraction.EventType.SENSOR_INSERT  -> Sources.SensorInsert
            UiInteraction.EventType.BATTERY_CHANGE -> Sources.BatteryChange
            UiInteraction.EventType.NOTE           -> Sources.Note
            UiInteraction.EventType.EXERCISE       -> Sources.Exercise
            UiInteraction.EventType.QUESTION       -> Sources.Question
            UiInteraction.EventType.ANNOUNCEMENT   -> Sources.Announcement
        }

        uiInteraction.showOkCancelDialog(
            context = requireActivity(),
            title = rh.gs(event),
            message = Joiner.on("<br/>").join(actions),
            ok = {
                valuesWithUnit.add(0, ValueWithUnit.Timestamp(eventTime).takeIf { eventTimeChanged })
                valuesWithUnit.add(1, ValueWithUnit.TEType(therapyEvent.type))
                lifecycleScope.launch {
                    persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                        therapyEvent = therapyEvent,
                        action = Action.CAREPORTAL,
                        source = source,
                        note = notes,
                        listValues = valuesWithUnit.filterNotNull()
                    )
                }
                if (therapyEvent.type == TE.Type.SENSOR_CHANGE && preferences.get(BooleanKey.SiteRotationManageCgm)) {
                    SiteRotationDialog().also { srd ->
                        srd.arguments = Bundle().also { args ->
                            args.putLong("time", therapyEvent.timestamp)
                            args.putInt("siteMode", UiInteraction.SiteMode.EDIT.ordinal)
                            args.putInt("siteType", TE.Type.SENSOR_CHANGE.ordinal)
                        }
                        srd.show(fm, "SiteRotationViewDialog")
                    }
                }
            },
            cancel = null
        )
        return true
    }
}
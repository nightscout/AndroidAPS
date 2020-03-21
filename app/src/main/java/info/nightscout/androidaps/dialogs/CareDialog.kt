package info.nightscout.androidaps.dialogs

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.annotation.StringRes
import com.google.common.base.Joiner
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.OKDialog
import info.nightscout.androidaps.utils.SP
import info.nightscout.androidaps.utils.Translator
import kotlinx.android.synthetic.main.dialog_care.*
import kotlinx.android.synthetic.main.notes.*
import kotlinx.android.synthetic.main.okcancel.*
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.*

class CareDialog : DialogFragmentWithDate() {

    enum class EventType {
        BGCHECK,
        SENSOR_INSERT,
        BATTERY_CHANGE,
        NOTE,
        EXERCISE
    }

    private var options: EventType = EventType.BGCHECK
    @StringRes
    private var event: Int = R.string.none

    fun setOptions(options: EventType, @StringRes event: Int): CareDialog {
        this.options = options
        this.event = event
        return this
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("actions_care_bg", actions_care_bg.value)
        savedInstanceState.putDouble("actions_care_duration", actions_care_duration.value)
        savedInstanceState.putInt("event", event)
        savedInstanceState.putInt("options", options.ordinal)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        onCreateViewGeneral()
        return inflater.inflate(R.layout.dialog_care, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        savedInstanceState?.let {
            event = savedInstanceState.getInt("event", R.string.error)
            options = EventType.values()[savedInstanceState.getInt("options", 0)]
        }

        actions_care_icon.setImageResource(when (options) {
            EventType.BGCHECK        -> R.drawable.icon_cp_bgcheck
            EventType.SENSOR_INSERT  -> R.drawable.icon_cp_cgm_insert
            EventType.BATTERY_CHANGE -> R.drawable.icon_cp_pump_battery
            EventType.NOTE           -> R.drawable.icon_cp_note
            EventType.EXERCISE       -> R.drawable.icon_cp_exercise
        })
        actions_care_title.text = MainApp.gs(when (options) {
            EventType.BGCHECK        -> R.string.careportal_bgcheck
            EventType.SENSOR_INSERT  -> R.string.careportal_cgmsensorinsert
            EventType.BATTERY_CHANGE -> R.string.careportal_pumpbatterychange
            EventType.NOTE           -> R.string.careportal_note
            EventType.EXERCISE       -> R.string.careportal_exercise
        })

        when (options) {
            EventType.BGCHECK        -> {
                action_care_duration_layout.visibility = View.GONE
            }
            EventType.SENSOR_INSERT,
            EventType.BATTERY_CHANGE -> {
                action_care_bg_layout.visibility = View.GONE
                actions_care_bgsource.visibility = View.GONE
                action_care_duration_layout.visibility = View.GONE
            }
            EventType.NOTE,
            EventType.EXERCISE -> {
                action_care_bg_layout.visibility = View.GONE
                actions_care_bgsource.visibility = View.GONE
            }
        }

        val bg = Profile.fromMgdlToUnits(GlucoseStatus.getGlucoseStatusData()?.glucose
            ?: 0.0, ProfileFunctions.getSystemUnits())
        val bgTextWatcher: TextWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable) {}
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (actions_care_sensor.isChecked) actions_care_meter.isChecked = true
            }
        }

        if (ProfileFunctions.getSystemUnits() == Constants.MMOL) {
            actions_care_bgunits.text = MainApp.gs(R.string.mmol)
            actions_care_bg.setParams(savedInstanceState?.getDouble("actions_care_bg")
                ?: bg, 2.0, 30.0, 0.1, DecimalFormat("0.0"), false, ok, bgTextWatcher)
        } else {
            actions_care_bgunits.text = MainApp.gs(R.string.mgdl)
            actions_care_bg.setParams(savedInstanceState?.getDouble("actions_care_bg")
                ?: bg, 36.0, 500.0, 1.0, DecimalFormat("0"), false, ok, bgTextWatcher)
        }
        actions_care_duration.setParams(savedInstanceState?.getDouble("actions_care_duration")
            ?: 0.0, 0.0, Constants.MAX_PROFILE_SWITCH_DURATION, 10.0, DecimalFormat("0"), false, ok)
        if (options == EventType.NOTE)
            notes_layout?.visibility = View.VISIBLE // independent to preferences
    }

    override fun submit(): Boolean {
        val enteredBy = SP.getString("careportal_enteredby", "")
        val unitResId = if (ProfileFunctions.getSystemUnits() == Constants.MGDL) R.string.mgdl else R.string.mmol

        val json = JSONObject()
        val actions: LinkedList<String> = LinkedList()
        if (options == EventType.BGCHECK) {
            val type =
                when {
                    actions_care_meter.isChecked  -> "Finger"
                    actions_care_sensor.isChecked -> "Sensor"
                    else                          -> "Manual"
                }
            actions.add(MainApp.gs(R.string.careportal_newnstreatment_glucosetype) + ": " + Translator.translate(type))
            actions.add(MainApp.gs(R.string.treatments_wizard_bg_label) + ": " + Profile.toCurrentUnitsString(actions_care_bg.value) + " " + MainApp.gs(unitResId))
            json.put("glucose", actions_care_bg.value)
            json.put("glucoseType", type)
        }
        if (options == EventType.NOTE || options == EventType.EXERCISE) {
            actions.add(MainApp.gs(R.string.careportal_newnstreatment_duration_label) + ": " + MainApp.gs(R.string.format_mins, actions_care_duration.value.toInt()))
            json.put("duration", actions_care_duration.value.toInt())
        }
        val notes = notes.text.toString()
        if (notes.isNotEmpty()) {
            actions.add(MainApp.gs(R.string.careportal_newnstreatment_notes_label) + ": " + notes)
            json.put("notes", notes)
        }
        eventTime -= eventTime % 1000

        if (eventTimeChanged)
            actions.add(MainApp.gs(R.string.time) + ": " + DateUtil.dateAndTimeString(eventTime))

        json.put("created_at", DateUtil.toISOString(eventTime))
        json.put("mills", eventTime)
        json.put("eventType", when (options) {
            EventType.BGCHECK        -> CareportalEvent.BGCHECK
            EventType.SENSOR_INSERT  -> CareportalEvent.SENSORCHANGE
            EventType.BATTERY_CHANGE -> CareportalEvent.PUMPBATTERYCHANGE
            EventType.NOTE           -> CareportalEvent.NOTE
            EventType.EXERCISE       -> CareportalEvent.EXERCISE
        })
        json.put("units", ProfileFunctions.getSystemUnits())
        if (enteredBy.isNotEmpty())
            json.put("enteredBy", enteredBy)

        activity?.let { activity ->
            OKDialog.showConfirmation(activity, MainApp.gs(event), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), Runnable {
                val careportalEvent = CareportalEvent()
                careportalEvent.date = eventTime
                careportalEvent.source = Source.USER
                careportalEvent.eventType = when (options) {
                    EventType.BGCHECK        -> CareportalEvent.BGCHECK
                    EventType.SENSOR_INSERT  -> CareportalEvent.SENSORCHANGE
                    EventType.BATTERY_CHANGE -> CareportalEvent.PUMPBATTERYCHANGE
                    EventType.NOTE           -> CareportalEvent.NOTE
                    EventType.EXERCISE       -> CareportalEvent.EXERCISE
                }
                careportalEvent.json = json.toString()
                log.debug("USER ENTRY: CAREPORTAL ${careportalEvent.eventType} json: ${careportalEvent.json}")
                MainApp.getDbHelper().createOrUpdate(careportalEvent)
                NSUpload.uploadCareportalEntryToNS(json)
            }, null)
        }
        return true
    }
}

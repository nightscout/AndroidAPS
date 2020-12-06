package info.nightscout.androidaps.dialogs

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import com.google.common.base.Joiner
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.Translator
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import kotlinx.android.synthetic.main.dialog_care.*
import kotlinx.android.synthetic.main.notes.*
import kotlinx.android.synthetic.main.okcancel.*
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.*
import javax.inject.Inject

class CareDialog : DialogFragmentWithDate() {
    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var mainApp: MainApp
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var nsUpload: NSUpload
    @Inject lateinit var translator: Translator

    enum class EventType {
        BGCHECK,
        SENSOR_INSERT,
        BATTERY_CHANGE,
        NOTE,
        EXERCISE,
        QUESTION,
        ANNOUNCEMENT
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
            EventType.BGCHECK        -> R.drawable.ic_cp_bgcheck
            EventType.SENSOR_INSERT  -> R.drawable.ic_cp_cgm_insert
            EventType.BATTERY_CHANGE -> R.drawable.ic_cp_pump_battery
            EventType.NOTE           -> R.drawable.ic_cp_note
            EventType.EXERCISE       -> R.drawable.ic_cp_exercise
            EventType.QUESTION       -> R.drawable.ic_cp_question
            EventType.ANNOUNCEMENT   -> R.drawable.ic_cp_announcement
        })
        actions_care_title.text = resourceHelper.gs(when (options) {
            EventType.BGCHECK        -> R.string.careportal_bgcheck
            EventType.SENSOR_INSERT  -> R.string.careportal_cgmsensorinsert
            EventType.BATTERY_CHANGE -> R.string.careportal_pumpbatterychange
            EventType.NOTE           -> R.string.careportal_note
            EventType.EXERCISE       -> R.string.careportal_exercise
            EventType.QUESTION       -> R.string.careportal_question
            EventType.ANNOUNCEMENT   -> R.string.careportal_announcement
        })

        when (options) {
            EventType.QUESTION,
            EventType.ANNOUNCEMENT,
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
            EventType.EXERCISE       -> {
                action_care_bg_layout.visibility = View.GONE
                actions_care_bgsource.visibility = View.GONE
            }
        }

        val bg = Profile.fromMgdlToUnits(GlucoseStatus(injector).glucoseStatusData?.glucose
            ?: 0.0, profileFunction.getUnits())
        val bgTextWatcher: TextWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable) {}
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (actions_care_sensor.isChecked) actions_care_meter.isChecked = true
            }
        }

        if (profileFunction.getUnits() == Constants.MMOL) {
            actions_care_bgunits.text = resourceHelper.gs(R.string.mmol)
            actions_care_bg.setParams(savedInstanceState?.getDouble("actions_care_bg")
                ?: bg, 2.0, 30.0, 0.1, DecimalFormat("0.0"), false, ok, bgTextWatcher)
        } else {
            actions_care_bgunits.text = resourceHelper.gs(R.string.mgdl)
            actions_care_bg.setParams(savedInstanceState?.getDouble("actions_care_bg")
                ?: bg, 36.0, 500.0, 1.0, DecimalFormat("0"), false, ok, bgTextWatcher)
        }
        actions_care_duration.setParams(savedInstanceState?.getDouble("actions_care_duration")
            ?: 0.0, 0.0, Constants.MAX_PROFILE_SWITCH_DURATION, 10.0, DecimalFormat("0"), false, ok)
        if (options == EventType.NOTE || options == EventType.QUESTION || options == EventType.ANNOUNCEMENT)
            notes_layout?.visibility = View.VISIBLE // independent to preferences
    }

    override fun submit(): Boolean {
        val enteredBy = sp.getString("careportal_enteredby", "")
        val unitResId = if (profileFunction.getUnits() == Constants.MGDL) R.string.mgdl else R.string.mmol

        val json = JSONObject()
        val actions: LinkedList<String> = LinkedList()
        if (options == EventType.BGCHECK || options == EventType.QUESTION || options == EventType.ANNOUNCEMENT) {
            val type =
                when {
                    actions_care_meter.isChecked  -> CareportalEvent.FINGER
                    actions_care_sensor.isChecked -> CareportalEvent.SENSOR
                    else                          -> CareportalEvent.MANUAL
                }
            actions.add(resourceHelper.gs(R.string.careportal_newnstreatment_glucosetype) + ": " + translator.translate(type))
            actions.add(resourceHelper.gs(R.string.treatments_wizard_bg_label) + ": " + Profile.toCurrentUnitsString(profileFunction, actions_care_bg.value) + " " + resourceHelper.gs(unitResId))
            json.put("glucose", actions_care_bg.value)
            json.put("glucoseType", type)
        }
        if (options == EventType.NOTE || options == EventType.EXERCISE) {
            actions.add(resourceHelper.gs(R.string.careportal_newnstreatment_duration_label) + ": " + resourceHelper.gs(R.string.format_mins, actions_care_duration.value.toInt()))
            json.put("duration", actions_care_duration.value.toInt())
        }
        val notes = notes.text.toString()
        if (notes.isNotEmpty()) {
            actions.add(resourceHelper.gs(R.string.careportal_newnstreatment_notes_label) + ": " + notes)
            json.put("notes", notes)
        }
        eventTime -= eventTime % 1000

        if (eventTimeChanged)
            actions.add(resourceHelper.gs(R.string.time) + ": " + dateUtil.dateAndTimeString(eventTime))

        json.put("created_at", DateUtil.toISOString(eventTime))
        json.put("mills", eventTime)
        json.put("eventType", when (options) {
            EventType.BGCHECK        -> CareportalEvent.BGCHECK
            EventType.SENSOR_INSERT  -> CareportalEvent.SENSORCHANGE
            EventType.BATTERY_CHANGE -> CareportalEvent.PUMPBATTERYCHANGE
            EventType.NOTE           -> CareportalEvent.NOTE
            EventType.EXERCISE       -> CareportalEvent.EXERCISE
            EventType.QUESTION       -> CareportalEvent.QUESTION
            EventType.ANNOUNCEMENT   -> CareportalEvent.ANNOUNCEMENT
        })
        json.put("units", profileFunction.getUnits())
        if (enteredBy.isNotEmpty())
            json.put("enteredBy", enteredBy)

        activity?.let { activity ->
            OKDialog.showConfirmation(activity, resourceHelper.gs(event), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), Runnable {
                val careportalEvent = CareportalEvent(injector)
                careportalEvent.date = eventTime
                careportalEvent.source = Source.USER
                careportalEvent.eventType = when (options) {
                    EventType.BGCHECK        -> CareportalEvent.BGCHECK
                    EventType.SENSOR_INSERT  -> CareportalEvent.SENSORCHANGE
                    EventType.BATTERY_CHANGE -> CareportalEvent.PUMPBATTERYCHANGE
                    EventType.NOTE           -> CareportalEvent.NOTE
                    EventType.EXERCISE       -> CareportalEvent.EXERCISE
                    EventType.QUESTION       -> CareportalEvent.QUESTION
                    EventType.ANNOUNCEMENT   -> CareportalEvent.ANNOUNCEMENT
                }
                careportalEvent.json = json.toString()
                aapsLogger.debug("USER ENTRY: CAREPORTAL ${careportalEvent.eventType} json: ${careportalEvent.json}")
                MainApp.getDbHelper().createOrUpdate(careportalEvent)
                nsUpload.uploadCareportalEntryToNS(json)
            }, null)
        }
        return true
    }
}

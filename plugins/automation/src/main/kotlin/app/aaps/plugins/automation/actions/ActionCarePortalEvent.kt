package app.aaps.plugins.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.elements.InputCarePortalMenu
import app.aaps.plugins.automation.elements.InputDuration
import app.aaps.plugins.automation.elements.InputString
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.json.JSONObject
import javax.inject.Inject

class ActionCarePortalEvent(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider

    private val disposable = CompositeDisposable()

    var note = InputString()
    var duration = InputDuration(0, InputDuration.TimeUnit.MINUTES)
    var cpEvent = InputCarePortalMenu(rh)
    private var valuesWithUnit = mutableListOf<ValueWithUnit>()

    override fun friendlyName(): Int = app.aaps.core.ui.R.string.careportal
    override fun shortDescription(): String = rh.gs(cpEvent.value.stringResWithValue, note.value)

    @DrawableRes override fun icon(): Int = cpEvent.value.drawableRes

    override fun doAction(callback: Callback) {
        val enteredBy = "AAPS"
        val eventTime = dateUtil.now()
        val therapyEvent = TE(
            timestamp = eventTime,
            type = cpEvent.value.therapyEventType,
            glucoseUnit = profileFunction.getUnits()
        )
        valuesWithUnit.add(ValueWithUnit.TEType(therapyEvent.type))

        therapyEvent.enteredBy = enteredBy
        if (therapyEvent.type == TE.Type.QUESTION || therapyEvent.type == TE.Type.ANNOUNCEMENT) {
            val glucoseStatus = glucoseStatusProvider.glucoseStatusData
            if (glucoseStatus != null) {
                therapyEvent.glucose = glucoseStatus.glucose
                therapyEvent.glucoseType = TE.MeterType.SENSOR
                valuesWithUnit.add(ValueWithUnit.Mgdl(glucoseStatus.glucose))
                valuesWithUnit.add(ValueWithUnit.TEMeterType(TE.MeterType.SENSOR))
            }
        } else {
            therapyEvent.duration = T.mins(duration.value.toLong()).msecs()
            valuesWithUnit.addAll(listOfNotNull(ValueWithUnit.Minute(duration.value).takeIf { duration.value != 0 }))
        }
        therapyEvent.note = note.value
        valuesWithUnit.addAll(listOfNotNull(ValueWithUnit.SimpleString(note.value).takeIf { note.value.isNotBlank() }))
        disposable += persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
            therapyEvent = therapyEvent,
            action = app.aaps.core.data.ue.Action.CAREPORTAL,
            source = Sources.Automation,
            note = title,
            listValues = valuesWithUnit
        ).subscribe()
    }

    override fun toJSON(): String {
        val data = JSONObject()
            .put("cpEvent", cpEvent.value)
            .put("note", note.value)
            .put("durationInMinutes", duration.value)
        return JSONObject()
            .put("type", this.javaClass.simpleName)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Action {
        val o = JSONObject(data)
        cpEvent.value = InputCarePortalMenu.EventType.valueOf(JsonHelper.safeGetString(o, "cpEvent")!!)
        note.value = JsonHelper.safeGetString(o, "note", "")
        duration.value = JsonHelper.safeGetInt(o, "durationInMinutes")
        return this
    }

    override fun hasDialog(): Boolean = true

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(cpEvent)
            .add(LabelWithElement(rh, rh.gs(app.aaps.core.ui.R.string.duration_min_label), "", duration))
            .add(LabelWithElement(rh, rh.gs(app.aaps.core.ui.R.string.notes_label), "", note))
            .build(root)
    }

    override fun isValid(): Boolean = true
}

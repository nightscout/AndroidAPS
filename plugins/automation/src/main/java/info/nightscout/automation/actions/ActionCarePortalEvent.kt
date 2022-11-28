package info.nightscout.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import dagger.android.HasAndroidInjector
import info.nightscout.core.extensions.fromConstant
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.core.iob.iobCobCalculator.GlucoseStatusProvider
import info.nightscout.automation.R
import info.nightscout.automation.elements.InputCarePortalMenu
import info.nightscout.automation.elements.InputDuration
import info.nightscout.automation.elements.InputString
import info.nightscout.automation.elements.LabelWithElement
import info.nightscout.automation.elements.LayoutBuilder
import info.nightscout.database.entities.TherapyEvent
import info.nightscout.database.entities.UserEntry
import info.nightscout.database.entities.ValueWithUnit
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.InsertIfNewByTimestampTherapyEventTransaction
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.utils.JsonHelper
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.json.JSONObject
import javax.inject.Inject

class ActionCarePortalEvent(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var repository: AppRepository
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var sp: SP
    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Inject lateinit var uel: UserEntryLogger

    private val disposable = CompositeDisposable()

    var note = InputString()
    var duration = InputDuration(0, InputDuration.TimeUnit.MINUTES)
    var cpEvent = InputCarePortalMenu(rh)
    private var valuesWithUnit = mutableListOf<ValueWithUnit?>()

    private constructor(injector: HasAndroidInjector, actionCPEvent: ActionCarePortalEvent) : this(injector) {
        cpEvent = InputCarePortalMenu(rh, actionCPEvent.cpEvent.value)
    }

    override fun friendlyName(): Int = R.string.careportal
    override fun shortDescription(): String = rh.gs(cpEvent.value.stringResWithValue, note.value)

    @DrawableRes override fun icon(): Int = cpEvent.value.drawableRes

    override fun doAction(callback: Callback) {
        val enteredBy = sp.getString("careportal_enteredby", "AAPS")
        val eventTime = dateUtil.now()
        val therapyEvent = TherapyEvent(
            timestamp = eventTime,
            type = cpEvent.value.therapyEventType,
            glucoseUnit = TherapyEvent.GlucoseUnit.fromConstant(profileFunction.getUnits())
        )
        valuesWithUnit.add(ValueWithUnit.TherapyEventType(therapyEvent.type))

        therapyEvent.enteredBy = enteredBy
        if (therapyEvent.type == TherapyEvent.Type.QUESTION || therapyEvent.type == TherapyEvent.Type.ANNOUNCEMENT) {
            val glucoseStatus = glucoseStatusProvider.glucoseStatusData
            if (glucoseStatus != null) {
                therapyEvent.glucose = glucoseStatus.glucose
                therapyEvent.glucoseType = TherapyEvent.MeterType.SENSOR
                valuesWithUnit.add(ValueWithUnit.Mgdl(glucoseStatus.glucose))
                valuesWithUnit.add(ValueWithUnit.TherapyEventMeterType(TherapyEvent.MeterType.SENSOR))
            }
        } else {
            therapyEvent.duration = T.mins(duration.value.toLong()).msecs()
            valuesWithUnit.add(ValueWithUnit.Minute(duration.value).takeIf { !duration.value.equals(0) })
        }
        therapyEvent.note = note.value
        valuesWithUnit.add(ValueWithUnit.SimpleString(note.value).takeIf { note.value.isNotBlank() })
        disposable += repository.runTransactionForResult(InsertIfNewByTimestampTherapyEventTransaction(therapyEvent))
            .subscribe(
                { result -> result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted therapy event $it") } },
                { aapsLogger.error(LTag.DATABASE, "Error while saving therapy event", it) }
            )
        uel.log(UserEntry.Action.CAREPORTAL, UserEntry.Sources.Automation, title, valuesWithUnit)
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
            .add(LabelWithElement(rh, rh.gs(R.string.duration_min_label), "", duration))
            .add(LabelWithElement(rh, rh.gs(R.string.notes_label), "", note))
            .build(root)
    }

    override fun isValid(): Boolean = true
}

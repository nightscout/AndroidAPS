package app.aaps.plugins.automation.actions

import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.icons.IcActivity
import app.aaps.core.ui.compose.icons.IcAnnouncement
import app.aaps.core.ui.compose.icons.IcNote
import app.aaps.core.ui.compose.icons.IcQuestion
import app.aaps.core.utils.lenientInt
import app.aaps.core.utils.lenientString
import app.aaps.core.utils.lenientStringOrNull
import app.aaps.plugins.automation.elements.InputCarePortalMenu
import app.aaps.plugins.automation.elements.InputDuration
import app.aaps.plugins.automation.elements.InputString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Provider

class ActionCarePortalEvent(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val persistenceLayer: PersistenceLayer,
    private val profileFunction: ProfileFunction,
    private val dateUtil: DateUtil,
    private val glucoseStatusProvider: GlucoseStatusProvider
) : Action(aapsLogger, rh, pumpEnactResultProvider) {


    var note = InputString()
    var duration = InputDuration(0, InputDuration.TimeUnit.MINUTES)
    var cpEvent = InputCarePortalMenu()
    private var valuesWithUnit = mutableListOf<ValueWithUnit>()

    override fun friendlyName(): Int = app.aaps.core.ui.R.string.careportal
    override fun shortDescription(): String = rh.gs(cpEvent.value.stringResWithValue, note.value)

    override fun composeIcon() = when (cpEvent.value) {
        InputCarePortalMenu.EventType.NOTE         -> IcNote
        InputCarePortalMenu.EventType.EXERCISE     -> IcActivity
        InputCarePortalMenu.EventType.QUESTION     -> IcQuestion
        InputCarePortalMenu.EventType.ANNOUNCEMENT -> IcAnnouncement
    }

    override fun elementType() = when (cpEvent.value) {
        InputCarePortalMenu.EventType.NOTE         -> ElementType.NOTE
        InputCarePortalMenu.EventType.EXERCISE     -> ElementType.EXERCISE
        InputCarePortalMenu.EventType.QUESTION     -> ElementType.QUESTION
        InputCarePortalMenu.EventType.ANNOUNCEMENT -> ElementType.ANNOUNCEMENT
    }

    override suspend fun doAction(): PumpEnactResult {
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
        persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
            therapyEvent = therapyEvent,
            action = app.aaps.core.data.ue.Action.CAREPORTAL,
            source = Sources.Automation,
            note = title,
            listValues = valuesWithUnit
        )
        return pumpEnactResultProvider.get().success(true).comment(app.aaps.core.ui.R.string.ok)
    }

    override fun toJSON(): String =
        buildJsonObject {
            put("type", this@ActionCarePortalEvent.javaClass.simpleName)
            put(
                "data", buildJsonObject {
                    put("cpEvent", cpEvent.value.toString())
                    put("note", note.value)
                    put("durationInMinutes", duration.value)
                }
            )
        }.toString()

    override fun fromJSON(data: String): Action {
        val o = jsonOf(data)
        cpEvent.value = InputCarePortalMenu.EventType.valueOf(o.lenientStringOrNull("cpEvent")!!)
        note.value = o.lenientString("note", "")
        duration.value = o.lenientInt("durationInMinutes")
        return this
    }

    override fun hasDialog(): Boolean = true

    override fun isValid(): Boolean = true
}

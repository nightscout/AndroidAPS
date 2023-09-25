package info.nightscout.plugins.sync.nsclient.extensions

import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.T
import app.aaps.core.utils.JsonHelper
import app.aaps.database.entities.TherapyEvent
import info.nightscout.plugins.sync.nsclient.data.NSMbg
import org.json.JSONObject

fun TherapyEvent.GlucoseUnit.toMainUnit(): GlucoseUnit =
    if (this == TherapyEvent.GlucoseUnit.MGDL) GlucoseUnit.MGDL
    else GlucoseUnit.MMOL

fun therapyEventFromNsMbg(mbg: NSMbg) =
    TherapyEvent(
        type = TherapyEvent.Type.FINGER_STICK_BG_VALUE, //convert Mbg to finger stick because is coming from "entries" collection
        timestamp = mbg.date,
        glucose = mbg.mbg,
        glucoseUnit = TherapyEvent.GlucoseUnit.MGDL
    )

fun TherapyEvent.Companion.fromJson(jsonObject: JSONObject): TherapyEvent? {
    val glucoseUnit = if (JsonHelper.safeGetString(jsonObject, "units", GlucoseUnit.MGDL.asText) == GlucoseUnit.MGDL.asText) TherapyEvent.GlucoseUnit.MGDL else TherapyEvent.GlucoseUnit.MMOL
    val timestamp =
        JsonHelper.safeGetLongAllowNull(jsonObject, "mills", null)
            ?: JsonHelper.safeGetLongAllowNull(jsonObject, "date", null)
            ?: return null
    val type = TherapyEvent.Type.fromString(JsonHelper.safeGetString(jsonObject, "eventType", TherapyEvent.Type.NONE.text))
    val duration = JsonHelper.safeGetLong(jsonObject, "duration")
    val durationInMilliseconds = JsonHelper.safeGetLongAllowNull(jsonObject, "durationInMilliseconds")
    val glucose = JsonHelper.safeGetDoubleAllowNull(jsonObject, "glucose")
    val glucoseType = TherapyEvent.MeterType.fromString(JsonHelper.safeGetString(jsonObject, "glucoseType"))
    val enteredBy = JsonHelper.safeGetStringAllowNull(jsonObject, "enteredBy", null)
    val note = JsonHelper.safeGetStringAllowNull(jsonObject, "notes", null)
    val id = JsonHelper.safeGetStringAllowNull(jsonObject, "identifier", null)
        ?: JsonHelper.safeGetStringAllowNull(jsonObject, "_id", null)
        ?: return null
    val isValid = JsonHelper.safeGetBoolean(jsonObject, "isValid", true)

    if (timestamp == 0L) return null

    val te = TherapyEvent(
        timestamp = timestamp,
        duration = durationInMilliseconds ?: T.mins(duration).msecs(),
        glucoseUnit = glucoseUnit,
        type = type,
        glucose = glucose,
        glucoseType = glucoseType,
        enteredBy = enteredBy,
        note = note,
        isValid = isValid
    )
    te.interfaceIDs.nightscoutId = id
    return te
}

fun TherapyEvent.toJson(isAdd: Boolean, dateUtil: DateUtil): JSONObject =
    JSONObject()
        .put("eventType", type.text)
        .put("isValid", isValid)
        .put("created_at", dateUtil.toISOString(timestamp))
        .put("enteredBy", enteredBy)
        .put("units", if (glucoseUnit == TherapyEvent.GlucoseUnit.MGDL) GlucoseUnit.MGDL.asText else GlucoseUnit.MMOL.asText)
        .also {
            if (duration != 0L) it.put("duration", T.msecs(duration).mins())
            if (duration != 0L) it.put("durationInMilliseconds", duration)
            if (note != null) it.put("notes", note)
            if (glucose != null) it.put("glucose", glucose)
            if (glucoseType != null) it.put("glucoseType", glucoseType!!.text)
            if (isAdd && interfaceIDs.nightscoutId != null) it.put("_id", interfaceIDs.nightscoutId)
            if (type == TherapyEvent.Type.ANNOUNCEMENT) it.put("isAnnouncement", true)
        }


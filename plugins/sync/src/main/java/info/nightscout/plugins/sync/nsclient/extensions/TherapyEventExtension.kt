package info.nightscout.plugins.sync.nsclient.extensions

import info.nightscout.database.entities.TherapyEvent
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.utils.JsonHelper
import info.nightscout.plugins.sync.nsclient.data.NSMbg
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
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
fun therapyEventFromJson(jsonObject: JSONObject): TherapyEvent? {
    val glucoseUnit = if (JsonHelper.safeGetString(jsonObject, "units", Constants.MGDL) == Constants.MGDL) TherapyEvent.GlucoseUnit.MGDL else TherapyEvent.GlucoseUnit.MMOL
    val timestamp = JsonHelper.safeGetLongAllowNull(jsonObject, "mills", null) ?: return null
    val type = TherapyEvent.Type.fromString(JsonHelper.safeGetString(jsonObject, "eventType", TherapyEvent.Type.NONE.text))
    val duration = JsonHelper.safeGetLong(jsonObject, "duration")
    val durationInMilliseconds = JsonHelper.safeGetLongAllowNull(jsonObject, "durationInMilliseconds")
    val glucose = JsonHelper.safeGetDoubleAllowNull(jsonObject, "glucose")
    val glucoseType = TherapyEvent.MeterType.fromString(JsonHelper.safeGetString(jsonObject, "glucoseType"))
    val enteredBy = JsonHelper.safeGetStringAllowNull(jsonObject, "enteredBy", null)
    val note = JsonHelper.safeGetStringAllowNull(jsonObject, "notes", null)
    val id = JsonHelper.safeGetStringAllowNull(jsonObject, "_id", null) ?: return null
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
        .put("units", if (glucoseUnit == TherapyEvent.GlucoseUnit.MGDL) Constants.MGDL else Constants.MMOL)
        .also {
            if (duration != 0L) it.put("duration", T.msecs(duration).mins())
            if (duration != 0L) it.put("durationInMilliseconds", duration)
            if (note != null) it.put("notes", note)
            if (glucose != null) it.put("glucose", glucose)
            if (glucoseType != null) it.put("glucoseType", glucoseType!!.text)
            if (isAdd && interfaceIDs.nightscoutId != null) it.put("_id", interfaceIDs.nightscoutId)
            if (type == TherapyEvent.Type.ANNOUNCEMENT) it.put("isAnnouncement", true)
        }


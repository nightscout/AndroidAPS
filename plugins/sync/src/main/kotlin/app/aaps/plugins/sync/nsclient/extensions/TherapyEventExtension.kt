package app.aaps.plugins.sync.nsclient.extensions

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.sync.nsclient.data.NSMbg
import org.json.JSONObject

fun therapyEventFromNsMbg(mbg: NSMbg) =
    TE(
        type = TE.Type.FINGER_STICK_BG_VALUE, //convert Mbg to finger stick because is coming from "entries" collection
        timestamp = mbg.date,
        glucose = mbg.mbg,
        glucoseUnit = GlucoseUnit.MGDL
    )

fun TE.Companion.fromJson(jsonObject: JSONObject): TE? {
    val glucoseUnit = if (JsonHelper.safeGetString(jsonObject, "units", GlucoseUnit.MGDL.asText) == GlucoseUnit.MGDL.asText) GlucoseUnit.MGDL else GlucoseUnit.MMOL
    val timestamp =
        JsonHelper.safeGetLongAllowNull(jsonObject, "mills", null)
            ?: JsonHelper.safeGetLongAllowNull(jsonObject, "date", null)
            ?: return null
    val type = TE.Type.fromString(JsonHelper.safeGetString(jsonObject, "eventType", TE.Type.NONE.text))
    val duration = JsonHelper.safeGetLong(jsonObject, "duration")
    val durationInMilliseconds = JsonHelper.safeGetLongAllowNull(jsonObject, "durationInMilliseconds")
    val glucose = JsonHelper.safeGetDoubleAllowNull(jsonObject, "glucose")
    val glucoseType = TE.MeterType.fromString(JsonHelper.safeGetString(jsonObject, "glucoseType"))
    val location = TE.Location.fromString(JsonHelper.safeGetString(jsonObject, "location"))
    val arrow = TE.Arrow.fromString(JsonHelper.safeGetString(jsonObject, "arrow"))
    val enteredBy = JsonHelper.safeGetStringAllowNull(jsonObject, "enteredBy", null)
    val note = JsonHelper.safeGetStringAllowNull(jsonObject, "notes", null)
    val id = JsonHelper.safeGetStringAllowNull(jsonObject, "identifier", null)
        ?: JsonHelper.safeGetStringAllowNull(jsonObject, "_id", null)
        ?: return null
    val isValid = JsonHelper.safeGetBoolean(jsonObject, "isValid", true)

    if (timestamp == 0L) return null

    val te = TE(
        timestamp = timestamp,
        duration = durationInMilliseconds ?: T.mins(duration).msecs(),
        glucoseUnit = glucoseUnit,
        type = type,
        glucose = glucose,
        glucoseType = glucoseType,
        enteredBy = enteredBy,
        note = note,
        location = location,
        arrow = arrow,
        isValid = isValid
    )
    te.ids.nightscoutId = id
    return te
}

fun TE.toJson(isAdd: Boolean, dateUtil: DateUtil): JSONObject =
    JSONObject()
        .put("eventType", type.text)
        .put("isValid", isValid)
        .put("created_at", dateUtil.toISOString(timestamp))
        .put("enteredBy", enteredBy)
        .put("units", if (glucoseUnit == GlucoseUnit.MGDL) GlucoseUnit.MGDL.asText else GlucoseUnit.MMOL.asText)
        .also {
            if (duration != 0L) it.put("duration", T.msecs(duration).mins())
            if (duration != 0L) it.put("durationInMilliseconds", duration)
            if (note != null) it.put("notes", note)
            if (glucose != null) it.put("glucose", glucose)
            if (glucoseType != null) it.put("glucoseType", glucoseType!!.text)
            if (isAdd && ids.nightscoutId != null) it.put("_id", ids.nightscoutId)
            if (type == TE.Type.ANNOUNCEMENT) it.put("isAnnouncement", true)
            if (location != null) it.put("location", location?.text)
            if (arrow != null) it.put("arrow", arrow?.text)
        }
package info.nightscout.androidaps.extensions

import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.T
import org.json.JSONObject
import java.util.concurrent.TimeUnit

fun TherapyEvent.age(useShortText: Boolean, rh: ResourceHelper, dateUtil: DateUtil): String {
    val diff = dateUtil.computeDiff(timestamp, System.currentTimeMillis())
    var days = " " + rh.gs(R.string.days) + " "
    var hours = " " + rh.gs(R.string.hours) + " "
    if (useShortText) {
        days = rh.gs(R.string.shortday)
        hours = rh.gs(R.string.shorthour)
    }
    return diff[TimeUnit.DAYS].toString() + days + diff[TimeUnit.HOURS] + hours
}

fun TherapyEvent.isOlderThan(hours: Double): Boolean {
    return getHoursFromStart() > hours
}

fun TherapyEvent.getHoursFromStart(): Double {
    return (System.currentTimeMillis() - timestamp) / (60 * 60 * 1000.0)
}

fun TherapyEvent.GlucoseUnit.toMainUnit(): GlucoseUnit =
    if (this == TherapyEvent.GlucoseUnit.MGDL) GlucoseUnit.MGDL
    else GlucoseUnit.MMOL

fun TherapyEvent.GlucoseUnit.Companion.fromConstant(units: GlucoseUnit): TherapyEvent.GlucoseUnit =
    if (units == GlucoseUnit.MGDL) TherapyEvent.GlucoseUnit.MGDL
    else TherapyEvent.GlucoseUnit.MMOL

/*
        create fake object with nsID and isValid == false
 */

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

fun List<TherapyEvent>.isTherapyEventEvent5minBack(time: Long): Boolean {
    for (event in this) {
        if (event.timestamp <= time && event.timestamp > time - T.mins(5).msecs()) {
            return true
        }
    }
    return false
}

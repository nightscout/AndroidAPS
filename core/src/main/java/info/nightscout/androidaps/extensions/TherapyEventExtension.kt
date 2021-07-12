package info.nightscout.androidaps.extensions

import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.androidaps.plugins.general.nsclient.data.NSMbg
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.json.JSONObject
import java.util.concurrent.TimeUnit

fun TherapyEvent.age(useShortText: Boolean, resourceHelper: ResourceHelper, dateUtil: DateUtil): String {
    val diff = dateUtil.computeDiff(timestamp, System.currentTimeMillis())
    var days = " " + resourceHelper.gs(R.string.days) + " "
    var hours = " " + resourceHelper.gs(R.string.hours) + " "
    if (useShortText) {
        days = resourceHelper.gs(R.string.shortday)
        hours = resourceHelper.gs(R.string.shorthour)
    }
    return diff[TimeUnit.DAYS].toString() + days + diff[TimeUnit.HOURS] + hours
}

fun TherapyEvent.isOlderThan(hours: Double): Boolean {
    return getHoursFromStart() > hours
}

fun TherapyEvent.getHoursFromStart(): Double {
    return (System.currentTimeMillis() - timestamp) / (60 * 60 * 1000.0)
}

fun TherapyEvent.GlucoseUnit.toConstant(): String =
    if (this == TherapyEvent.GlucoseUnit.MGDL) Constants.MGDL
    else Constants.MMOL

fun TherapyEvent.GlucoseUnit.toMainUnit(): GlucoseUnit =
    if (this == TherapyEvent.GlucoseUnit.MGDL) GlucoseUnit.MGDL
    else GlucoseUnit.MMOL

fun TherapyEvent.GlucoseUnit.Companion.fromConstant(units: String): TherapyEvent.GlucoseUnit =
    if (units == Constants.MGDL) TherapyEvent.GlucoseUnit.MGDL
    else TherapyEvent.GlucoseUnit.MMOL

fun TherapyEvent.GlucoseUnit.Companion.fromConstant(units: GlucoseUnit): TherapyEvent.GlucoseUnit =
    if (units == GlucoseUnit.MGDL) TherapyEvent.GlucoseUnit.MGDL
    else TherapyEvent.GlucoseUnit.MMOL

fun therapyEventFromNsMbg(mbg: NSMbg) =
    TherapyEvent(
        type = TherapyEvent.Type.NS_MBG,
        timestamp = mbg.date,
        glucose = mbg.mbg,
        glucoseUnit = TherapyEvent.GlucoseUnit.MGDL
    ).also {
        it.interfaceIDs.nightscoutId = mbg.id()
    }

/*
        create fake object with nsID and isValid == false
 */
fun therapyEventFromNsIdForInvalidating(nsId: String): TherapyEvent =
    therapyEventFromJson(
        JSONObject()
            .put("mills", 1)
            .put("_id", nsId)
            .put("isValid", false)
    )!!

fun therapyEventFromJson(jsonObject: JSONObject): TherapyEvent? {
    val glucoseUnit = if (JsonHelper.safeGetString(jsonObject, "units", Constants.MGDL) == Constants.MGDL) TherapyEvent.GlucoseUnit.MGDL else TherapyEvent.GlucoseUnit.MMOL
    val timestamp = JsonHelper.safeGetLongAllowNull(jsonObject, "mills", null) ?: return null
    val type = TherapyEvent.Type.fromString(JsonHelper.safeGetString(jsonObject, "eventType", TherapyEvent.Type.NONE.text))
    val duration = JsonHelper.safeGetLong(jsonObject, "duration")
    val glucose = JsonHelper.safeGetDoubleAllowNull(jsonObject, "glucose")
    val glucoseType = TherapyEvent.MeterType.fromString(JsonHelper.safeGetString(jsonObject, "glucoseType"))
    val enteredBy = JsonHelper.safeGetStringAllowNull(jsonObject, "enteredBy", null)
    val note = JsonHelper.safeGetStringAllowNull(jsonObject, "notes", null)
    val id = JsonHelper.safeGetStringAllowNull(jsonObject, "_id", null) ?: return null
    val isValid = JsonHelper.safeGetBoolean(jsonObject, "isValid", true)

    if (timestamp == 0L) return null

    val te = TherapyEvent(
        timestamp = timestamp,
        duration = TimeUnit.MINUTES.toMillis(duration),
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



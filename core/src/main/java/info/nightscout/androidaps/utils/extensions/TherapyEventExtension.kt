package info.nightscout.androidaps.utils.extensions

import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.general.nsclient.data.NSMbg
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.json.JSONObject
import java.util.concurrent.TimeUnit

fun TherapyEvent.age(useShortText: Boolean, resourceHelper: ResourceHelper): String {
    val diff = DateUtil.computeDiff(timestamp, System.currentTimeMillis())
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

fun TherapyEvent.GlucoseUnit.Companion.fromConstant(units: String): TherapyEvent.GlucoseUnit =
    if (units == Constants.MGDL) TherapyEvent.GlucoseUnit.MGDL
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
            .put(NSUpload.ISVALID, false)
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
    val isValid = JsonHelper.safeGetBoolean(jsonObject, NSUpload.ISVALID, true)

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

fun isEvent5minBack(list: List<TherapyEvent>, time: Long): Boolean {
    for (i in list.indices) {
        val event = list[i]
        if (event.timestamp <= time && event.timestamp > time - T.mins(5).msecs()) {
            return true
        }
    }
    return false
}



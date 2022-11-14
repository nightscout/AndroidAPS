package info.nightscout.plugins.sync.nsclient.extensions

import info.nightscout.database.entities.Carbs
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.interfaces.utils.JsonHelper
import info.nightscout.shared.utils.DateUtil
import org.json.JSONObject

fun Carbs.toJson(isAdd: Boolean, dateUtil: DateUtil): JSONObject =
    JSONObject()
        .put("eventType", if (amount < 12) info.nightscout.database.entities.TherapyEvent.Type.CARBS_CORRECTION.text else info.nightscout.database.entities.TherapyEvent.Type.MEAL_BOLUS.text)
        .put("carbs", amount)
        .put("notes", notes)
        .put("created_at", dateUtil.toISOString(timestamp))
        .put("isValid", isValid)
        .put("date", timestamp).also {
            if (duration != 0L) it.put("duration", duration)
            if (interfaceIDs.pumpId != null) it.put("pumpId", interfaceIDs.pumpId)
            if (interfaceIDs.pumpType != null) it.put("pumpType", interfaceIDs.pumpType!!.name)
            if (interfaceIDs.pumpSerial != null) it.put("pumpSerial", interfaceIDs.pumpSerial)
            if (isAdd && interfaceIDs.nightscoutId != null) it.put("_id", interfaceIDs.nightscoutId)
        }

fun carbsFromJson(jsonObject: JSONObject): Carbs? {
    val timestamp = JsonHelper.safeGetLongAllowNull(jsonObject, "mills", null) ?: return null
    val duration = JsonHelper.safeGetLong(jsonObject, "duration")
    val amount = JsonHelper.safeGetDoubleAllowNull(jsonObject, "carbs") ?: return null
    val notes = JsonHelper.safeGetStringAllowNull(jsonObject, "notes", null)
    val isValid = JsonHelper.safeGetBoolean(jsonObject, "isValid", true)
    val id = JsonHelper.safeGetStringAllowNull(jsonObject, "_id", null) ?: return null
    val pumpId = JsonHelper.safeGetLongAllowNull(jsonObject, "pumpId", null)
    val pumpType = InterfaceIDs.PumpType.fromString(JsonHelper.safeGetStringAllowNull(jsonObject, "pumpType", null))
    val pumpSerial = JsonHelper.safeGetStringAllowNull(jsonObject, "pumpSerial", null)

    if (timestamp == 0L) return null
    if (amount == 0.0) return null

    return Carbs(
        timestamp = timestamp,
        duration = duration,
        amount = amount,
        notes = notes,
        isValid = isValid
    ).also {
        it.interfaceIDs.nightscoutId = id
        it.interfaceIDs.pumpId = pumpId
        it.interfaceIDs.pumpType = pumpType
        it.interfaceIDs.pumpSerial = pumpSerial
    }
}


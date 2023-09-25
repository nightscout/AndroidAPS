package info.nightscout.plugins.sync.nsclient.extensions

import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.utils.JsonHelper
import app.aaps.database.entities.Carbs
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.embedments.InterfaceIDs
import org.json.JSONObject

fun Carbs.toJson(isAdd: Boolean, dateUtil: DateUtil): JSONObject =
    JSONObject()
        .put("eventType", if (amount < 12) TherapyEvent.Type.CARBS_CORRECTION.text else TherapyEvent.Type.MEAL_BOLUS.text)
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

fun Carbs.Companion.fromJson(jsonObject: JSONObject): Carbs? {
    val timestamp =
        JsonHelper.safeGetLongAllowNull(jsonObject, "mills", null)
            ?: JsonHelper.safeGetLongAllowNull(jsonObject, "date", null)
            ?: return null
    val duration = JsonHelper.safeGetLong(jsonObject, "duration")
    val amount = JsonHelper.safeGetDoubleAllowNull(jsonObject, "carbs") ?: return null
    val notes = JsonHelper.safeGetStringAllowNull(jsonObject, "notes", null)
    val isValid = JsonHelper.safeGetBoolean(jsonObject, "isValid", true)
    val id = JsonHelper.safeGetStringAllowNull(jsonObject, "identifier", null)
        ?: JsonHelper.safeGetStringAllowNull(jsonObject, "_id", null)
        ?: return null
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


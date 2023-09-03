package info.nightscout.plugins.sync.nsclient.extensions

import info.nightscout.database.entities.Bolus
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.interfaces.utils.JsonHelper
import info.nightscout.shared.utils.DateUtil
import org.json.JSONObject

fun Bolus.toJson(isAdd: Boolean, dateUtil: DateUtil): JSONObject =
    JSONObject()
        .put(
            "eventType",
            if (type == Bolus.Type.SMB) info.nightscout.database.entities.TherapyEvent.Type.CORRECTION_BOLUS.text else info.nightscout.database.entities.TherapyEvent.Type.MEAL_BOLUS.text
        )
        .put("insulin", amount)
        .put("created_at", dateUtil.toISOString(timestamp))
        .put("date", timestamp)
        .put("type", type.name)
        .put("notes", notes)
        .put("isValid", isValid)
        .put("isSMB", type == Bolus.Type.SMB).also {
            if (interfaceIDs.pumpId != null) it.put("pumpId", interfaceIDs.pumpId)
            if (interfaceIDs.pumpType != null) it.put("pumpType", interfaceIDs.pumpType!!.name)
            if (interfaceIDs.pumpSerial != null) it.put("pumpSerial", interfaceIDs.pumpSerial)
            if (isAdd && interfaceIDs.nightscoutId != null) it.put("_id", interfaceIDs.nightscoutId)
        }

fun Bolus.Companion.fromJson(jsonObject: JSONObject): Bolus? {
    val timestamp = JsonHelper.safeGetLongAllowNull(jsonObject, "mills", null) ?: return null
    val amount = JsonHelper.safeGetDoubleAllowNull(jsonObject, "insulin") ?: return null
    val type = Bolus.Type.fromString(JsonHelper.safeGetString(jsonObject, "type"))
    val isValid = JsonHelper.safeGetBoolean(jsonObject, "isValid", true)
    val notes = JsonHelper.safeGetStringAllowNull(jsonObject, "notes", null)
    val id = JsonHelper.safeGetStringAllowNull(jsonObject, "_id", null) ?: return null
    val pumpId = JsonHelper.safeGetLongAllowNull(jsonObject, "pumpId", null)
    val pumpType = InterfaceIDs.PumpType.fromString(JsonHelper.safeGetStringAllowNull(jsonObject, "pumpType", null))
    val pumpSerial = JsonHelper.safeGetStringAllowNull(jsonObject, "pumpSerial", null)

    if (timestamp == 0L) return null
    if (amount == 0.0) return null

    return Bolus(
        timestamp = timestamp,
        amount = amount,
        type = type,
        notes = notes,
        isValid = isValid,
    ).also {
        it.interfaceIDs.nightscoutId = id
        it.interfaceIDs.pumpId = pumpId
        it.interfaceIDs.pumpType = pumpType
        it.interfaceIDs.pumpSerial = pumpSerial
    }
}
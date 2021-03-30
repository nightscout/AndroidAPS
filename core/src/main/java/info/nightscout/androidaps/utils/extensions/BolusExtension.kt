package info.nightscout.androidaps.utils.extensions

import info.nightscout.androidaps.data.Iob
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.InsulinInterface
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.JsonHelper
import org.json.JSONObject

fun Bolus.iobCalc(activePlugin: ActivePluginProvider, time: Long, dia: Double): Iob {
    if (!isValid  || type == Bolus.Type.PRIMING ) return Iob()
    val insulinInterface: InsulinInterface = activePlugin.activeInsulin
    return insulinInterface.iobCalcForTreatment(this, time, dia)
}

fun Bolus.toJson(): JSONObject =
    JSONObject()
        .put("eventType", if (type == Bolus.Type.SMB) TherapyEvent.Type.CORRECTION_BOLUS else TherapyEvent.Type.MEAL_BOLUS)
        .put("insulin", amount)
        .put("created_at", DateUtil.toISOString(timestamp))
        .put("date", timestamp)
        .put("type", type.name)
        .put("isValid", isValid)
        .put("isSMB", type == Bolus.Type.SMB).also {
            if (interfaceIDs.pumpId != null) it.put("pumpId", interfaceIDs.pumpId)
            if (interfaceIDs.pumpType != null) it.put("pumpType", interfaceIDs.pumpType!!.name)
            if (interfaceIDs.pumpSerial != null) it.put("pumpSerial", interfaceIDs.pumpSerial)
            if (interfaceIDs.nightscoutId != null) it.put("_id", interfaceIDs.nightscoutId)
        }

/*
        create fake object with nsID and isValid == false
 */
fun bolusFromNsIdForInvalidating(nsId: String): Bolus =
    bolusFromJson(
        JSONObject()
            .put("mills", 1)
            .put("insulin", -1.0)
            .put("_id", nsId)
            .put("isValid", false)
    )!!

fun bolusFromJson(jsonObject: JSONObject): Bolus? {
    val timestamp = JsonHelper.safeGetLongAllowNull(jsonObject, "mills", null) ?: return null
    val amount = JsonHelper.safeGetDoubleAllowNull(jsonObject, "insulin") ?: return null
    val type = Bolus.Type.fromString(JsonHelper.safeGetString(jsonObject, "type"))
    val isValid = JsonHelper.safeGetBoolean(jsonObject, "isValid", true)
    val id = JsonHelper.safeGetStringAllowNull(jsonObject, "_id", null) ?: return null
    val pumpId = JsonHelper.safeGetLongAllowNull(jsonObject, "pumpId", null)
    val pumpType = InterfaceIDs.PumpType.fromString(JsonHelper.safeGetStringAllowNull(jsonObject, "pumpType", null))
    val pumpSerial = JsonHelper.safeGetStringAllowNull(jsonObject, "pumpSerial", null)

    return Bolus(
        timestamp = timestamp,
        amount = amount,
        type = type,
        isValid = isValid,
        isBasalInsulin = false
    ).also {
        it.interfaceIDs.nightscoutId = id
        it.interfaceIDs.pumpId = pumpId
        it.interfaceIDs.pumpType = pumpType
        it.interfaceIDs.pumpSerial = pumpSerial
    }
}


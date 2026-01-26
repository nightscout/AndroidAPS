package app.aaps.plugins.sync.nsclient.extensions

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.utils.JsonHelper
import org.json.JSONObject

fun BS.toJson(isAdd: Boolean, dateUtil: DateUtil): JSONObject =
    JSONObject()
        .put(
            "eventType",
            if (type == BS.Type.SMB) TE.Type.CORRECTION_BOLUS.text else TE.Type.MEAL_BOLUS.text
        )
        .put("insulin", amount)
        .put("created_at", dateUtil.toISOString(timestamp))
        .put("date", timestamp)
        .put("type", type.name)
        .put("notes", notes)
        .put("isValid", isValid)
        .put("isSMB", type == BS.Type.SMB).also {
            if (ids.pumpId != null) it.put("pumpId", ids.pumpId)
            if (ids.pumpType != null) it.put("pumpType", ids.pumpType!!.name)
            if (ids.pumpSerial != null) it.put("pumpSerial", ids.pumpSerial)
            if (isAdd && ids.nightscoutId != null) it.put("_id", ids.nightscoutId)
        }

fun BS.Companion.fromJson(jsonObject: JSONObject, insulinFallback: Insulin): BS? {
    val timestamp =
        JsonHelper.safeGetLongAllowNull(jsonObject, "mills", null)
            ?: JsonHelper.safeGetLongAllowNull(jsonObject, "date", null)
            ?: return null
    val amount = JsonHelper.safeGetDoubleAllowNull(jsonObject, "insulin") ?: return null
    val type = BS.Type.fromString(JsonHelper.safeGetString(jsonObject, "type"))
    val isValid = JsonHelper.safeGetBoolean(jsonObject, "isValid", true)
    val notes = JsonHelper.safeGetStringAllowNull(jsonObject, "notes", null)
    val id = JsonHelper.safeGetStringAllowNull(jsonObject, "identifier", null)
        ?: JsonHelper.safeGetStringAllowNull(jsonObject, "_id", null)
        ?: return null
    val pumpId = JsonHelper.safeGetLongAllowNull(jsonObject, "pumpId", null)
    val pumpType = PumpType.fromString(JsonHelper.safeGetStringAllowNull(jsonObject, "pumpType", null))
    val pumpSerial = JsonHelper.safeGetStringAllowNull(jsonObject, "pumpSerial", null)

    val insulinLabel = JsonHelper.safeGetStringAllowNull(jsonObject, "insulinLabel", null)
    val insulinEndTime = JsonHelper.safeGetLongAllowNull(jsonObject, "insulinEndTime")
    val insulinPeakTime = JsonHelper.safeGetLongAllowNull(jsonObject, "insulinPeakTime")
    val concentration = JsonHelper.safeGetDoubleAllowNull(jsonObject, "concentration")

    val iCfg =
        if (insulinLabel != null && insulinEndTime != null && insulinPeakTime != null && concentration != null) ICfg(insulinLabel, insulinEndTime, insulinPeakTime, concentration)
        else ICfg(
            insulinLabel = insulinFallback.friendlyName,
            insulinEndTime = (insulinFallback.dia * 60 * 60 * 1000).toLong(),
            insulinPeakTime = (insulinFallback.peak * 60 * 1000).toLong(),
            concentration = 1.0
        )

    if (timestamp == 0L) return null
    if (amount == 0.0) return null

    return BS(
        timestamp = timestamp,
        amount = amount,
        type = type,
        notes = notes,
        isValid = isValid,
        iCfg = iCfg
    ).also {
        it.ids.nightscoutId = id
        it.ids.pumpId = pumpId
        it.ids.pumpType = pumpType
        it.ids.pumpSerial = pumpSerial
    }
}
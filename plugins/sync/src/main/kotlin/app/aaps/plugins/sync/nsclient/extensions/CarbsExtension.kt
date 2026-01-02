package app.aaps.plugins.sync.nsclient.extensions

import app.aaps.core.data.model.CA
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.utils.JsonHelper
import org.json.JSONObject
import kotlin.math.min

fun CA.toJson(isAdd: Boolean, dateUtil: DateUtil): JSONObject =
    JSONObject()
        .put("eventType", if (amount < 12) TE.Type.CARBS_CORRECTION.text else TE.Type.MEAL_BOLUS.text)
        .put("carbs", amount)
        .put("notes", notes)
        .put("created_at", dateUtil.toISOString(timestamp))
        .put("isValid", isValid)
        .put("date", timestamp).also {
            if (duration != 0L) it.put("duration", duration)
            if (ids.pumpId != null) it.put("pumpId", ids.pumpId)
            if (ids.pumpType != null) it.put("pumpType", ids.pumpType!!.name)
            if (ids.pumpSerial != null) it.put("pumpSerial", ids.pumpSerial)
            if (isAdd && ids.nightscoutId != null) it.put("_id", ids.nightscoutId)
        }

fun CA.Companion.fromJson(jsonObject: JSONObject): CA? {
    val timestamp =
        JsonHelper.safeGetLongAllowNull(jsonObject, "mills", null)
            ?: JsonHelper.safeGetLongAllowNull(jsonObject, "date", null)
            ?: return null
    val duration = min(JsonHelper.safeGetLong(jsonObject, "duration"), T.hours(HardLimits.MAX_CARBS_DURATION_HOURS).msecs())
    val amount = JsonHelper.safeGetDoubleAllowNull(jsonObject, "carbs")?.let { min(it, HardLimits.MAX_CARBS.toDouble()) } ?: return null
    val notes = JsonHelper.safeGetStringAllowNull(jsonObject, "notes", null)
    val isValid = JsonHelper.safeGetBoolean(jsonObject, "isValid", true)
    val id = JsonHelper.safeGetStringAllowNull(jsonObject, "identifier", null)
        ?: JsonHelper.safeGetStringAllowNull(jsonObject, "_id", null)
        ?: return null
    val pumpId = JsonHelper.safeGetLongAllowNull(jsonObject, "pumpId", null)
    val pumpType = PumpType.fromString(JsonHelper.safeGetStringAllowNull(jsonObject, "pumpType", null))
    val pumpSerial = JsonHelper.safeGetStringAllowNull(jsonObject, "pumpSerial", null)

    if (timestamp == 0L) return null
    if (amount == 0.0) return null

    return CA(
        timestamp = timestamp,
        duration = duration,
        amount = amount,
        notes = notes,
        isValid = isValid
    ).also {
        it.ids.nightscoutId = id
        it.ids.pumpId = pumpId
        it.ids.pumpType = pumpType
        it.ids.pumpSerial = pumpSerial
    }
}


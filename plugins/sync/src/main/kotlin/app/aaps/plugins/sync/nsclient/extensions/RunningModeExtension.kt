package app.aaps.plugins.sync.nsclient.extensions

import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.utils.JsonHelper
import org.json.JSONObject

fun RM.toJson(isAdd: Boolean, dateUtil: DateUtil): JSONObject {
    val reportedDuration = when (mode) {
        RM.Mode.OPEN_LOOP,
        RM.Mode.CLOSED_LOOP,
        RM.Mode.CLOSED_LOOP_LGS   -> 0

        RM.Mode.DISABLED_LOOP,
        RM.Mode.SUPER_BOLUS,
        RM.Mode.DISCONNECTED_PUMP,
        RM.Mode.SUSPENDED_BY_PUMP,
        RM.Mode.SUSPENDED_BY_DST,
        RM.Mode.SUSPENDED_BY_USER -> duration

        RM.Mode.RESUME            -> error("Invalid mode")
    }
    return JSONObject()
        .put("created_at", dateUtil.toISOString(timestamp))
        .put("enteredBy", "openaps://" + "AAPS")
        .put("eventType", TE.Type.APS_OFFLINE.text)
        .put("isValid", isValid)
        .put("duration", T.msecs(reportedDuration).mins())
        .put("durationInMilliseconds", reportedDuration)
        .put("originalDuration", duration)
        .put("mode", mode.name)
        .also {
            if (ids.pumpId != null) it.put("pumpId", ids.pumpId)
            if (ids.pumpType != null) it.put("pumpType", ids.pumpType!!.name)
            if (ids.pumpSerial != null) it.put("pumpSerial", ids.pumpSerial)
            if (isAdd && ids.nightscoutId != null) it.put("_id", ids.nightscoutId)
        }
}
/* NS PS
{
    "enteredBy": "undefined",
    "eventType": "OpenAPS Offline",
    "duration": 60,
    "created_at": "2021-05-27T15:11:52.230Z",
    "utcOffset": 0,
    "_id": "60afb6ba3c0d77e3e720f2fe",
    "mills": 1622128312230,
    "carbs": null,
    "insulin": null
}
 */
fun RM.Companion.fromJson(jsonObject: JSONObject): RM? {
    val timestamp =
        JsonHelper.safeGetLongAllowNull(jsonObject, "mills", null)
            ?: JsonHelper.safeGetLongAllowNull(jsonObject, "date", null)
            ?: return null
    val duration = JsonHelper.safeGetLong(jsonObject, "duration")
    val durationInMilliseconds = JsonHelper.safeGetLongAllowNull(jsonObject, "durationInMilliseconds")
    val originalDuration = JsonHelper.safeGetLongAllowNull(jsonObject, "originalDuration")
    val isValid = JsonHelper.safeGetBoolean(jsonObject, "isValid", true)
    val id = JsonHelper.safeGetStringAllowNull(jsonObject, "identifier", null)
        ?: JsonHelper.safeGetStringAllowNull(jsonObject, "_id", null)
        ?: return null
    val pumpId = JsonHelper.safeGetLongAllowNull(jsonObject, "pumpId", null)
    val pumpType = PumpType.fromString(JsonHelper.safeGetStringAllowNull(jsonObject, "pumpType", null))
    val pumpSerial = JsonHelper.safeGetStringAllowNull(jsonObject, "pumpSerial", null)
    val mode = RM.Mode.fromString(JsonHelper.safeGetString(jsonObject, "mode", DEFAULT_MODE.name))


    return RM(
        timestamp = timestamp,
        duration = originalDuration ?: durationInMilliseconds ?: T.mins(duration).msecs(),
        isValid = isValid,
        mode = mode
    ).also {
        it.ids.nightscoutId = id
        it.ids.pumpId = pumpId
        it.ids.pumpType = pumpType
        it.ids.pumpSerial = pumpSerial
    }
}

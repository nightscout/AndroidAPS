package app.aaps.plugins.sync.xdrip.extensions

import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.utils.DateUtil
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
        .put("autoForced", autoForced)
        .also {
            if (reasons != null) it.put("reasons", reasons)
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

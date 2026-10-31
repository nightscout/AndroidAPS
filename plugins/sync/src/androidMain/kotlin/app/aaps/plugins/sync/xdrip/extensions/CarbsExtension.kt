package app.aaps.plugins.sync.xdrip.extensions

import app.aaps.core.data.model.CA
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.utils.DateUtil
import org.json.JSONObject

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

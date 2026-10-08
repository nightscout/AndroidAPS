package app.aaps.plugins.sync.xdrip.extensions

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.utils.DateUtil
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

package app.aaps.plugins.sync.xdrip.extensions

import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.DateUtil
import com.google.gson.Gson
import org.json.JSONObject

fun BCR.toJson(isAdd: Boolean, dateUtil: DateUtil, profileUtil: ProfileUtil): JSONObject =
    JSONObject()
        .put("eventType", TE.Type.BOLUS_WIZARD.text)
        .put("created_at", dateUtil.toISOString(timestamp))
        .put("isValid", isValid)
        .put("bolusCalculatorResult", Gson().toJson(this))
        .put("date", timestamp)
        .put("glucose", profileUtil.fromMgdlToUnits(glucoseValue))
        .put("units", profileUtil.units.asText)
        .put("notes", note)
        .also { if (isAdd && ids.nightscoutId != null) it.put("_id", ids.nightscoutId) }

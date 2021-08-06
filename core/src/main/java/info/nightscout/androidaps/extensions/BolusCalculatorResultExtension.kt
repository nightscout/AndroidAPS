package info.nightscout.androidaps.extensions

import com.google.gson.Gson
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.database.entities.BolusCalculatorResult
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.utils.DateUtil
import org.json.JSONObject

fun BolusCalculatorResult.toJson(isAdd: Boolean, dateUtil: DateUtil): JSONObject =
    JSONObject()
        .put("eventType", TherapyEvent.Type.BOLUS_WIZARD.text)
        .put("created_at", dateUtil.toISOString(timestamp))
        .put("isValid", isValid)
        .put("bolusCalculatorResult", Gson().toJson(this))
        .put("date", timestamp)
        .put("glucose", glucoseValue)
        .put("units", Constants.MGDL)
        .put("notes", note)
        .also { if (isAdd && interfaceIDs.nightscoutId != null) it.put("_id", interfaceIDs.nightscoutId) }

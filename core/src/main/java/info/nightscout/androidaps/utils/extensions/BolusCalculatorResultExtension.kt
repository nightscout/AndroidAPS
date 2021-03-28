package info.nightscout.androidaps.utils.extensions

import com.google.gson.Gson
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.database.entities.BolusCalculatorResult
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.utils.DateUtil
import org.json.JSONObject

fun BolusCalculatorResult.toJson(): JSONObject =
    JSONObject()
        .put("eventType", TherapyEvent.Type.BOLUS_WIZARD)
        .put("created_at", DateUtil.toISOString(timestamp))
        .put("bolusCalculatorResult", Gson().toJson(this))
        .put("date", timestamp)
        .put("glucose", glucoseValue)
        .put("units", Constants.MGDL)
        .put("notes", note)
        .also {
            if (interfaceIDs.nightscoutId != null) it.put("_id", interfaceIDs.nightscoutId)
        }

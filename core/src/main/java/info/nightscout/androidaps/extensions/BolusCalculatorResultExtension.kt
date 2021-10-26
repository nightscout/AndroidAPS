package info.nightscout.androidaps.extensions

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.database.entities.BolusCalculatorResult
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.JsonHelper
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

fun bolusCalculatorResultFromJson(jsonObject: JSONObject): BolusCalculatorResult? {
    val timestamp = JsonHelper.safeGetLongAllowNull(jsonObject, "mills", null) ?: return null
    val isValid = JsonHelper.safeGetBoolean(jsonObject, "isValid", true)
    val id = JsonHelper.safeGetStringAllowNull(jsonObject, "_id", null) ?: return null
    val bcrString = JsonHelper.safeGetStringAllowNull(jsonObject, "bolusCalculatorResult", null) ?: return null

    if (timestamp == 0L) return null

    return try {
        Gson().fromJson(bcrString, BolusCalculatorResult::class.java)
            .also {
                it.id = 0
                it.isValid = isValid
                it.interfaceIDs.nightscoutId = id
            }
    } catch (e: JsonSyntaxException) {
        null
    }
}

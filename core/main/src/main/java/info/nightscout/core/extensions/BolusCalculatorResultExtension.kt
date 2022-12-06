package info.nightscout.core.extensions

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import info.nightscout.database.entities.BolusCalculatorResult
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.utils.JsonHelper
import info.nightscout.shared.utils.DateUtil
import org.json.JSONObject

fun BolusCalculatorResult.toJson(isAdd: Boolean, dateUtil: DateUtil, profileFunction: ProfileFunction): JSONObject =
    JSONObject()
        .put("eventType", info.nightscout.database.entities.TherapyEvent.Type.BOLUS_WIZARD.text)
        .put("created_at", dateUtil.toISOString(timestamp))
        .put("isValid", isValid)
        .put("bolusCalculatorResult", Gson().toJson(this))
        .put("date", timestamp)
        .put("glucose", Profile.fromMgdlToUnits(glucoseValue, profileFunction.getUnits()))
        .put("units", profileFunction.getUnits().asText)
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
                it.version = 0
            }
    } catch (e: JsonSyntaxException) {
        null
    }
}

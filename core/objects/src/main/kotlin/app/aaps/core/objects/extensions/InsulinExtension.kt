package app.aaps.core.objects.extensions

import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.insulin.InsulinType
import org.json.JSONObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.*

/** used to save configuration within InsulinPlugin */
fun ICfg.toJson(): JSONObject = JSONObject()
    .put("insulinLabel", insulinLabel)
    .put("insulinEndTime", insulinEndTime)
    .put("insulinPeakTime", insulinPeakTime)
    .put("concentration", concentration)
    .put("isInhaled", isInhaled)
    .put("insulinNickname", insulinNickname)

/** used to restore configuration within InsulinPlugin and insulin Editor */
fun ICfg.Companion.fromJson(json: JSONObject): ICfg = ICfg(
    insulinLabel = json.optString("insulinLabel", ""),
    insulinEndTime = json.optLong("insulinEndTime", 0),
    insulinPeakTime = json.optLong("insulinPeakTime", 0),
    concentration = json.optDouble("concentration", 1.0),
    // Absent for entries written before the field existed: reconstruct from the peak.
    isInhaled = if (json.has("isInhaled")) json.optBoolean("isInhaled", false)
    else InsulinType.isInhaledPeak(json.optLong("insulinPeakTime", 0))

) .also { it.insulinNickname = json.optString("insulinNickname", "") }

fun ICfg.toJsonObject(): JsonObject = buildJsonObject {
    put("insulinLabel", insulinLabel)
    put("insulinEndTime", insulinEndTime)
    put("insulinPeakTime", insulinPeakTime)
    put("concentration", JsonPrimitive(concentration))
    put("isInhaled", JsonPrimitive(isInhaled))
    put("insulinNickname", insulinNickname)
}

/** used to restore configuration within InsulinPlugin and insulin Editor */
fun ICfg.Companion.fromJsonObject(json: JsonObject): ICfg {
    val icfg = ICfg(
        insulinLabel = json["insulinLabel"]?.jsonPrimitive?.contentOrNull ?: "",
        insulinEndTime = json["insulinEndTime"]?.jsonPrimitive?.longOrNull ?: 0,
        insulinPeakTime = json["insulinPeakTime"]?.jsonPrimitive?.longOrNull ?: 0,
        concentration = json["concentration"]?.jsonPrimitive?.doubleOrNull ?: 1.0,
        // Absent for catalogue entries written before the field existed: reconstruct from the peak.
        isInhaled = json["isInhaled"]?.jsonPrimitive?.booleanOrNull
            ?: InsulinType.isInhaledPeak(json["insulinPeakTime"]?.jsonPrimitive?.longOrNull ?: 0)
    )

    icfg.insulinNickname = json["insulinNickname"]?.jsonPrimitive?.contentOrNull ?: ""

    return icfg
}
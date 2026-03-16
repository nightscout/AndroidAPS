package app.aaps.core.objects.extensions

import app.aaps.core.data.model.ICfg
import org.json.JSONObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.*

/** used to save configuration within InsulinPlugin */
fun ICfg.toJson(): JSONObject = JSONObject()
    .put("insulinLabel", insulinLabel)
    .put("insulinEndTime", insulinEndTime)
    .put("insulinPeakTime", insulinPeakTime)
    .put("concentration", concentration)
    .put("insulinTemplate", insulinTemplate)
    .put("isNew", isNew)

/** used to restore configuration within InsulinPlugin and insulin Editor */
fun ICfg.Companion.fromJson(json: JSONObject): ICfg = ICfg(
    insulinLabel = json.optString("insulinLabel", ""),
    insulinEndTime = json.optLong("insulinEndTime", 0),
    insulinPeakTime = json.optLong("insulinPeakTime", 0),
    concentration = json.optDouble("concentration", 1.0)

) .also {
    it.insulinTemplate = json.optInt("insulinTemplate", 0)
    it.isNew =  json.optBoolean("isNew", false)
}

fun ICfg.toJsonObject(): JsonObject = buildJsonObject {
    put("insulinLabel", insulinLabel)
    put("insulinEndTime", insulinEndTime)
    put("insulinPeakTime", insulinPeakTime)
    put("concentration", JsonPrimitive(concentration))
    put("insulinTemplate", insulinTemplate)
    put("isNew", isNew)
}

/** used to restore configuration within InsulinPlugin and insulin Editor */
fun ICfg.Companion.fromJsonObject(json: JsonObject): ICfg {
    val icfg = ICfg(
        insulinLabel = json["insulinLabel"]?.jsonPrimitive?.contentOrNull ?: "",
        insulinEndTime = json["insulinEndTime"]?.jsonPrimitive?.longOrNull ?: 0,
        insulinPeakTime = json["insulinPeakTime"]?.jsonPrimitive?.longOrNull ?: 0,
        concentration = json["concentration"]?.jsonPrimitive?.doubleOrNull ?: 1.0
    )

    icfg.insulinTemplate = json["insulinTemplate"]?.jsonPrimitive?.intOrNull ?: 0
    icfg.isNew = json["isNew"]?.jsonPrimitive?.booleanOrNull ?: false

    return icfg
}
package app.aaps.core.objects.extensions

import app.aaps.core.data.model.ICfg
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.json.JSONObject

/** used to save configuration within InsulinPlugin */
fun ICfg.toJson(): JSONObject = JSONObject()
    .put("insulinLabel", insulinLabel)
    .put("insulinEndTime", insulinEndTime)
    .put("insulinPeakTime", insulinPeakTime)
    .put("concentration", concentration)
    .put("insulinNickname", insulinNickname)

/** used to restore configuration within InsulinPlugin and insulin Editor */
fun ICfg.Companion.fromJson(json: JSONObject): ICfg = ICfg(
    insulinLabel = json.optString("insulinLabel", ""),
    insulinEndTime = json.optLong("insulinEndTime", 0),
    insulinPeakTime = json.optLong("insulinPeakTime", 0),
    concentration = json.optDouble("concentration", 1.0)

).also { it.insulinNickname = json.optString("insulinNickname", "") }

fun ICfg.toJsonObject(): JsonObject = buildJsonObject {
    put("insulinLabel", insulinLabel)
    put("insulinEndTime", insulinEndTime)
    put("insulinPeakTime", insulinPeakTime)
    put("concentration", JsonPrimitive(concentration))
    put("insulinNickname", insulinNickname)
}

/** used to restore configuration within InsulinPlugin and insulin Editor */
fun ICfg.Companion.fromJsonObject(json: JsonObject): ICfg {
    val icfg = ICfg(
        insulinLabel = json["insulinLabel"]?.jsonPrimitive?.contentOrNull ?: "",
        insulinEndTime = json["insulinEndTime"]?.jsonPrimitive?.longOrNull ?: 0,
        insulinPeakTime = json["insulinPeakTime"]?.jsonPrimitive?.longOrNull ?: 0,
        concentration = json["concentration"]?.jsonPrimitive?.doubleOrNull ?: 1.0
    )

    icfg.insulinNickname = json["insulinNickname"]?.jsonPrimitive?.contentOrNull ?: ""

    return icfg
}
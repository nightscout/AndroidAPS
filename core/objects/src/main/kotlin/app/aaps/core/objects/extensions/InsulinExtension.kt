package app.aaps.core.objects.extensions

import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.insulin.Insulin
import org.json.JSONObject

fun ICfg.toJson(): JSONObject = JSONObject()
    .put("insulinLabel", insulinLabel)
    .put("insulinEndTime", insulinEndTime)
    .put("insulinPeakTime", insulinPeakTime)

fun ICfg.Companion.fromJson(json: JSONObject): ICfg = ICfg(
    insulinLabel = json.optString("insulinLabel", ""),
    insulinEndTime = json.optLong("insulinEndTime", 0),
    insulinPeakTime = json.optLong("insulinPeakTime", 0)
)
package app.aaps.core.objects.extensions

import app.aaps.core.data.model.ICfg
import org.json.JSONObject

/**
 * `org.json` form of the insulin configuration.
 *
 * The kotlinx pair ([toJsonObject] / [fromJsonObject]) is the shared one and lives in commonMain.
 * These two stay Android-only because `org.json` is part of the Android platform, and they go away
 * with the callers that still hold `JSONObject`.
 */

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

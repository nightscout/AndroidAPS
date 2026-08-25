package app.aaps.core.interfaces.tempTargets

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TTPreset
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parse JSON string into a list of TTPreset.
 * Note: nameRes is NOT set here — it depends on core:ui R strings.
 * Use [withNameRes] to set nameRes for display purposes.
 * @return List of TTPreset objects, or empty list if parsing fails
 */
fun String.toTTPresets(): List<TTPreset> {
    return try {
        if (isEmpty() || this == "[]") {
            emptyList()
        } else {
            val jsonArray = JSONArray(this)
            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                val reason = TT.Reason.fromString(obj.getString("reason"))
                TTPreset(
                    id = obj.getString("id"),
                    name = if (obj.has("name") && !obj.isNull("name")) obj.getString("name") else null,
                    reason = reason,
                    targetValue = obj.getDouble("targetValue"),
                    duration = obj.getLong("duration"),
                    isDeletable = obj.getBoolean("isDeletable")
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * Convert a list of TTPreset to JSON string.
 * nameRes is NOT persisted — Android resource IDs change between builds.
 */
fun List<TTPreset>.toJson(): String {
    val jsonArray = JSONArray()
    forEach { preset ->
        val obj = JSONObject().apply {
            put("id", preset.id)
            preset.name?.let { put("name", it) }
            put("reason", preset.reason.text)
            put("targetValue", preset.targetValue)
            put("duration", preset.duration)
            put("isDeletable", preset.isDeletable)
        }
        jsonArray.put(obj)
    }
    return jsonArray.toString()
}

/**
 * Get all TT presets from preferences.
 */
fun Preferences.ttPresets(): List<TTPreset> =
    get(StringNonKey.TempTargetPresets).toTTPresets()

/**
 * Get TT preset target value in mg/dL for the given reason.
 * Returns default value if preset not found.
 */
fun Preferences.ttTargetMgdl(reason: TT.Reason): Double =
    ttPresets().firstOrNull { it.reason == reason }?.targetValue
        ?: defaultTTTargetMgdl(reason)

/**
 * Get TT preset duration in minutes for the given reason.
 * Returns default value if preset not found.
 */
fun Preferences.ttDurationMinutes(reason: TT.Reason): Int =
    ttPresets().firstOrNull { it.reason == reason }?.let { (it.duration / 60_000).toInt() }
        ?: defaultTTDurationMinutes(reason)

private fun defaultTTTargetMgdl(reason: TT.Reason): Double = when (reason) {
    TT.Reason.EATING_SOON  -> Constants.DEFAULT_TT_EATING_SOON_TARGET
    TT.Reason.ACTIVITY     -> Constants.DEFAULT_TT_ACTIVITY_TARGET
    TT.Reason.HYPOGLYCEMIA -> Constants.DEFAULT_TT_HYPO_TARGET
    else                   -> Constants.DEFAULT_TT_EATING_SOON_TARGET
}

private fun defaultTTDurationMinutes(reason: TT.Reason): Int = when (reason) {
    TT.Reason.EATING_SOON  -> Constants.DEFAULT_TT_EATING_SOON_DURATION
    TT.Reason.ACTIVITY     -> Constants.DEFAULT_TT_ACTIVITY_DURATION
    TT.Reason.HYPOGLYCEMIA -> Constants.DEFAULT_TT_HYPO_DURATION
    else                   -> Constants.DEFAULT_TT_EATING_SOON_DURATION
}

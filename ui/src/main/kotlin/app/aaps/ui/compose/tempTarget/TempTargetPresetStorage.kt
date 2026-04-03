package app.aaps.ui.compose.tempTarget

import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TTPreset
import org.json.JSONArray
import org.json.JSONObject

/**
 * Extension function to convert a list of TTPreset to JSON string.
 * Note: nameRes is NOT persisted — Android resource IDs change between builds.
 * Instead, nameRes is derived from the reason field on deserialization.
 * @return JSON string representation of presets
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
 * Derive nameRes from TT.Reason for fixed (non-deletable) presets.
 * This avoids persisting Android resource IDs which change between builds.
 */
private fun nameResFromReason(reason: TT.Reason, isDeletable: Boolean): Int? {
    if (isDeletable) return null
    return when (reason) {
        TT.Reason.EATING_SOON  -> app.aaps.core.ui.R.string.eatingsoon
        TT.Reason.ACTIVITY     -> app.aaps.core.ui.R.string.activity
        TT.Reason.HYPOGLYCEMIA -> app.aaps.core.ui.R.string.hypo
        else                   -> null
    }
}

/**
 * Extension function to parse JSON string into a list of TTPreset.
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
                val isDeletable = obj.getBoolean("isDeletable")
                TTPreset(
                    id = obj.getString("id"),
                    name = if (obj.has("name") && !obj.isNull("name")) obj.getString("name") else null,
                    nameRes = nameResFromReason(reason, isDeletable),
                    reason = reason,
                    targetValue = obj.getDouble("targetValue"),
                    duration = obj.getLong("duration"),
                    isDeletable = isDeletable
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

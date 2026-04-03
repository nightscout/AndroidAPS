package app.aaps.ui.compose.quickLaunch

import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes/deserializes toolbar actions to/from JSON for persistence.
 *
 * Format: JSON array of objects.
 * - Static: `{"type":"insulin"}`
 * - Dynamic: `{"type":"quick_wizard","id":"uuid-here"}`
 */
object QuickLaunchSerializer {

    private const val KEY_TYPE = "type"
    private const val KEY_ID = "id"
    private const val KEY_PCT = "pct"
    private const val KEY_DUR = "dur"

    fun toJson(actions: List<QuickLaunchAction>): String {
        val array = JSONArray()
        for (action in actions) {
            val obj = JSONObject().put(KEY_TYPE, action.typeId)
            when (action) {
                is QuickLaunchAction.ProfileAction -> {
                    obj.put(KEY_ID, action.profileName)
                    if (action.percentage != 100) obj.put(KEY_PCT, action.percentage)
                    if (action.durationMinutes != 0) obj.put(KEY_DUR, action.durationMinutes)
                }

                else                               -> action.dynamicId?.let { obj.put(KEY_ID, it) }
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun fromJson(json: String): List<QuickLaunchAction> {
        if (json.isBlank()) return QuickLaunchAction.default
        return try {
            val array = JSONArray(json)
            val result = mutableListOf<QuickLaunchAction>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val type = obj.optString(KEY_TYPE, "")
                val id = obj.optString(KEY_ID, "")
                deserializeAction(type, id, obj)?.let { result.add(it) }
            }
            // Ensure ToolbarConfig is always present and last
            result.removeAll { it == QuickLaunchAction.QuickLaunchConfig }
            result.add(QuickLaunchAction.QuickLaunchConfig)
            result
        } catch (_: Exception) {
            QuickLaunchAction.default
        }
    }

    private fun deserializeAction(type: String, id: String, obj: JSONObject): QuickLaunchAction? = when (type) {
        "quick_wizard" -> if (id.isNotEmpty()) QuickLaunchAction.QuickWizardAction(id) else null
        "automation"   -> if (id.isNotEmpty()) QuickLaunchAction.AutomationAction(id) else null
        "tt_preset"    -> if (id.isNotEmpty()) QuickLaunchAction.TempTargetPreset(id) else null
        "profile"      -> if (id.isNotEmpty()) QuickLaunchAction.ProfileAction(
            profileName = id,
            percentage = obj.optInt(KEY_PCT, 100),
            durationMinutes = obj.optInt(KEY_DUR, 0)
        ) else null

        "plugin"       -> if (id.isNotEmpty()) QuickLaunchAction.PluginAction(id) else null
        else           -> QuickLaunchAction.fromTypeId(type)
    }
}

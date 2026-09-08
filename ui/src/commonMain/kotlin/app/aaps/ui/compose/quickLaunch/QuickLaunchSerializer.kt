package app.aaps.ui.compose.quickLaunch

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Serializes/deserializes toolbar actions to/from JSON for persistence.
 *
 * Format: JSON array of objects.
 * - Static: `{"type":"insulin"}`
 * - Dynamic: `{"type":"quick_wizard","id":"uuid-here"}`
 *
 * kotlinx rather than `org.json`, so this can be shared. The stored shape is unchanged; only the key
 * order is now stable, where `org.json` wrote it in hash order. Nothing reads by position, so
 * documents written by either version load in both - `QuickLaunchSerializerTest` pins that.
 */
object QuickLaunchSerializer {

    private const val KEY_TYPE = "type"
    private const val KEY_ID = "id"
    private const val KEY_PCT = "pct"
    private const val KEY_DUR = "dur"

    fun toJson(actions: List<QuickLaunchAction>): String =
        buildJsonArray {
            for (action in actions) {
                addJsonObject {
                    put(KEY_TYPE, action.typeId)
                    when (action) {
                        is QuickLaunchAction.ProfileAction -> {
                            put(KEY_ID, action.profileName)
                            // Absent at the default, as before: the reader fills these in.
                            if (action.percentage != 100) put(KEY_PCT, action.percentage)
                            if (action.durationMinutes != 0) put(KEY_DUR, action.durationMinutes)
                        }

                        else                               -> action.dynamicId?.let { put(KEY_ID, it) }
                    }
                }
            }
        }.toString()

    fun fromJson(json: String): List<QuickLaunchAction> {
        if (json.isBlank()) return QuickLaunchAction.default
        return try {
            val array = Json.parseToJsonElement(json) as JsonArray
            val result = mutableListOf<QuickLaunchAction>()
            for (element in array) {
                val obj = element as JsonObject
                deserializeAction(obj.string(KEY_TYPE), obj.string(KEY_ID), obj)?.let { result.add(it) }
            }
            // Ensure ToolbarConfig is always present and last
            result.removeAll { it == QuickLaunchAction.QuickLaunchConfig }
            result.add(QuickLaunchAction.QuickLaunchConfig)
            result
        } catch (_: Exception) {
            // A half written preference must not stop the toolbar from loading at all.
            QuickLaunchAction.default
        }
    }

    /** Missing (or not a string) reads as empty, which is what `optString(key, "")` did. */
    private fun JsonObject.string(key: String): String =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""

    /** Missing (or unreadable) reads as [fallback], which is what `optInt(key, fallback)` did. */
    private fun JsonObject.int(key: String, fallback: Int): Int =
        (this[key] as? JsonPrimitive)?.let { runCatching { it.int }.getOrNull() } ?: fallback

    private fun deserializeAction(type: String, id: String, obj: JsonObject): QuickLaunchAction? = when (type) {
        "quick_wizard" -> if (id.isNotEmpty()) QuickLaunchAction.QuickWizardAction(id) else null
        "automation"   -> if (id.isNotEmpty()) QuickLaunchAction.AutomationAction(id) else null
        "tt_preset"    -> if (id.isNotEmpty()) QuickLaunchAction.TempTargetPreset(id) else null
        "profile"      -> if (id.isNotEmpty()) QuickLaunchAction.ProfileAction(
            profileName = id,
            percentage = obj.int(KEY_PCT, 100),
            durationMinutes = obj.int(KEY_DUR, 0)
        ) else null

        "scene"        -> if (id.isNotEmpty()) QuickLaunchAction.SceneAction(id) else null
        "plugin"       -> if (id.isNotEmpty()) QuickLaunchAction.PluginAction(id) else null
        else           -> QuickLaunchAction.fromTypeId(type)
    }
}

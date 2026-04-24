package app.aaps.ui.compose.scenes

import app.aaps.core.data.model.RM
import app.aaps.core.data.model.Scene
import app.aaps.core.data.model.SceneAction
import app.aaps.core.data.model.SceneEndAction
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import org.json.JSONArray
import org.json.JSONObject

/**
 * Extension function to convert a list of Scene to JSON string.
 * @return JSON string representation of scenes
 */
fun List<Scene>.toJson(): String {
    val jsonArray = JSONArray()
    forEach { scene ->
        val obj = JSONObject().apply {
            put("id", scene.id)
            put("name", scene.name)
            put("icon", scene.icon)
            put("defaultDurationMinutes", scene.defaultDurationMinutes)
            put("isDeletable", scene.isDeletable)
            put("isEnabled", scene.isEnabled)
            put("sortOrder", scene.sortOrder)
            put("actions", scene.actions.toJsonArray())
            put("endAction", scene.endAction.toJson())
        }
        jsonArray.put(obj)
    }
    return jsonArray.toString()
}

/**
 * Extension function to parse JSON string into a list of Scene.
 * @return List of Scene objects, or empty list if parsing fails
 */
fun String.toScenes(): List<Scene> {
    return try {
        if (isEmpty() || this == "[]") {
            emptyList()
        } else {
            val jsonArray = JSONArray(this)
            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                Scene(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    icon = obj.optString("icon", "star"),
                    defaultDurationMinutes = obj.optInt("defaultDurationMinutes", 60),
                    isDeletable = obj.optBoolean("isDeletable", true),
                    isEnabled = obj.optBoolean("isEnabled", true),
                    sortOrder = obj.optInt("sortOrder", 0),
                    actions = obj.optJSONArray("actions")?.toSceneActions() ?: emptyList(),
                    endAction = obj.optJSONObject("endAction")?.toSceneEndAction() ?: SceneEndAction.Notification
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

// --- SceneAction serialization ---

private fun List<SceneAction>.toJsonArray(): JSONArray {
    val arr = JSONArray()
    forEach { action ->
        val obj = JSONObject()
        when (action) {
            is SceneAction.TempTarget       -> obj.apply {
                put("type", "temp_target")
                put("reason", action.reason.text)
                put("targetMgdl", action.targetMgdl)
            }

            is SceneAction.ProfileSwitch    -> obj.apply {
                put("type", "profile_switch")
                put("profileName", action.profileName)
                put("percentage", action.percentage)
                put("timeShiftHours", action.timeShiftHours)
            }

            is SceneAction.SmbToggle        -> obj.apply {
                put("type", "smb_toggle")
                put("enabled", action.enabled)
            }

            is SceneAction.LoopModeChange   -> obj.apply {
                put("type", "loop_mode")
                put("mode", action.mode.name)
            }

            is SceneAction.CarePortalEvent  -> obj.apply {
                put("type", "careportal")
                put("teType", action.type.text)
                put("note", action.note)
            }
        }
        arr.put(obj)
    }
    return arr
}

private fun JSONArray.toSceneActions(): List<SceneAction> {
    return (0 until length()).mapNotNull { i ->
        val obj = getJSONObject(i)
        when (obj.getString("type")) {
            "temp_target"    -> SceneAction.TempTarget(
                reason = TT.Reason.fromString(obj.getString("reason")),
                targetMgdl = obj.getDouble("targetMgdl")
            )

            "profile_switch" -> SceneAction.ProfileSwitch(
                profileName = obj.getString("profileName"),
                percentage = obj.optInt("percentage", 100),
                timeShiftHours = obj.optInt("timeShiftHours", 0)
            )

            "smb_toggle"     -> SceneAction.SmbToggle(
                enabled = obj.getBoolean("enabled")
            )

            "loop_mode"      -> SceneAction.LoopModeChange(
                mode = try {
                    RM.Mode.valueOf(obj.getString("mode"))
                } catch (_: Exception) {
                    RM.Mode.CLOSED_LOOP
                }
            )

            "careportal"     -> SceneAction.CarePortalEvent(
                type = TE.Type.entries.firstOrNull { it.text == obj.getString("teType") } ?: TE.Type.NOTE,
                note = obj.optString("note", "")
            )

            else             -> null
        }
    }
}

// --- SceneEndAction serialization ---

private fun SceneEndAction.toJson(): JSONObject = JSONObject().apply {
    when (this@toJson) {
        is SceneEndAction.Notification -> put("type", "notification")
        is SceneEndAction.SuggestScene -> {
            put("type", "suggest_scene")
            put("sceneId", sceneId)
        }
    }
}

private fun JSONObject.toSceneEndAction(): SceneEndAction =
    when (optString("type", "notification")) {
        "suggest_scene" -> SceneEndAction.SuggestScene(getString("sceneId"))
        else            -> SceneEndAction.Notification
    }

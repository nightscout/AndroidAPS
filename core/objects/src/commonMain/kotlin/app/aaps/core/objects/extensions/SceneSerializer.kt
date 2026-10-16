package app.aaps.core.objects.extensions

import app.aaps.core.data.model.RM
import app.aaps.core.data.model.Scene
import app.aaps.core.data.model.SceneAction
import app.aaps.core.data.model.SceneEndAction
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.utils.lenientBoolean
import app.aaps.core.utils.lenientBooleanOrNull
import app.aaps.core.utils.lenientDoubleOrNull
import app.aaps.core.utils.lenientInt
import app.aaps.core.utils.lenientString
import app.aaps.core.utils.lenientStringOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Scene list to and from the text held in `StringNonKey.SceneDefinitions`.
 *
 * Both entry points take and return a [String], so nothing outside this file ever sees the JSON
 * library and no `org.json` adapter is needed.
 *
 * Reads go through the lenient helpers so the documents `org.json` used to write keep parsing:
 * `defaultDurationMinutes` may be stored as `60`, `60.0` or `"60"` and still reads as 60. Writes
 * differ in one harmless way - `org.json` printed a whole numbered Double as a bare integer
 * (`140.0` as `140`) and kotlinx prints `140.0`. Both parse back to the same value, and only a scene
 * the user actually edits gets rewritten.
 */

/**
 * Extension function to convert a list of Scene to JSON string.
 * @return JSON string representation of scenes
 */
fun List<Scene>.toJson(): String =
    buildJsonArray {
        this@toJson.forEach { scene ->
            add(
                buildJsonObject {
                    put("id", scene.id)
                    put("name", scene.name)
                    put("icon", scene.icon)
                    put("defaultDurationMinutes", scene.defaultDurationMinutes)
                    put("isDeletable", scene.isDeletable)
                    put("isEnabled", scene.isEnabled)
                    put("sortOrder", scene.sortOrder)
                    put("actions", scene.actions.toJsonArray())
                    put("endAction", scene.endAction.toJsonObject())
                }
            )
        }
    }.toString()

/**
 * Extension function to parse JSON string into a list of Scene.
 * @return List of Scene objects, or empty list if parsing fails
 */
fun String.toScenes(): List<Scene> {
    return try {
        if (isEmpty() || this == "[]") {
            emptyList()
        } else {
            val jsonArray = Json.parseToJsonElement(this) as JsonArray
            // Skip a scene we cannot read, keep the rest. Without the per-entry catch, one missing
            // "id" or "name" threw all the way out to the outer catch below and returned an empty
            // list - a single damaged entry silently wiped the user's whole scene catalogue. This
            // matches how an unknown action type is already handled: skipped, not fatal.
            jsonArray.mapNotNull { element ->
                runCatching {
                    val obj = element.jsonObject
                    Scene(
                        // id and name have no sensible default, so a missing one drops this scene.
                        id = obj.lenientStringOrNull("id") ?: error("scene without id"),
                        name = obj.lenientStringOrNull("name") ?: error("scene without name"),
                        icon = obj.lenientString("icon", "star"),
                        defaultDurationMinutes = obj.lenientInt("defaultDurationMinutes", 60),
                        isDeletable = obj.lenientBoolean("isDeletable", true),
                        isEnabled = obj.lenientBoolean("isEnabled", true),
                        sortOrder = obj.lenientInt("sortOrder", 0),
                        actions = (obj["actions"] as? JsonArray)?.toSceneActions() ?: emptyList(),
                        endAction = (obj["endAction"] as? JsonObject)?.toSceneEndAction() ?: SceneEndAction.Notification
                    )
                }.getOrNull()
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

// --- SceneAction serialization ---

private fun List<SceneAction>.toJsonArray(): JsonArray =
    buildJsonArray {
        forEach { action ->
            add(
                buildJsonObject {
                    when (action) {
                        is SceneAction.TempTarget      -> {
                            put("type", "temp_target")
                            put("reason", action.reason.text)
                            put("targetMgdl", action.targetMgdl)
                        }

                        is SceneAction.ProfileSwitch   -> {
                            put("type", "profile_switch")
                            put("profileName", action.profileName)
                            put("percentage", action.percentage)
                            put("timeShiftHours", action.timeShiftHours)
                        }

                        is SceneAction.SmbToggle       -> {
                            put("type", "smb_toggle")
                            put("enabled", action.enabled)
                        }

                        is SceneAction.LoopModeChange  -> {
                            put("type", "loop_mode")
                            put("mode", action.mode.name)
                        }

                        is SceneAction.CarePortalEvent -> {
                            put("type", "careportal")
                            put("teType", action.type.text)
                            put("note", action.note)
                        }
                    }
                }
            )
        }
    }

private fun JsonArray.toSceneActions(): List<SceneAction> {
    return mapNotNull { element ->
        val obj = element.jsonObject
        when (obj.lenientStringOrNull("type")) {
            "temp_target"    -> SceneAction.TempTarget(
                reason = TT.Reason.fromString(obj.lenientString("reason")),
                // No default: an unreadable target must not silently become 0 mg/dl.
                targetMgdl = obj.lenientDoubleOrNull("targetMgdl") ?: error("temp target without targetMgdl")
            )

            "profile_switch" -> SceneAction.ProfileSwitch(
                profileName = obj.lenientString("profileName"),
                percentage = obj.lenientInt("percentage", 100),
                timeShiftHours = obj.lenientInt("timeShiftHours", 0)
            )

            "smb_toggle"     -> SceneAction.SmbToggle(
                // getBoolean threw for a missing or non-boolean value, and the throw was fatal to the
                // whole list. Keeping it fatal to this action only, via the same error() route.
                enabled = obj.lenientBooleanOrNull("enabled") ?: error("smb toggle without enabled")
            )

            "loop_mode"      -> SceneAction.LoopModeChange(
                // Deliberately swallowed, as before: an unreadable mode defaults to closed loop
                // rather than dropping the action.
                mode = try {
                    RM.Mode.valueOf(obj.lenientString("mode"))
                } catch (_: Exception) {
                    RM.Mode.CLOSED_LOOP
                }
            )

            "careportal"     -> SceneAction.CarePortalEvent(
                type = TE.Type.entries.firstOrNull { it.text == obj.lenientString("teType") } ?: TE.Type.NOTE,
                note = obj.lenientString("note", "")
            )

            else             -> null
        }
    }
}

// --- SceneEndAction serialization ---

private fun SceneEndAction.toJsonObject(): JsonObject =
    buildJsonObject {
        when (this@toJsonObject) {
            is SceneEndAction.Notification -> put("type", "notification")

            is SceneEndAction.ChainScene   -> {
                put("type", "chain_scene")
                put("sceneId", sceneId)
            }
        }
    }

private fun JsonObject.toSceneEndAction(): SceneEndAction =
    when (lenientString("type", "notification")) {
        "chain_scene" -> SceneEndAction.ChainScene(
            lenientStringOrNull("sceneId") ?: error("chain scene without sceneId")
        )

        else          -> SceneEndAction.Notification
    }

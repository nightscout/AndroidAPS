package app.aaps.plugins.automation.triggers

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.automation.AutomationStrings
import kotlinx.serialization.json.JsonObject

/**
 * True when the scene selected in the action is the one running now.
 * Used only as a precondition by [app.aaps.plugins.automation.actions.ActionStopScene], so the
 * whole automation is skipped (no log entry, no re-run) when that scene is not running.
 * Not user-selectable and never serialized — preconditions are rebuilt from the action class.
 */
class TriggerSelectedSceneRunning(
    deps: TriggerDeps,
    private val sceneApi: SceneAutomationApi,
    private val selectedSceneId: () -> String
) : Trigger(deps) {

    override suspend fun shouldRun(): Boolean {
        val ready = sceneApi.isSceneActive(selectedSceneId())
        aapsLogger.debug(
            LTag.AUTOMATION,
            (if (ready) "Ready for execution: " else "NOT ready for execution: ") + friendlyDescription()
        )
        return ready
    }

    override fun dataJSON(): JsonObject = JsonObject(emptyMap())
    override fun fromJSON(data: String): Trigger = this

    override fun friendlyName(): TextRef = AutomationStrings.precondition_scene_running_name
    override fun friendlyDescription(): String =
        rh.gs(AutomationStrings.precondition_scene_running, sceneApi.getScene(selectedSceneId())?.name ?: "")

    override fun composeIcon() = Icons.Filled.PlayArrow
    override fun elementType() = ElementType.SCENE

    override fun duplicate(): Trigger = TriggerSelectedSceneRunning(deps, sceneApi, selectedSceneId)
}

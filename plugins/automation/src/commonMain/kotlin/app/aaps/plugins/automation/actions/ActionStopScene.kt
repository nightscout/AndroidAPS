package app.aaps.plugins.automation.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.scenes.SceneAutomationResult
import app.aaps.core.interfaces.scenes.SceneIconResolver
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.utils.lenientString
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.plugins.automation.elements.InputSceneName
import app.aaps.plugins.automation.triggers.Trigger
import app.aaps.plugins.automation.triggers.TriggerDeps
import app.aaps.plugins.automation.triggers.TriggerSelectedSceneRunning
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Ends the selected scene, but only when it is the one running now. Another running scene is left
 * alone, so a rule cannot end a scene the user started for another reason. The follow-up scene of
 * the ended scene is not started.
 *
 * Do not put this in one rule together with [ActionRunScene]: that action needs "no scene is
 * running" and this one needs "the selected scene is running". Both are checked before the first
 * action runs, so such a rule never runs. Use two rules.
 */
class ActionStopScene(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    pumpEnactResultProvider: () -> PumpEnactResult,
    private val sceneApi: SceneAutomationApi,
    private val sceneIconResolver: SceneIconResolver,
    // Only to build the Trigger precondition below.
    triggerDeps: TriggerDeps
) : Action(aapsLogger, rh, pumpEnactResultProvider) {

    var scene: InputSceneName = InputSceneName()

    // Skip the whole automation (no log entry) while the selected scene is not running —
    // a trigger like "COB lower than 5" stays true and would fire every cycle.
    override var precondition: Trigger? = TriggerSelectedSceneRunning(triggerDeps, sceneApi) { scene.value }

    override fun friendlyName(): TextRef = AutomationStrings.action_stop_scene
    override fun shortDescription(): String =
        rh.gs(AutomationStrings.action_stop_scene_short, sceneApi.getScene(scene.value)?.name ?: "")

    override fun composeIcon() = sceneIconResolver.iconForScene(scene.value) ?: Icons.Filled.Stop
    override fun elementType() = ElementType.SCENE

    override suspend fun doAction(): PumpEnactResult {
        // The precondition normally keeps the rule from running at all. This is for a scene that
        // ended between that check and now, for example by an earlier action of the same rule.
        if (!sceneApi.isSceneActive(scene.value))
            return pumpEnactResultProvider().success(true).comment(AutomationStrings.action_scene_not_running)
        return when (val result = sceneApi.stopScene(scene.value)) {
            SceneAutomationResult.Success           ->
                pumpEnactResultProvider().success(true).comment(CoreUiStrings.ok)

            is SceneAutomationResult.Failed         ->
                pumpEnactResultProvider().success(false)
                    .comment(result.message ?: rh.gs(CoreUiStrings.error))

            // stopScene() never returns these; if one does, the contract changed.
            SceneAutomationResult.SceneNotFound,
            SceneAutomationResult.SceneDisabled,
            is SceneAutomationResult.ChainCompleted -> error("stopScene returned ${result::class.simpleName} — contract violated")
        }
    }

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String =
        buildJsonObject {
            put("type", this@ActionStopScene::class.simpleName)
            put("data", buildJsonObject { put("sceneId", scene.value) })
        }.toString()

    override fun fromJSON(data: String): Action {
        val o = jsonOf(data)
        scene.value = o.lenientString("sceneId", "")
        return this
    }

    // A disabled scene can still be running, so only a missing scene makes the action invalid.
    override fun isValid(): Boolean = sceneApi.getScene(scene.value) != null
}

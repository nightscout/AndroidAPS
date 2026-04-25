package app.aaps.plugins.automation.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.scenes.SceneAutomationResult
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.compose.IconTint
import app.aaps.plugins.automation.elements.ComparatorExists
import app.aaps.plugins.automation.elements.InputSceneName
import app.aaps.plugins.automation.triggers.Trigger
import app.aaps.plugins.automation.triggers.TriggerSceneActive
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import javax.inject.Inject

class ActionRunScene(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var sceneApi: SceneAutomationApi

    var scene: InputSceneName = InputSceneName()

    // Skip activation if a scene is already running — don't overwrite a manually
    // activated scene the user may be relying on.
    override var precondition: Trigger? = TriggerSceneActive(injector, ComparatorExists.Compare.NOT_EXISTS)

    override fun friendlyName(): Int = R.string.action_run_scene
    override fun shortDescription(): String =
        rh.gs(R.string.action_run_scene_short, sceneApi.getScene(scene.value)?.name ?: "")

    // Show the assigned scene's icon when one is selected; fall back to PlayArrow in the
    // chooser sheet (no scene yet) or if the scene was deleted.
    override fun composeIcon() = sceneApi.iconForScene(scene.value) ?: Icons.Filled.PlayArrow
    override fun composeIconTint() = IconTint.Scene

    override suspend fun doAction(): PumpEnactResult =
        when (val result = sceneApi.runScene(scene.value)) {
            SceneAutomationResult.Success       ->
                pumpEnactResultProvider.get().success(true).comment(app.aaps.core.ui.R.string.ok)

            SceneAutomationResult.SceneNotFound ->
                pumpEnactResultProvider.get().success(false).comment(R.string.action_scene_not_found)

            SceneAutomationResult.SceneDisabled ->
                pumpEnactResultProvider.get().success(false).comment(R.string.action_scene_disabled)

            is SceneAutomationResult.Failed     ->
                pumpEnactResultProvider.get().success(false)
                    .comment(result.message ?: rh.gs(app.aaps.core.ui.R.string.error))
        }

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String {
        val data = JSONObject().put("sceneId", scene.value)
        return JSONObject()
            .put("type", this.javaClass.simpleName)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Action {
        val o = JSONObject(data)
        scene.value = JsonHelper.safeGetString(o, "sceneId", "")
        return this
    }

    override fun isValid(): Boolean {
        val s = sceneApi.getScene(scene.value) ?: return false
        return s.isEnabled
    }
}

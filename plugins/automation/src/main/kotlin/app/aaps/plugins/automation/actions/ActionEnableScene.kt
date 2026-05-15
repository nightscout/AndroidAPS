package app.aaps.plugins.automation.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.scenes.SceneAutomationResult
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.compose.IconTint
import app.aaps.plugins.automation.elements.InputSceneName
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import javax.inject.Inject

class ActionEnableScene(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var sceneApi: SceneAutomationApi

    var scene: InputSceneName = InputSceneName()

    override fun friendlyName(): Int = R.string.action_enable_scene
    override fun shortDescription(): String =
        rh.gs(R.string.action_enable_scene_short, sceneApi.getScene(scene.value)?.name ?: "")

    override fun composeIcon() = sceneApi.iconForScene(scene.value) ?: Icons.Filled.Visibility
    override fun composeIconTint() = IconTint.Scene

    override suspend fun doAction(): PumpEnactResult =
        when (val result = sceneApi.setEnabled(scene.value, true)) {
            SceneAutomationResult.Success       ->
                pumpEnactResultProvider.get().success(true).comment(app.aaps.core.ui.R.string.ok)

            SceneAutomationResult.SceneNotFound ->
                pumpEnactResultProvider.get().success(false).comment(R.string.action_scene_not_found)

            is SceneAutomationResult.Failed     ->
                pumpEnactResultProvider.get().success(false)
                    .comment(result.message ?: rh.gs(app.aaps.core.ui.R.string.error))

            // setEnabled() never returns SceneDisabled; if it ever does, the contract changed.
            SceneAutomationResult.SceneDisabled -> error("setEnabled returned SceneDisabled — contract violated")
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

    override fun isValid(): Boolean = sceneApi.getScene(scene.value) != null
}

package app.aaps.plugins.automation.actions

import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.automation.AutomationStrings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.scenes.SceneAutomationResult
import app.aaps.core.interfaces.scenes.SceneIconResolver
import app.aaps.core.utils.lenientString
import app.aaps.plugins.automation.elements.InputSceneName
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ActionEnableScene(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    pumpEnactResultProvider: () -> PumpEnactResult,
    private val sceneApi: SceneAutomationApi,
    private val sceneIconResolver: SceneIconResolver
) : Action(aapsLogger, rh, pumpEnactResultProvider) {


    var scene: InputSceneName = InputSceneName()

    override fun friendlyName(): TextRef = AutomationStrings.action_enable_scene
    override fun shortDescription(): String =
        rh.gs(AutomationStrings.action_enable_scene_short, sceneApi.getScene(scene.value)?.name ?: "")

    override fun composeIcon() = sceneIconResolver.iconForScene(scene.value) ?: Icons.Filled.Visibility
    override fun elementType() = ElementType.SCENE

    override suspend fun doAction(): PumpEnactResult =
        when (val result = sceneApi.setEnabled(scene.value, true)) {
            SceneAutomationResult.Success           ->
                pumpEnactResultProvider().success(true).comment(CoreUiStrings.ok)

            SceneAutomationResult.SceneNotFound     ->
                pumpEnactResultProvider().success(false).comment(AutomationStrings.action_scene_not_found)

            is SceneAutomationResult.Failed         ->
                pumpEnactResultProvider().success(false)
                    .comment(result.message ?: rh.gs(CoreUiStrings.error))

            // setEnabled() never returns SceneDisabled or ChainCompleted; if either does, the contract changed.
            SceneAutomationResult.SceneDisabled,
            is SceneAutomationResult.ChainCompleted -> error("setEnabled returned ${result::class.simpleName} — contract violated")
        }

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String =
        buildJsonObject {
            put("type", this@ActionEnableScene::class.simpleName)
            put("data", buildJsonObject { put("sceneId", scene.value) })
        }.toString()

    override fun fromJSON(data: String): Action {
        val o = jsonOf(data)
        scene.value = o.lenientString("sceneId", "")
        return this
    }

    override fun isValid(): Boolean = sceneApi.getScene(scene.value) != null
}

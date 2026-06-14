package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import app.aaps.core.interfaces.scenes.SceneAutomationResult
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.nfcCommands.NfcJsonKeys
import org.json.JSONObject
import app.aaps.plugins.sync.R
import app.aaps.core.ui.R as CoreUiR

class RunSceneAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    @StringRes override val labelResId = R.string.nfccommands_cmd_run_scene
    override val elementType = ElementType.SCENE
    override val argType = listOf(ArgType.SCENE_ID)
    override val icon
        get() = elementType.icon()

    override fun isSupported(): Boolean {
        return plugin.sceneAutomationApi.getScenes().isNotEmpty()
    }

    override suspend fun getDefaultParams(): JSONObject {
        val firstScene = plugin.sceneAutomationApi.getScenes().firstOrNull()
        return JSONObject().put(NfcJsonKeys.SCENE_ID, firstScene?.id ?: "")
    }

    override suspend fun execute(): NfcExecutionResult {
        val sceneId = params.optString(NfcJsonKeys.SCENE_ID)
        if (sceneId.isNullOrBlank()) return invalidFormat()

        return when (val result = plugin.sceneAutomationApi.runScene(sceneId)) {
            SceneAutomationResult.Success ->
                NfcExecutionResult(true, plugin.rh.gs(CoreUiR.string.ok))
            SceneAutomationResult.SceneNotFound ->
                NfcExecutionResult(false, plugin.rh.gs(R.string.nfccommands_scene_not_found))
            SceneAutomationResult.SceneDisabled ->
                NfcExecutionResult(false, plugin.rh.gs(R.string.nfccommands_scene_disabled))
            is SceneAutomationResult.Failed ->
                NfcExecutionResult(false, result.message ?: plugin.rh.gs(CoreUiR.string.error))
            is SceneAutomationResult.ChainCompleted ->
                NfcExecutionResult(true, plugin.rh.gs(CoreUiR.string.ok))
        }
    }
}

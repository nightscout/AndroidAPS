package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.scenes.SceneAutomationResult
import app.aaps.core.interfaces.scenes.SceneIconResolver
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.R
import app.aaps.core.ui.R as CoreUiR
import app.aaps.plugins.sync.nfcCommands.NfcParams

class RunSceneAction(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    uel: UserEntryLogger,
    private val sceneAutomationApi: SceneAutomationApi,
    private val sceneIconResolver: SceneIconResolver
) : NfcAction(aapsLogger, rh, uel) {
    @StringRes override val labelResId = R.string.nfccommands_cmd_run_scene
    override val elementType = ElementType.SCENE
    override val argType = listOf(ArgType.SCENE_ID)
    override val icon
        get() = elementType.icon()
    override val secondaryIcon: ImageVector?
        get() = (params.sceneId ?: "").let { sceneIconResolver.iconForScene(it) }

    override fun isSupported(): Boolean {
        return sceneAutomationApi.getScenes().isNotEmpty()
    }

    override suspend fun getDefaultParams() =
        NfcParams(sceneId = sceneAutomationApi.getScenes().firstOrNull()?.id ?: "")

    override suspend fun formatParams(tagName: String): String? {
        val sceneId = (params.sceneId ?: "")
        return sceneAutomationApi.getScene(sceneId)?.name
    }

    override suspend fun execute(tagName: String): NfcExecutionResult {
        val sceneId = (params.sceneId ?: "")
        if (sceneId.isNullOrBlank()) return invalidFormat()
        val sceneName = sceneAutomationApi.getScene(sceneId)?.name ?: sceneId

        return when (val result = sceneAutomationApi.runScene(sceneId)) {
            SceneAutomationResult.Success -> {
                uel.log(
                    action = Action.SCENE_ACTIVATED,
                    source = source,
                    note = tagName,
                    listValues = listOf(ValueWithUnit.SimpleString(sceneName))
                )
                NfcExecutionResult(true, sceneName)
            }

            SceneAutomationResult.SceneNotFound ->
                NfcExecutionResult(false, rh.gs(R.string.nfccommands_scene_not_found))

            SceneAutomationResult.SceneDisabled ->
                NfcExecutionResult(false, rh.gs(R.string.nfccommands_scene_disabled))

            is SceneAutomationResult.Failed ->
                NfcExecutionResult(false, result.message ?: rh.gs(CoreUiR.string.error))

            is SceneAutomationResult.ChainCompleted -> {
                uel.log(
                    action = Action.SCENE_ACTIVATED,
                    source = source,
                    note = tagName,
                    listValues = listOf(ValueWithUnit.SimpleString(sceneName))
                )
                NfcExecutionResult(true, sceneName)
            }
        }
    }
}

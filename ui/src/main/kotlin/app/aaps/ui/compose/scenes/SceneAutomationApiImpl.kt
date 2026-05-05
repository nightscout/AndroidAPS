package app.aaps.ui.compose.scenes

import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.data.model.Scene
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.scenes.SceneAutomationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SceneAutomationApiImpl @Inject constructor(
    private val sceneRepository: SceneRepository,
    private val sceneExecutor: SceneExecutor,
    private val activeSceneManager: ActiveSceneManager
) : SceneAutomationApi {

    override val scenesFlow: StateFlow<String> get() = sceneRepository.scenesFlow

    override fun isAnySceneActive(): Boolean = activeSceneManager.isActive()

    override val activeFlow: Flow<Boolean> =
        activeSceneManager.activeSceneState.map { it != null }.distinctUntilChanged()

    override suspend fun stopActiveScene(): SceneAutomationResult =
        when {
            activeSceneManager.isExpired() -> {
                sceneExecutor.dismiss()
                SceneAutomationResult.Success
            }

            activeSceneManager.isActive()  -> {
                val result = sceneExecutor.deactivate()
                if (result.success) SceneAutomationResult.Success
                else SceneAutomationResult.Failed(result.errorMessage)
            }

            else                           -> SceneAutomationResult.Success
        }

    override fun iconForScene(sceneId: String): ImageVector? =
        sceneRepository.getScene(sceneId)?.let { SceneIcons.fromKey(it.icon).icon }

    override fun getScenes(): List<Scene> = sceneRepository.getScenes()

    override fun getScene(id: String): Scene? = sceneRepository.getScene(id)

    override suspend fun runScene(id: String, durationMinutes: Int?): SceneAutomationResult {
        val scene = sceneRepository.getScene(id) ?: return SceneAutomationResult.SceneNotFound
        if (!scene.isEnabled) return SceneAutomationResult.SceneDisabled
        val effective = durationMinutes ?: scene.defaultDurationMinutes
        val result = sceneExecutor.activate(scene, effective)
        return if (result.success) SceneAutomationResult.Success
        else SceneAutomationResult.Failed(result.errorMessage)
    }

    override fun setEnabled(id: String, enabled: Boolean): SceneAutomationResult {
        val scene = sceneRepository.getScene(id) ?: return SceneAutomationResult.SceneNotFound
        if (scene.isEnabled == enabled) return SceneAutomationResult.Success
        sceneRepository.saveScene(scene.copy(isEnabled = enabled))
        return SceneAutomationResult.Success
    }
}

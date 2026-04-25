package app.aaps.core.interfaces.scenes

import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.data.model.Scene

/**
 * Surface exposed to the automation plugin so scene actions can list, run, enable
 * and disable scenes without depending on the :ui module.
 */
interface SceneAutomationApi {

    /** All scenes (enabled and disabled). */
    fun getScenes(): List<Scene>

    /** Scene by id, or null if not found. */
    fun getScene(id: String): Scene?

    /**
     * Activate a scene. Fails if missing or disabled.
     * @param durationMinutes override duration; null uses the scene's default.
     */
    suspend fun runScene(id: String, durationMinutes: Int? = null): SceneAutomationResult

    /** Set the enabled flag on a scene by id. Fails only if the scene is missing. */
    fun setEnabled(id: String, enabled: Boolean): SceneAutomationResult

    /** Whether a scene is currently active (running, not yet expired). */
    fun isAnySceneActive(): Boolean

    /** Resolved icon for the scene's stored icon key, or null if the scene is missing. */
    fun iconForScene(sceneId: String): ImageVector?
}

sealed interface SceneAutomationResult {

    data object Success : SceneAutomationResult
    data object SceneNotFound : SceneAutomationResult
    data object SceneDisabled : SceneAutomationResult
    data class Failed(val message: String?) : SceneAutomationResult
}

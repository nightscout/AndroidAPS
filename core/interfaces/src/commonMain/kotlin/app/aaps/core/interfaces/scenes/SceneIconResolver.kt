package app.aaps.core.interfaces.scenes

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Resolves a scene's stored icon key to a Compose [ImageVector]. The icon catalog is a `:ui` concern,
 * so this interface (impl in `:ui`) lets non-UI callers (the automation plugin) and the
 * `:implementation` scene engine show scene icons without depending on `:ui`.
 */
interface SceneIconResolver {

    /** Resolved icon for the scene's stored icon key, or null if the scene is missing. */
    fun iconForScene(sceneId: String): ImageVector?
}

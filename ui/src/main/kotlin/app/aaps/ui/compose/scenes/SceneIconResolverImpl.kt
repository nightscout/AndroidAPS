package app.aaps.ui.compose.scenes

import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.interfaces.scenes.SceneIconResolver
import app.aaps.core.interfaces.scenes.SceneStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SceneIconResolver] backed by the `:ui` [SceneIcons] catalog. Stays in `:ui` (where the icon catalog
 * and its labels live) while the scene engine moves to `:implementation`.
 */
@Singleton
class SceneIconResolverImpl @Inject constructor(
    private val sceneStore: SceneStore
) : SceneIconResolver {

    override fun iconForScene(sceneId: String): ImageVector? =
        sceneStore.getScene(sceneId)?.let { SceneIcons.fromKey(it.icon).icon }
}

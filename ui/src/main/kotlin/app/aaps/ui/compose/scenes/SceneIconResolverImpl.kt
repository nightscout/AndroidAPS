package app.aaps.ui.compose.scenes

import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.interfaces.scenes.SceneIconResolver
import app.aaps.core.interfaces.scenes.SceneStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import javax.inject.Inject

/**
 * [SceneIconResolver] backed by the `:ui` [SceneIcons] catalog. Stays in `:ui` (where the icon catalog
 * and its labels live) while the scene engine moves to `:implementation`.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class SceneIconResolverImpl @Inject constructor(
    private val sceneStore: SceneStore
) : SceneIconResolver {

    override fun iconForScene(sceneId: String): ImageVector? =
        sceneStore.getScene(sceneId)?.let { SceneIcons.fromKey(it.icon).icon }
}

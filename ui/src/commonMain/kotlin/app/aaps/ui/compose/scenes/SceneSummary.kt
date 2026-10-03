package app.aaps.ui.compose.scenes

import app.aaps.core.data.model.Scene
import app.aaps.core.data.model.SceneEndAction
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.CoreUiStrings

/**
 * The one-line summary of a scene, "3 actions, 2 hours", as the scene list shows it.
 *
 * Shared with the Scenes sheet and the QuickLaunch surfaces (toolbar tooltip, overflow menu,
 * configure screen) so a scene reads the same everywhere. Keep every scene summary on this function.
 */
fun sceneSummary(scene: Scene, rh: TextResolver, dateUtil: DateUtil): String =
    rh.gs(CoreUiStrings.scene_summary, scene.actions.size, formatSceneMinutes(scene.defaultDurationMinutes, rh, dateUtil))

/**
 * [sceneSummary] plus the follow-up scene, "3 actions, 2 hours → Cooldown", when the follow-up still
 * exists in the catalog. A deleted follow-up is left out: these are glance surfaces, and the scene
 * list is where a broken chain is reported.
 */
fun sceneSummaryWithChain(scene: Scene, rh: TextResolver, dateUtil: DateUtil, findScene: (String) -> Scene?): String {
    val summary = sceneSummary(scene, rh, dateUtil)
    val chainTargetName = (scene.endAction as? SceneEndAction.ChainScene)?.sceneId?.let { findScene(it)?.name }
    return if (chainTargetName != null) rh.gs(CoreUiStrings.scene_summary_with_chain, summary, chainTargetName) else summary
}

/** A scene duration in minutes as text; zero is "Indefinite (manual end)". */
fun formatSceneMinutes(minutes: Int, rh: TextResolver, dateUtil: DateUtil): String =
    if (minutes == 0) rh.gs(CoreUiStrings.scene_duration_indefinite)
    else dateUtil.niceTimeScalar(minutes * 60_000L, rh)

package app.aaps.ui.compose.scenes

import app.aaps.core.data.model.Scene
import app.aaps.core.data.model.SceneEndAction
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.formatMinutesAsDuration

/**
 * The one-line summary of a scene, "3 actions, 2 h", as the scene list shows it.
 *
 * Shared with the Scenes sheet and the QuickLaunch surfaces (toolbar tooltip, overflow menu,
 * configure screen) so a scene reads the same everywhere. The duration uses the short form that
 * confirmations and the QuickLaunch labels use. Keep every scene summary on this function.
 */
fun sceneSummary(scene: Scene, rh: TextResolver): String =
    rh.gs(CoreUiStrings.scene_summary, scene.actions.size, formatSceneMinutes(scene.defaultDurationMinutes, rh))

/**
 * [sceneSummary] plus the follow-up scene, "3 actions, 2 h → Cooldown", when the follow-up still
 * exists in the catalog. A deleted follow-up is left out: these are glance surfaces, and the scene
 * list is where a broken chain is reported.
 */
fun sceneSummaryWithChain(scene: Scene, rh: TextResolver, findScene: (String) -> Scene?): String {
    val summary = sceneSummary(scene, rh)
    val chainTargetName = (scene.endAction as? SceneEndAction.ChainScene)?.sceneId?.let { findScene(it)?.name }
    return if (chainTargetName != null) rh.gs(CoreUiStrings.scene_summary_with_chain, summary, chainTargetName) else summary
}

/** A scene duration in minutes as text; zero is "Indefinite (manual end)". */
private fun formatSceneMinutes(minutes: Int, rh: TextResolver): String =
    if (minutes == 0) rh.gs(CoreUiStrings.scene_duration_indefinite)
    else formatMinutesAsDuration(minutes, rh)

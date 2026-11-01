package app.aaps.ui.compose.scenes

import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.SceneAction
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.ui.CoreUiStrings

/**
 * One line per change that ending this scene will undo, for the "End scene" confirmation.
 * Used by both places that can end a scene (the overview banner and the Scenes screen), so they
 * tell the user the same thing. CarePortal entries are not undone; that gets one closing line.
 */
internal fun ActiveSceneState.revertSummaries(rh: TextResolver): List<String> {
    val summaries = mutableListOf<String>()
    var hasCarePortal = false

    for (action in scene.actions) {
        when (action) {
            is SceneAction.TempTarget      -> summaries.add(rh.gs(CoreUiStrings.scene_revert_tt))
            is SceneAction.ProfileSwitch   -> summaries.add(rh.gs(CoreUiStrings.scene_revert_profile))

            is SceneAction.SmbToggle       -> {
                val wasEnabled = priorSmb ?: true
                summaries.add(
                    if (wasEnabled) rh.gs(CoreUiStrings.scene_revert_smb_on)
                    else rh.gs(CoreUiStrings.scene_revert_smb_off)
                )
            }

            is SceneAction.LoopModeChange  -> summaries.add(rh.gs(CoreUiStrings.scene_revert_loop_mode))
            is SceneAction.CarePortalEvent -> hasCarePortal = true
        }
    }

    if (hasCarePortal) summaries.add(rh.gs(CoreUiStrings.scene_careportal_no_revert))
    return summaries
}

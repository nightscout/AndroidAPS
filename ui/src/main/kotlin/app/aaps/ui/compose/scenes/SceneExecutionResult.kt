package app.aaps.ui.compose.scenes

import app.aaps.core.data.model.SceneAction

/**
 * Result of executing a scene activation or deactivation.
 */
data class SceneExecutionResult(
    val success: Boolean,
    val actionResults: List<ActionResult> = emptyList(),
    val errorMessage: String? = null
) {

    data class ActionResult(
        val action: SceneAction,
        val success: Boolean,
        val errorMessage: String? = null,
        val psId: Long? = null // PS id created by ProfileSwitch action (for scene tracking)
    )
}

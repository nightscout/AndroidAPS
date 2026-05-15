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
        val recordId: Long? = null // DB id of the row created/updated by this action (TT/PS/RM)
    )
}

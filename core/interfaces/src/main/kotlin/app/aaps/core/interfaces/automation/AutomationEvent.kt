package app.aaps.core.interfaces.automation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class AutomationIconData(
    val icon: ImageVector,
    val tint: Color? = null
)

interface AutomationEvent {

    val id: String
    var isEnabled: Boolean
    var title: String
    suspend fun canRun(): Boolean
    suspend fun preconditionCanRun(): Boolean
    fun firstActionIcon(): AutomationIconData?

    /** Human-readable descriptions of each action (e.g. "Start temp target: 5.5 mmol 45 min") */
    fun actionsDescription(): List<String>

    /** @return set of trigger icons (recursive from trigger tree) */
    fun triggerIcons(): Set<AutomationIconData>

    /** @return set of action icons */
    fun actionIcons(): Set<AutomationIconData>
}

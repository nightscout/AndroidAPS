package app.aaps.core.interfaces.automation

import androidx.annotation.DrawableRes

interface AutomationEvent {

    val id: String
    var isEnabled: Boolean
    var title: String
    suspend fun canRun(): Boolean
    suspend fun preconditionCanRun(): Boolean
    @DrawableRes fun firstActionIcon(): Int?

    /** Human-readable descriptions of each action (e.g. "Start temp target: 5.5 mmol 45 min") */
    fun actionsDescription(): List<String>

    /** @return set of @DrawableRes trigger icon resource IDs (recursive from trigger tree) */
    fun triggerIcons(): Set<Int>

    /** @return set of @DrawableRes action icon resource IDs */
    fun actionIcons(): Set<Int>
}
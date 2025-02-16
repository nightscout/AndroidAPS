package app.aaps.core.interfaces.automation

import androidx.annotation.DrawableRes

interface AutomationEvent {

    var isEnabled: Boolean
    var title: String
    fun canRun(): Boolean
    fun preconditionCanRun(): Boolean
    @DrawableRes fun firstActionIcon(): Int?
}
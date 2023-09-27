package app.aaps.core.interfaces.automation

interface AutomationEvent {

    var isEnabled: Boolean
    var title: String
    fun canRun(): Boolean
    fun preconditionCanRun(): Boolean
}
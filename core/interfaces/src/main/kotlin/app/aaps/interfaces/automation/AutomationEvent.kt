package app.aaps.interfaces.automation

interface AutomationEvent {

    var isEnabled: Boolean
    var title: String
    fun canRun(): Boolean
    fun preconditionCanRun(): Boolean
}
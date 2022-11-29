package info.nightscout.interfaces.automation

interface AutomationEvent {
    var isEnabled: Boolean
    var title: String
    fun canRun(): Boolean
    fun preconditionCanRun() : Boolean
}
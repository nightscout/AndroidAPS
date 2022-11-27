package info.nightscout.interfaces.automation

interface Automation {
    fun userEvents(): List<AutomationEvent>
    fun processEvent(someEvent: AutomationEvent)
}
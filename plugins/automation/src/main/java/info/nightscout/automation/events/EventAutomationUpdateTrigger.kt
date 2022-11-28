package info.nightscout.automation.events

import info.nightscout.automation.triggers.TriggerConnector
import info.nightscout.rx.events.Event

class EventAutomationUpdateTrigger(val trigger: TriggerConnector) : Event()

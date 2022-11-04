package info.nightscout.automation.events

import info.nightscout.androidaps.events.Event
import info.nightscout.automation.triggers.TriggerConnector

class EventAutomationUpdateTrigger(val trigger: TriggerConnector) : Event()

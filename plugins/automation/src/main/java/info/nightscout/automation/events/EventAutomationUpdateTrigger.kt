package info.nightscout.automation.events

import app.aaps.interfaces.rx.events.Event
import info.nightscout.automation.triggers.TriggerConnector

class EventAutomationUpdateTrigger(val trigger: TriggerConnector) : Event()

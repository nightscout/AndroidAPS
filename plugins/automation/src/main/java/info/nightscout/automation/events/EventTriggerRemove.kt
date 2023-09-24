package info.nightscout.automation.events

import app.aaps.core.interfaces.rx.events.Event
import info.nightscout.automation.triggers.Trigger

class EventTriggerRemove(val trigger: Trigger) : Event()
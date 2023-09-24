package info.nightscout.automation.events

import app.aaps.interfaces.rx.events.Event
import info.nightscout.automation.triggers.Trigger

class EventTriggerClone(val trigger: Trigger) : Event()
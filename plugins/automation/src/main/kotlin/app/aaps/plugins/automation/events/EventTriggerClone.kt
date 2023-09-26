package app.aaps.plugins.automation.events

import app.aaps.core.interfaces.rx.events.Event
import app.aaps.plugins.automation.triggers.Trigger

class EventTriggerClone(val trigger: Trigger) : Event()
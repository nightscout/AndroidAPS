package app.aaps.plugins.automation.events

import app.aaps.core.interfaces.rx.events.Event
import app.aaps.plugins.automation.triggers.Trigger

class EventTriggerRemove(val trigger: Trigger) : Event()
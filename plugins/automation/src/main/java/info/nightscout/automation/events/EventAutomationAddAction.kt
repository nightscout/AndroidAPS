package info.nightscout.automation.events

import app.aaps.interfaces.rx.events.Event
import info.nightscout.automation.actions.Action

class EventAutomationAddAction(val action: Action) : Event()

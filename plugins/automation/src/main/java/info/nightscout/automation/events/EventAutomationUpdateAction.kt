package info.nightscout.automation.events

import app.aaps.core.interfaces.rx.events.Event
import info.nightscout.automation.actions.Action

class EventAutomationUpdateAction(val action: Action, val position: Int) : Event()
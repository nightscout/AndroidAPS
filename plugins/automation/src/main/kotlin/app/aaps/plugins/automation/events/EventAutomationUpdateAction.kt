package app.aaps.plugins.automation.events

import app.aaps.core.interfaces.rx.events.Event
import app.aaps.plugins.automation.actions.Action

class EventAutomationUpdateAction(val action: Action, val position: Int) : Event()
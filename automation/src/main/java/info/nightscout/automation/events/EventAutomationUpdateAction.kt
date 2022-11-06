package info.nightscout.automation.events

import info.nightscout.automation.actions.Action
import info.nightscout.rx.events.Event

class EventAutomationUpdateAction(val action: Action, val position: Int) : Event()
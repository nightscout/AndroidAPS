package info.nightscout.automation.events

import info.nightscout.androidaps.events.Event
import info.nightscout.automation.actions.Action

class EventAutomationUpdateAction(val action: Action, val position: Int) : Event()
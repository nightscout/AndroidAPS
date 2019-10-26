package info.nightscout.androidaps.plugins.general.automation.events

import info.nightscout.androidaps.events.Event
import info.nightscout.androidaps.plugins.general.automation.actions.Action

class EventAutomationUpdateAction(val action: Action, val position : Int) : Event() {
}
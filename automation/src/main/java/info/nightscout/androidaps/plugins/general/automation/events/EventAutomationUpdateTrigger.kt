package info.nightscout.androidaps.plugins.general.automation.events

import info.nightscout.androidaps.events.Event
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerConnector

class EventAutomationUpdateTrigger(val trigger: TriggerConnector) : Event()

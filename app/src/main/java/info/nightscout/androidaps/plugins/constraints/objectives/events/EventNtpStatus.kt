package info.nightscout.androidaps.plugins.constraints.objectives.events

import info.nightscout.androidaps.events.Event

class EventNtpStatus(val status: String, val percent: Int) : Event()
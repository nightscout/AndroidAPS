package info.nightscout.androidaps.events

import info.nightscout.androidaps.events.Event

class EventNtpStatus(val status: String, val percent: Int) : Event()
package info.nightscout.plugins.sync.nsShared.events

import info.nightscout.rx.events.Event

class EventNSConnectivityOptionChanged(val blockingReason: String) : Event()
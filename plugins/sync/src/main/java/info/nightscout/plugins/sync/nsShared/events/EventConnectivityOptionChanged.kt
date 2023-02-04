package info.nightscout.plugins.sync.nsShared.events

import info.nightscout.rx.events.Event

class EventConnectivityOptionChanged(val blockingReason: String) : Event()
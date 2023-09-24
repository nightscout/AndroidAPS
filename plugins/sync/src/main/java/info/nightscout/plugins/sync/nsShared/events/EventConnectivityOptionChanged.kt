package info.nightscout.plugins.sync.nsShared.events

import app.aaps.interfaces.rx.events.Event

class EventConnectivityOptionChanged(val blockingReason: String) : Event()
package app.aaps.plugins.sync.nsShared.events

import app.aaps.core.interfaces.rx.events.Event

class EventConnectivityOptionChanged(val blockingReason: String, val connected: Boolean) : Event()
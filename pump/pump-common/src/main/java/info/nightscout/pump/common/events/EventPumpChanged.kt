package info.nightscout.pump.common.events

import app.aaps.interfaces.rx.events.Event

class EventPumpChanged(
    var serialNumber: String,
    var connectionAddress: String,
    var parameters: MutableMap<String, Any>? = null
) : Event()
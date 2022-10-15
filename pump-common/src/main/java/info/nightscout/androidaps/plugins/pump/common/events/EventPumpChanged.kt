package info.nightscout.androidaps.plugins.pump.common.events

import info.nightscout.androidaps.events.Event

class EventPumpChanged(var serialNumber: String,
                       var connectionAddress: String,
                       var parameters: MutableMap<String, Any>? = null) : Event() {
}
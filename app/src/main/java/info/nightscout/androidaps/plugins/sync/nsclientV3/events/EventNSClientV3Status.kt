package info.nightscout.androidaps.plugins.sync.nsclientV3.events

import info.nightscout.androidaps.events.EventStatus
import info.nightscout.androidaps.interfaces.ResourceHelper

class EventNSClientV3Status(var text: String) : EventStatus() {
    override fun getStatus(rh: ResourceHelper): String = text
}

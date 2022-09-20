package info.nightscout.androidaps.plugins.sync.nsclient.events

import info.nightscout.androidaps.events.EventStatus
import info.nightscout.androidaps.interfaces.ResourceHelper

class EventNSClientStatus(var text: String) : EventStatus() {
    override fun getStatus(rh: ResourceHelper): String = text
}

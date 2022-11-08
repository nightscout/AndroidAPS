package info.nightscout.androidaps.plugins.sync.nsShared.events

import info.nightscout.androidaps.events.EventStatus
import info.nightscout.androidaps.interfaces.NsClient
import info.nightscout.shared.interfaces.ResourceHelper

class EventNSClientStatus(var text: String, val version: NsClient.Version) : EventStatus() {
    override fun getStatus(rh: ResourceHelper): String = text
}
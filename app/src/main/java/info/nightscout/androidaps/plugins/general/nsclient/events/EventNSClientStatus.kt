package info.nightscout.androidaps.plugins.general.nsclient.events

import info.nightscout.androidaps.events.EventStatus
import info.nightscout.androidaps.utils.resources.ResourceHelper

class EventNSClientStatus(var text: String) : EventStatus() {
    override fun getStatus(resourceHelper: ResourceHelper): String = text
}

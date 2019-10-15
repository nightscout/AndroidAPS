package info.nightscout.androidaps.plugins.general.nsclient.events

import info.nightscout.androidaps.events.EventStatus

class EventNSClientStatus(var text: String) : EventStatus() {
    override fun getStatus(): String = text
}

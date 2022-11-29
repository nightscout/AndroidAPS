package info.nightscout.plugins.sync.nsShared.events

import android.content.Context
import info.nightscout.interfaces.sync.NsClient
import info.nightscout.rx.events.EventStatus

class EventNSClientStatus(var text: String) : EventStatus() {
    override fun getStatus(context: Context): String = text
}
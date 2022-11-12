package info.nightscout.plugins.sync.nsShared.events

import android.content.Context
import info.nightscout.interfaces.sync.NsClient
import info.nightscout.rx.events.EventStatus

class EventNSClientStatus(var text: String, val version: NsClient.Version) : EventStatus() {
    override fun getStatus(context: Context): String = text
}
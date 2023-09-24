package info.nightscout.plugins.sync.nsShared.events

import android.content.Context
import app.aaps.core.interfaces.rx.events.EventStatus

class EventNSClientStatus(var text: String) : EventStatus() {

    override fun getStatus(context: Context): String = text
}
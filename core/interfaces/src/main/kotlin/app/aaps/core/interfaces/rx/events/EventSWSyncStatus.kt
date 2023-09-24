package app.aaps.core.interfaces.rx.events

import android.content.Context

// Pass pump status to setup wizard
class EventSWSyncStatus(val status: String) : EventStatus() {

    override fun getStatus(context: Context): String = status
}
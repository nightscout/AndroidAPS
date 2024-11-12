package app.aaps.core.interfaces.rx.events

import android.content.Context

// Pass directory to setup wizard
class EventAAPSDirectorySelected(val status: String) : EventStatus() {

    override fun getStatus(context: Context): String = status
}
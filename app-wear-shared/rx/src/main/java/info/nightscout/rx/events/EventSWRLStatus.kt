package info.nightscout.rx.events

import android.content.Context

// Pass RL status to setup wizard
class EventSWRLStatus(val status: String) : EventStatus() {

    override fun getStatus(context: Context): String = status
}
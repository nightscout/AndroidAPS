package app.aaps.plugins.sync.tidepool.events

import app.aaps.core.interfaces.rx.events.Event

data class EventTidepoolStatus(val status: String) : Event() {

    var date: Long = System.currentTimeMillis()
}
package app.aaps.core.interfaces.rx.events

class EventEffectiveProfileSwitchChanged(timestamp: Long?) : Event() {

    var startDate: Long = timestamp ?: 0L
}
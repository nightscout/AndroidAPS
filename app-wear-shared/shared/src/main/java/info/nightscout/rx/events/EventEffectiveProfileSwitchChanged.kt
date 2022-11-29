package info.nightscout.rx.events

class EventEffectiveProfileSwitchChanged(timestamp: Long?) : Event() {

    var startDate: Long = 0

    init {
        startDate = timestamp ?: 0L
    }
}
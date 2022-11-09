package info.nightscout.androidaps.events

import info.nightscout.androidaps.database.entities.EffectiveProfileSwitch
import info.nightscout.rx.events.Event

class EventEffectiveProfileSwitchChanged(effectiveProfileSwitch: EffectiveProfileSwitch?) : Event() {
    var startDate: Long = 0

    init {
        effectiveProfileSwitch?.let { startDate = it.timestamp}
    }
    constructor(startDate: Long) : this(null) {
        this.startDate = startDate
    }
}
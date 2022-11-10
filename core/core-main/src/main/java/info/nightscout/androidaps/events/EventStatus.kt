package info.nightscout.androidaps.events

import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.rx.events.Event

// pass string to startup wizard
abstract class EventStatus : Event() {
    abstract fun getStatus(rh: ResourceHelper) : String
}
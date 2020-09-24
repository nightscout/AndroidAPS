package info.nightscout.androidaps.events

import info.nightscout.androidaps.utils.resources.ResourceHelper

// pass string to startup wizard
abstract class EventStatus : Event() {
    abstract fun getStatus(resourceHelper: ResourceHelper) : String
}
package info.nightscout.androidaps.events

import info.nightscout.androidaps.utils.resources.ResourceHelper

class EventPreferenceChange : Event {

    var changedKey: String? = null
        private set

    constructor(key: String) {
        changedKey = key
    }

    constructor(rh: ResourceHelper, resourceID: Int) {
        changedKey = rh.gs(resourceID)
    }

    fun isChanged(rh: ResourceHelper, id: Int): Boolean {
        return changedKey == rh.gs(id)
    }
}

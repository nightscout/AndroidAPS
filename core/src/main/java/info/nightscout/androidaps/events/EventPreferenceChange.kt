package info.nightscout.androidaps.events

import info.nightscout.androidaps.utils.resources.ResourceHelper

class EventPreferenceChange : Event {
    private var changedKey: String? = null

    constructor(key: String) {
        changedKey = key
    }

    constructor(resourceHelper: ResourceHelper, resourceID: Int) {
        changedKey = resourceHelper.gs(resourceID)
    }

    fun isChanged(resourceHelper: ResourceHelper, id: Int): Boolean {
        return changedKey == resourceHelper.gs(id)
    }
}

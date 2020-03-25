package info.nightscout.androidaps.events

import android.content.res.Resources
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.utils.resources.ResourceHelper

class EventPreferenceChange : Event {
    private var changedKey: String? = null

    constructor(key: String) {
        changedKey = key
    }

    constructor(resourceHelper: ResourceHelper, resourceID: Int) {
        changedKey = resourceHelper.gs(resourceID)
    }

    @Deprecated("use injected version")
    constructor(resources: Resources, id: Int) {
        changedKey == resources.getString(id)
    }

    fun isChanged(resourceHelper: ResourceHelper, id: Int): Boolean {
        return changedKey == resourceHelper.gs(id)
    }

    @Deprecated("use injected version")
    fun isChanged(resources: Resources, id: Int): Boolean {
        return changedKey == resources.getString(id)
    }
}

package info.nightscout.androidaps.events

import info.nightscout.androidaps.MainApp

class EventPreferenceChange : Event {
    private var changedKey: String? = null

    constructor(key: String) {
        changedKey = key
    }

    constructor(resourceID: Int) {
        changedKey = MainApp.gs(resourceID)
    }

    fun isChanged(id: Int): Boolean {
        return changedKey == MainApp.gs(id)
    }
}

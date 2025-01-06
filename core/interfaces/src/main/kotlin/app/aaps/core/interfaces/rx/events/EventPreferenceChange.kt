package app.aaps.core.interfaces.rx.events

import app.aaps.core.interfaces.resources.ResourceHelper

class EventPreferenceChange : Event {

    var changedKey: String? = null
        private set

    constructor(key: String) {
        changedKey = key
    }

    constructor(key: Int, rh: ResourceHelper) {
        changedKey = rh.gs(key)
    }

    fun isChanged(key: String): Boolean {
        return changedKey == key
    }

    fun isChanged(key: Int, rh: ResourceHelper): Boolean {
        return changedKey == rh.gs(key)
    }
}
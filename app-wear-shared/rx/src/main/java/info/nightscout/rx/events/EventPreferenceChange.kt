package info.nightscout.rx.events

class EventPreferenceChange : Event {

    var changedKey: String? = null
        private set

    constructor(key: String) {
        changedKey = key
    }

    fun isChanged(key: String): Boolean {
        return changedKey == key
    }
}
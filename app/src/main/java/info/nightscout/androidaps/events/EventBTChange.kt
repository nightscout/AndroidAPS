package info.nightscout.androidaps.events

class EventBTChange constructor(val state: Change, val deviceName: String?) : Event() {
    enum class Change {
        CONNECT,
        DISCONNECT
    }
}
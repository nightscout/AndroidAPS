package info.nightscout.androidaps.events

// pass string to startup wizard
abstract class EventStatus :Event() {
    abstract fun getStatus() : String
}
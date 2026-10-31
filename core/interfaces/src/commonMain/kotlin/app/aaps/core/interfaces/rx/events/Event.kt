package app.aaps.core.interfaces.rx.events

/**
 * Base class for all events posted on the event bus.
 *
 * Events that carry data are `data class`es, so they print their values themselves. This default is
 * for the ones that carry none: for those the name is the whole message, and it is what the previous
 * reflection based toString produced for them anyway.
 */
abstract class Event {

    override fun toString(): String = this::class.simpleName ?: "Event"
}

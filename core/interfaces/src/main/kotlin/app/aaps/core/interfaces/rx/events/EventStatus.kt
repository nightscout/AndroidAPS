package app.aaps.core.interfaces.rx.events

import app.aaps.core.keys.interfaces.TextRef

/**
 * Base class for events that carry a status message used in UI updates
 */
abstract class EventStatus : Event() {

    /**
     * Gets the status message.
     *
     * @return The status message, as a platform neutral reference.
     */
    abstract fun getStatus(): TextRef
}
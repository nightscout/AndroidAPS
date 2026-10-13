package app.aaps.core.interfaces.rx.events

import android.content.Context

/**
 * Base class for events that carry a status message used in UI updates
 */
abstract class EventStatus : Event() {

    /**
     * Gets the status message.
     *
     * @param context The context.
     * @return The status message string.
     */
    abstract fun getStatus(context: Context): String
}
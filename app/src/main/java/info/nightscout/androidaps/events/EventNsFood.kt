package info.nightscout.androidaps.events

import android.os.Bundle

/**
 * Event which is published with data fetched from NightScout specific for the
 * Food-class.
 *
 * Payload is the from NS retrieved JSON-String which should be handled by all
 * subscriber.
 */

class EventNsFood(val mode: Int, val payload: Bundle) : Event() {
    companion object {
        val ADD = 0
        val UPDATE = 1
        val REMOVE = 2
    }
}

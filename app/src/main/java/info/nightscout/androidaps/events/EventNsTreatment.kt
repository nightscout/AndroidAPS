package info.nightscout.androidaps.events

import org.json.JSONObject


/**
 * Event which is published with data fetched from NightScout specific for the
 * Treatment-class.
 *
 *
 * Payload is the from NS retrieved JSON-String which should be handled by all
 * subscriber.
 */

class EventNsTreatment(val mode: Int, val payload: JSONObject) : Event() {
    companion object {
        val ADD = 0
        val UPDATE = 1
        val REMOVE = 2
    }
}

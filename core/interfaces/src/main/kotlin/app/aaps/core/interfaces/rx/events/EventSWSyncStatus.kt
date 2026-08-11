package app.aaps.core.interfaces.rx.events

import app.aaps.core.keys.interfaces.TextRef

/**
 * Fired to update the setup wizard with the sync status.
 *
 * @param status The sync status message.
 */
class EventSWSyncStatus(val status: String) : EventStatus() {

    override fun getStatus(): TextRef = TextRef.Literal(status)
}
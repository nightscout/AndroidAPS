package app.aaps.core.interfaces.rx.events

import app.aaps.core.keys.interfaces.TextRef

/**
 * Fired to update the setup wizard with the RileyLink status.
 *
 * @param status The RileyLink status message.
 */
class EventSWRLStatus(val status: String) : EventStatus() {

    override fun getStatus(): TextRef = TextRef.Literal(status)
}
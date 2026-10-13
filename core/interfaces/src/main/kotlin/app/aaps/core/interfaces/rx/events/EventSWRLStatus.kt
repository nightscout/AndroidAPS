package app.aaps.core.interfaces.rx.events

import android.content.Context

/**
 * Fired to update the setup wizard with the RileyLink status.
 *
 * @param status The RileyLink status message.
 */
class EventSWRLStatus(val status: String) : EventStatus() {

    override fun getStatus(context: Context): String = status
}
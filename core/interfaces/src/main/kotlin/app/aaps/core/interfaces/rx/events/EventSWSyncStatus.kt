package app.aaps.core.interfaces.rx.events

import android.content.Context

/**
 * Fired to update the setup wizard with the sync status.
 *
 * @param status The sync status message.
 */
class EventSWSyncStatus(val status: String) : EventStatus() {

    override fun getStatus(context: Context): String = status
}
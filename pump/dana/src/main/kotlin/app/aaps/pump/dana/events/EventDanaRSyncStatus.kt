package app.aaps.pump.dana.events

import app.aaps.core.interfaces.rx.events.Event

/**
 * Fired to update the Dana-RS sync status.
 *
 * @param message The sync status message.
 */
class EventDanaRSyncStatus(var message: String) : Event()
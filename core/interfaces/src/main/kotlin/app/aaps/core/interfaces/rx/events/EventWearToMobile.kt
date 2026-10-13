package app.aaps.core.interfaces.rx.events

import app.aaps.core.interfaces.rx.weardata.EventData

/**
 * Fired to send data from the wearable to the mobile device.
 *
 * @param payload The data to send.
 */
class EventWearToMobile(val payload: EventData) : Event()
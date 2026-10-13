package app.aaps.core.interfaces.rx.events

import app.aaps.core.interfaces.rx.weardata.EventData

/**
 * Fired to send data from the mobile device to the wearable.
 * In Wear app you can directly listen to EventData
 *
 * @param payload The data to send.
 */
class EventMobileToWear(val payload: EventData) : Event()
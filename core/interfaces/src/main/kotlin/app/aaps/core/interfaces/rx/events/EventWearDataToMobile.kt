package app.aaps.core.interfaces.rx.events

import app.aaps.core.interfaces.rx.weardata.EventData

/**
 * Fired to send data from the wearable to the mobile device.
 * In mobile app you can directly listen to EventData
 *
 * @param payload The data to send.
 */
class EventWearDataToMobile(val payload: EventData) : Event()
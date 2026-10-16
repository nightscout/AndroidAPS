package app.aaps.core.interfaces.rx.events

import app.aaps.core.interfaces.rx.weardata.CwfData
import app.aaps.core.interfaces.rx.weardata.EventData

/**
 * Fired to update the GUI on the wearable device.
 *
 * @param customWatchfaceData Data for a custom watchface, or null if not applicable.
 * @param exportFile If true, the data should be exported to a file.
 * @param watchFacePushStatus What the watch reported about Watch Face Push, or null if this event
 *   carries something else.
 */
data class EventWearUpdateGui(
    val customWatchfaceData: CwfData? = null,
    val exportFile: Boolean = false,
    val watchFacePushStatus: EventData.WatchFacePushStatus? = null
) : Event()
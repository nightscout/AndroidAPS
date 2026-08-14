package app.aaps.core.interfaces.rx.events

import app.aaps.core.interfaces.rx.weardata.CwfData

/**
 * Fired to update the GUI on the wearable device.
 *
 * @param customWatchfaceData Data for a custom watchface, or null if not applicable.
 * @param exportFile If true, the data should be exported to a file.
 */
class EventWearUpdateGui(val customWatchfaceData: CwfData? = null, val exportFile: Boolean = false) : Event()
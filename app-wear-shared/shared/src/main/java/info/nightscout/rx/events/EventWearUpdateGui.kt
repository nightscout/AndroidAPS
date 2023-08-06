package info.nightscout.rx.events

import info.nightscout.rx.weardata.CustomWatchfaceData

class EventWearUpdateGui(val customWatchfaceData: CustomWatchfaceData? = null, val exportFile: Boolean = false) : Event()
package info.nightscout.rx.events

import info.nightscout.rx.weardata.CwfData

class EventWearUpdateGui(val customWatchfaceData: CwfData? = null, val exportFile: Boolean = false) : Event()
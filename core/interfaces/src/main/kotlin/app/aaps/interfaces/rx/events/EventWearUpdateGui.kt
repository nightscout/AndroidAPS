package app.aaps.interfaces.rx.events

import app.aaps.interfaces.rx.weardata.CwfData

class EventWearUpdateGui(val customWatchfaceData: CwfData? = null, val exportFile: Boolean = false) : Event()
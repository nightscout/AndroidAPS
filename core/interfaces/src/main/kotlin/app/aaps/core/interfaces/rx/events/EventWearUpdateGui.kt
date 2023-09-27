package app.aaps.core.interfaces.rx.events

import app.aaps.core.interfaces.rx.weardata.CwfData

class EventWearUpdateGui(val customWatchfaceData: CwfData? = null, val exportFile: Boolean = false) : Event()
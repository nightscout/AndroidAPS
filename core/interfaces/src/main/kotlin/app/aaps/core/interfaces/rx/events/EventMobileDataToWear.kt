package app.aaps.core.interfaces.rx.events

import app.aaps.core.interfaces.rx.weardata.EventData

class EventMobileDataToWear(val payload: EventData.ActionSetCustomWatchface) : Event()
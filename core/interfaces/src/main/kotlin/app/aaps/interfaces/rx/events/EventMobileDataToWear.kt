package app.aaps.interfaces.rx.events

import app.aaps.interfaces.rx.weardata.EventData

class EventMobileDataToWear(val payload: EventData.ActionSetCustomWatchface) : Event()
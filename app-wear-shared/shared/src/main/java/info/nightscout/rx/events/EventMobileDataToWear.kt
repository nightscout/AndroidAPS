package info.nightscout.rx.events

import info.nightscout.rx.weardata.EventData

class EventMobileDataToWear(val payload: EventData.ActionSetCustomWatchface) : Event()
package app.aaps.core.interfaces.rx.events

import app.aaps.core.interfaces.rx.weardata.EventData

class EventWearCwfExported(val payload: EventData.ActionSetCustomWatchface) : Event()
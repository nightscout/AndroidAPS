package app.aaps.interfaces.rx.events

import app.aaps.interfaces.rx.weardata.EventData

class EventWearToMobile(val payload: EventData) : Event()
package app.aaps.interfaces.rx.events

import app.aaps.interfaces.rx.weardata.EventData

class EventMobileToWear(val payload: EventData) : Event()
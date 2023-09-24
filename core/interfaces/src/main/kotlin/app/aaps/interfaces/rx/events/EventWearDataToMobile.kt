package app.aaps.interfaces.rx.events

import app.aaps.interfaces.rx.weardata.EventData

class EventWearDataToMobile(val payload: EventData) : Event()
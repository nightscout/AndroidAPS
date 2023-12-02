package app.aaps.core.interfaces.rx.events

import app.aaps.core.interfaces.rx.weardata.EventData

class EventWearDataToMobile(val payload: EventData) : Event()
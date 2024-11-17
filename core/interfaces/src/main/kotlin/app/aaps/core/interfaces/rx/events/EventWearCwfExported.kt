package app.aaps.core.interfaces.rx.events

import app.aaps.core.interfaces.rx.weardata.EventData
import kotlinx.serialization.InternalSerializationApi

@InternalSerializationApi
class EventWearCwfExported(val payload: EventData.ActionSetCustomWatchface) : Event()
package app.aaps.plugins.automation.events

import app.aaps.core.interfaces.rx.events.Event

class EventPlaceSelected(
    val latitude: Double,
    val longitude: Double
) : Event()

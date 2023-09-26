package app.aaps.plugins.automation.events

import android.location.Location
import app.aaps.core.interfaces.rx.events.Event

class EventLocationChange(var location: Location) : Event()

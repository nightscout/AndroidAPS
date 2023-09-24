package info.nightscout.automation.events

import android.location.Location
import app.aaps.interfaces.rx.events.Event

class EventLocationChange(var location: Location) : Event()

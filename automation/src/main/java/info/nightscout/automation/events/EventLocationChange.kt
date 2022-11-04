package info.nightscout.automation.events

import android.location.Location
import info.nightscout.androidaps.events.Event

class EventLocationChange(var location: Location) : Event()

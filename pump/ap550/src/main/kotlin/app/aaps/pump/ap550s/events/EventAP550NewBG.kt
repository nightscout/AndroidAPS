package app.aaps.pump.ap550s.events

import app.aaps.core.interfaces.rx.events.Event
import app.aaps.database.entities.GlucoseValue

// import info.nightscout.androidaps.events.Event

class EventAP550NewBG(val bgReading: GlucoseValue?) : Event() {
}
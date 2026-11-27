package app.aaps.pump.ap550s.events

import app.aaps.core.interfaces.rx.events.Event

// Read the reset and vent history, starting at the given record index
class EventGetResetVentRecord(val recordIndex: Int) : Event()
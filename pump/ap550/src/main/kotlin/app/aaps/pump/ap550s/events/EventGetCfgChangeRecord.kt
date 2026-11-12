package app.aaps.pump.ap550s.events

import app.aaps.core.interfaces.rx.events.Event

// Read the settings change history, starting at the given record index
class EventGetCfgChangeRecord(val recordIndex: Int) : Event()
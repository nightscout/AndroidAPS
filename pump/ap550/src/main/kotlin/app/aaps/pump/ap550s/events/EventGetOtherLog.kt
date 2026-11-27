package app.aaps.pump.ap550s.events

import app.aaps.core.interfaces.rx.events.Event

// logType: 2 = temporary basal log, 0 = other logs
class EventGetOtherLog(val logType: Int) : Event()
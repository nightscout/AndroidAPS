package app.aaps.pump.equil.events

import app.aaps.core.interfaces.rx.events.Event

data class EventEquilAlarm(var tips: String) : Event()
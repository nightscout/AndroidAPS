package app.aaps.pump.ap550s.events

import app.aaps.core.interfaces.rx.events.Event

class EventBolusStart(val bolusStart: Boolean) : Event()
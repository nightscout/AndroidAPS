package app.aaps.pump.equil.events

import app.aaps.core.interfaces.rx.events.Event

/** The pump changed from SUSPEND or STOP back to RUN. */
class EventEquilResumed : Event()

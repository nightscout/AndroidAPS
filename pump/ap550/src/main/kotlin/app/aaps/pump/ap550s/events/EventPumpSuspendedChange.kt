package app.aaps.pump.ap550s.events

import app.aaps.core.interfaces.rx.events.EventLoop

class EventPumpSuspendedChange(val isSuspended: Boolean) : EventLoop()
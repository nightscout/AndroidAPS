package app.aaps.pump.carelevo.event

import app.aaps.core.interfaces.rx.events.Event
import app.aaps.pump.carelevo.data.protocol.command.CarelevoProtocolCommand

class EventCarelevoAlarm(var alarmCodes: Set<CarelevoProtocolCommand>, var isFirst: Boolean = false) : Event()

class EventForceStopConnecting : Event()
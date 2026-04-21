package info.nightscout.androidaps.plugins.pump.carelevo.event

import app.aaps.core.interfaces.rx.events.Event
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.command.CarelevoProtocolCommand

class EventCarelevoAlarm(var alarmCodes: Set<CarelevoProtocolCommand>, var isFirst: Boolean = false) : Event()

class EventForceStopConnecting: Event()
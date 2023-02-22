package info.nightscout.aaps.pump.common.events

import info.nightscout.rx.events.Event
import info.nightscout.aaps.pump.common.defs.PumpDriverState

class EventPumpDriverStateChanged(var driverStatus: PumpDriverState) : Event()

package info.nightscout.pump.common.events

import app.aaps.core.interfaces.rx.events.Event
import info.nightscout.pump.common.defs.PumpUpdateFragmentType

class EventPumpFragmentValuesChanged : Event {

    var updateType: PumpUpdateFragmentType = PumpUpdateFragmentType.None

    constructor(updateType: PumpUpdateFragmentType) {
        this.updateType = updateType
    }

}
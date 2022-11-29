package info.nightscout.pump.common.events

import info.nightscout.pump.common.defs.PumpUpdateFragmentType
import info.nightscout.rx.events.Event

class EventPumpFragmentValuesChanged : Event {

    var updateType: PumpUpdateFragmentType = PumpUpdateFragmentType.None

    constructor(updateType: PumpUpdateFragmentType) {
        this.updateType = updateType
    }

}
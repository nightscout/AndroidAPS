package info.nightscout.androidaps.plugins.pump.common.events

import info.nightscout.androidaps.events.Event
import info.nightscout.androidaps.plugins.pump.common.defs.PumpUpdateFragmentType

class EventPumpFragmentValuesChanged : Event {

    var updateType: PumpUpdateFragmentType = PumpUpdateFragmentType.None

    constructor(updateType: PumpUpdateFragmentType) {
        this.updateType = updateType
    }

}
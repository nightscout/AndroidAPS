package info.nightscout.androidaps.plugins.pump.common.events

import info.nightscout.androidaps.events.Event
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState

open class EventRileyLinkDeviceStatusChange : Event {

    var rileyLinkServiceState: RileyLinkServiceState? = null
    var rileyLinkError: RileyLinkError? = null

    var pumpDeviceState: PumpDeviceState? = null
    var errorDescription: String? = null

    constructor() {

    }


    constructor(rileyLinkServiceState: RileyLinkServiceState?, rileyLinkError: RileyLinkError?) {
        this.rileyLinkServiceState = rileyLinkServiceState
        this.rileyLinkError = rileyLinkError
    }

    constructor(pumpDeviceState: PumpDeviceState?) {
        this.pumpDeviceState = pumpDeviceState
    }

    constructor(pumpDeviceState: PumpDeviceState?, errorDescription: String?) {
        this.pumpDeviceState = pumpDeviceState
        this.errorDescription = errorDescription
    }
}

package info.nightscout.androidaps.plugins.pump.medtronic.events

import info.nightscout.androidaps.events.Event
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState
import info.nightscout.androidaps.plugins.pump.medtronic.defs.PumpDeviceState

class EventMedtronicDeviceStatusChange : Event {

    private var rileyLinkServiceState: RileyLinkServiceState? = null
    private var rileyLinkError: RileyLinkError? = null

    private var pumpDeviceState: PumpDeviceState? = null
    private var errorDescription: String? = null


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

package info.nightscout.androidaps.plugins.pump.common.events

import info.nightscout.androidaps.events.Event
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState

class EventRileyLinkDeviceStatusChange : Event {

    private var rileyLinkServiceState: info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState? = null
    private var rileyLinkError: info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError? = null

    private var pumpDeviceState: info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState? = null
    private var errorDescription: String? = null


    constructor(rileyLinkServiceState: info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState?, rileyLinkError: info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError?) {
        this.rileyLinkServiceState = rileyLinkServiceState
        this.rileyLinkError = rileyLinkError
    }

    constructor(pumpDeviceState: info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState?) {
        this.pumpDeviceState = pumpDeviceState
    }

    constructor(pumpDeviceState: info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState?, errorDescription: String?) {
        this.pumpDeviceState = pumpDeviceState
        this.errorDescription = errorDescription
    }
}

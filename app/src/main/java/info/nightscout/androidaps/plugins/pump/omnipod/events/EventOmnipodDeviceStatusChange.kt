package info.nightscout.androidaps.plugins.pump.omnipod.events

import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState
import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommandType
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodDeviceState
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager

/**
 * Created by andy on 4.8.2019
 */
// FIXME Remove in favor of EventRileyLinkDeviceStatusChange and EventOmnipodPumpValuesChanged
class EventOmnipodDeviceStatusChange : EventRileyLinkDeviceStatusChange {

    var podStateManager: PodStateManager? = null
    var podDeviceState: PodDeviceState? = null

    @JvmOverloads
    constructor(rileyLinkServiceState: RileyLinkServiceState?, rileyLinkError: RileyLinkError? = null) : super(rileyLinkServiceState, rileyLinkError) {
    }

    constructor(commandType: OmnipodCommandType?) {
    }

    constructor(podStateManager: PodStateManager?) {
        this.podStateManager = podStateManager
    }

    constructor(errorDescription: String?) {
        this.errorDescription = errorDescription
    }

    constructor(podDeviceState: PodDeviceState?, errorDescription: String?) {
        this.podDeviceState = podDeviceState
        this.errorDescription = errorDescription
    }

    constructor(pumpDeviceState: PumpDeviceState?) {
        this.pumpDeviceState = pumpDeviceState
    }

    constructor(pumpDeviceState: PumpDeviceState?, errorDescription: String?) {
        this.pumpDeviceState = pumpDeviceState
        this.errorDescription = errorDescription
    }

    override fun toString(): String {
        return ("EventOmnipodDeviceStatusChange [" //
            + "rileyLinkServiceState=" + rileyLinkServiceState
            + ", rileyLinkError=" + rileyLinkError //
            + ", podStateManager=" + podStateManager //
            + ", podDeviceState=" + podDeviceState + "]")
    }
}
package info.nightscout.androidaps.plugins.pump.omnipod.events

import info.nightscout.androidaps.events.Event
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState
import info.nightscout.androidaps.plugins.pump.medtronic.defs.PumpDeviceState
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommandType
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodDeviceState
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager

/**
 * Created by andy on 4.8.2019
 */
// FIXME Rename this class to EventRileyLinkStatusChanged and use EventOmnipodPumpValuesChanged
//  for changes in Pod status
class EventOmnipodDeviceStatusChange : Event {

    var rileyLinkServiceState: RileyLinkServiceState? = null
    var rileyLinkError: RileyLinkError? = null
    var podStateManager: PodStateManager? = null
    var errorDescription: String? = null
    var podDeviceState: PodDeviceState? = null
    var pumpDeviceState: PumpDeviceState? = null


    @JvmOverloads
    constructor(rileyLinkServiceState: RileyLinkServiceState?, rileyLinkError: RileyLinkError? = null) {
        this.rileyLinkServiceState = rileyLinkServiceState
        this.rileyLinkError = rileyLinkError
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
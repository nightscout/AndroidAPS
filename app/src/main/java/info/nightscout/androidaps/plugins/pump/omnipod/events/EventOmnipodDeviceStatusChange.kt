package info.nightscout.androidaps.plugins.pump.omnipod.events

import info.nightscout.androidaps.events.Event
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodDeviceState
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState

/**
 * Created by andy on 4.8.2019
 */
class EventOmnipodDeviceStatusChange : Event {

    var rileyLinkServiceState: RileyLinkServiceState? = null
    var rileyLinkError: RileyLinkError? = null
    var podSessionState: PodSessionState? = null
    var errorDescription: String? = null
    var podDeviceState: PodDeviceState? = null

    @JvmOverloads
    constructor(rileyLinkServiceState: RileyLinkServiceState?, rileyLinkError: RileyLinkError? = null) {
        this.rileyLinkServiceState = rileyLinkServiceState
        this.rileyLinkError = rileyLinkError
    }

    constructor(podSessionState: PodSessionState?) {
        this.podSessionState = podSessionState
    }

    constructor(errorDescription: String?) {
        this.errorDescription = errorDescription
    }

    constructor(podDeviceState: PodDeviceState?, errorDescription: String?) {
        this.podDeviceState = podDeviceState
        this.errorDescription = errorDescription
    }

    override fun toString(): String {
        return ("EventOmnipodDeviceStatusChange [" //
            + "rileyLinkServiceState=" + rileyLinkServiceState
            + ", rileyLinkError=" + rileyLinkError //
            + ", podSessionState=" + podSessionState //
            + ", podDeviceState=" + podDeviceState + "]")
    }
}
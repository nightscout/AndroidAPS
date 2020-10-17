package info.nightscout.androidaps.plugins.pump.common.events

import info.nightscout.androidaps.events.EventStatus
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import info.nightscout.androidaps.utils.resources.ResourceHelper

open class EventRileyLinkDeviceStatusChange : EventStatus {

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

    override fun getStatus(resourceHelper: ResourceHelper): String {
        val rileyLinkServiceState = this.rileyLinkServiceState ?: return ""
        val resourceId = rileyLinkServiceState.resourceId
        val rileyLinkError = this.rileyLinkError

        if (rileyLinkServiceState.isError && rileyLinkError != null) {
            return resourceHelper.gs(rileyLinkError.getResourceId(RileyLinkTargetDevice.Omnipod))
        }

        return resourceHelper.gs(resourceId)
    }
}

package info.nightscout.androidaps.plugins.pump.common.events

import info.nightscout.androidaps.events.EventStatus
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import info.nightscout.androidaps.utils.resources.ResourceHelper

open class EventRileyLinkDeviceStatusChange : EventStatus {

    var rileyLinkTargetDevice: RileyLinkTargetDevice? = null
    var rileyLinkServiceState: RileyLinkServiceState? = null
    var rileyLinkError: RileyLinkError? = null

    var pumpDeviceState: PumpDeviceState? = null
    var errorDescription: String? = null

    constructor()

    constructor(rileyLinkTargetDevice: RileyLinkTargetDevice, rileyLinkServiceState: RileyLinkServiceState?, rileyLinkError: RileyLinkError?) {
        this.rileyLinkTargetDevice = rileyLinkTargetDevice
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
            val rileyLinkTargetDevice = this.rileyLinkTargetDevice ?: return ""
            return resourceHelper.gs(rileyLinkError.getResourceId(rileyLinkTargetDevice))
        }

        return resourceHelper.gs(resourceId)
    }
}

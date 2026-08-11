package app.aaps.pump.common.events

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.interfaces.pump.defs.PumpDeviceState
import app.aaps.core.interfaces.rx.events.EventStatus
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkError
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkServiceState
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkTargetDevice

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

    override fun getStatus(): TextRef {
        val rileyLinkServiceState = this.rileyLinkServiceState ?: return TextRef.Literal("")
        val resourceId = rileyLinkServiceState.resourceId
        val rileyLinkError = this.rileyLinkError

        if (rileyLinkServiceState.isError() && rileyLinkError != null) {
            val rileyLinkTargetDevice = this.rileyLinkTargetDevice ?: return TextRef.Literal("")
            return TextRef.AndroidRes(rileyLinkError.getResourceId(rileyLinkTargetDevice))
        }

        return TextRef.AndroidRes(resourceId)
    }
}

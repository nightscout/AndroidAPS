package app.aaps.pump.common.hw.rileylink.data

import app.aaps.core.interfaces.pump.defs.PumpDeviceState
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.common.extensions.stringResource
import app.aaps.pump.common.hw.rileylink.data.RLHistoryItem.RLHistoryItemSource
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkError
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkServiceState
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import org.joda.time.LocalDateTime

/**
 * Created by andy on 5/19/18.
 */
open class RLHistoryItem(val dateTime: LocalDateTime = LocalDateTime(), val source: RLHistoryItemSource, @Suppress("unused") val targetDevice: RileyLinkTargetDevice) {

    var serviceState: RileyLinkServiceState = RileyLinkServiceState.NotStarted
    var errorCode: RileyLinkError? = null
    var pumpDeviceState: PumpDeviceState = PumpDeviceState.NeverContacted

    constructor(serviceState: RileyLinkServiceState, errorCode: RileyLinkError?, targetDevice: RileyLinkTargetDevice) : this(source = RLHistoryItemSource.RileyLink, targetDevice = targetDevice) {
        this.serviceState = serviceState
        this.errorCode = errorCode
    }

    constructor(pumpDeviceState: PumpDeviceState, targetDevice: RileyLinkTargetDevice) : this(source = RLHistoryItemSource.MedtronicPump, targetDevice = targetDevice) {
        this.pumpDeviceState = pumpDeviceState
    }

    open fun getDescription(rh: ResourceHelper): String {
        return when (this.source) {
            RLHistoryItemSource.RileyLink     -> "State: " + rh.gs(serviceState.resourceId) +
                if (this.errorCode == null) "" else ", Error Code: $errorCode"

            RLHistoryItemSource.MedtronicPump -> rh.gs(pumpDeviceState.stringResource())
            else                              -> "Unknown Description"
        }
    }

    enum class RLHistoryItemSource(val desc: String) {
        RileyLink("RileyLink"),
        MedtronicPump("Medtronic"),
        MedtronicCommand("Medtronic"),
        OmnipodCommand("Omnipod");
    }

    class Comparator : java.util.Comparator<RLHistoryItem> {

        override fun compare(o1: RLHistoryItem, o2: RLHistoryItem): Int {
            return o2.dateTime.compareTo(o1.dateTime)
        }
    }
}

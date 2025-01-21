package app.aaps.pump.medtronic.data.dto

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.common.hw.rileylink.data.RLHistoryItem
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import app.aaps.pump.medtronic.defs.MedtronicCommandType
import org.joda.time.LocalDateTime

class RLHistoryItemMedtronic(private val medtronicCommandType: MedtronicCommandType) :
    RLHistoryItem(LocalDateTime(), RLHistoryItemSource.MedtronicCommand, RileyLinkTargetDevice.MedtronicPump) {

    override fun getDescription(rh: ResourceHelper): String =
        if (RLHistoryItemSource.MedtronicCommand == source) medtronicCommandType.name
        else super.getDescription(rh)
}
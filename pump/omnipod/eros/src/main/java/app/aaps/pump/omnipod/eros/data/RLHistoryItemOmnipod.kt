package app.aaps.pump.omnipod.eros.data

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.common.hw.rileylink.data.RLHistoryItem
import app.aaps.pump.common.hw.rileylink.data.RLHistoryItem.RLHistoryItemSource
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import app.aaps.pump.omnipod.common.definition.OmnipodCommandType
import org.joda.time.LocalDateTime

class RLHistoryItemOmnipod(private val omnipodCommandType: OmnipodCommandType) : RLHistoryItem(LocalDateTime(), RLHistoryItemSource.OmnipodCommand, RileyLinkTargetDevice.Omnipod) {

    override fun getDescription(rh: ResourceHelper): String {
        if (RLHistoryItemSource.OmnipodCommand == source) {
            return rh.gs(omnipodCommandType.resourceId)
        }
        return super.getDescription(rh)
    }
}

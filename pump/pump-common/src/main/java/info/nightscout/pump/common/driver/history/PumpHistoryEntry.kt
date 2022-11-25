package info.nightscout.pump.common.driver.history

import info.nightscout.pump.common.defs.PumpHistoryEntryGroup
import info.nightscout.shared.interfaces.ResourceHelper

interface PumpHistoryEntry {

    fun prepareEntryData(resourceHelper: ResourceHelper, pumpDataConverter: PumpDataConverter)

    fun getEntryDateTime(): String

    fun getEntryType(): String

    fun getEntryValue(): String

    fun getEntryTypeGroup(): PumpHistoryEntryGroup

}
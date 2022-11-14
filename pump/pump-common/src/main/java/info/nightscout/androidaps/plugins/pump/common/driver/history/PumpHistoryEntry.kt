package info.nightscout.androidaps.plugins.pump.common.driver.history

import info.nightscout.androidaps.plugins.pump.common.defs.PumpHistoryEntryGroup
import info.nightscout.shared.interfaces.ResourceHelper

interface PumpHistoryEntry {

    fun prepareEntryData(resourceHelper: ResourceHelper, pumpDataConverter: PumpDataConverter)

    fun getEntryDateTime(): String

    fun getEntryType(): String

    fun getEntryValue(): String

    fun getEntryTypeGroup(): PumpHistoryEntryGroup

}
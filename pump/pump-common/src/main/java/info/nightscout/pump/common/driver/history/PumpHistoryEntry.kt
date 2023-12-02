package info.nightscout.pump.common.driver.history

import app.aaps.core.interfaces.resources.ResourceHelper
import info.nightscout.pump.common.defs.PumpHistoryEntryGroup

interface PumpHistoryEntry {

    fun prepareEntryData(resourceHelper: ResourceHelper, pumpDataConverter: PumpDataConverter)

    fun getEntryDateTime(): String

    fun getEntryType(): String

    fun getEntryValue(): String

    fun getEntryTypeGroup(): PumpHistoryEntryGroup

}
package app.aaps.pump.common.driver.history

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.common.defs.PumpHistoryEntryGroup

interface PumpHistoryEntry {

    fun prepareEntryData(resourceHelper: ResourceHelper, pumpDataConverter: PumpDataConverter)

    fun getEntryDateTime(): String

    fun getEntryType(): String

    fun getEntryValue(): String

    fun getEntryTypeGroup(): PumpHistoryEntryGroup

}
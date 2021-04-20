package info.nightscout.androidaps.plugins.pump.common.data

import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType

data class PumpDbEntry(var temporaryId: Long,
                       var pumpType: PumpType,
                       var serialNumber: String,
                       var detailedBolusInfo: DetailedBolusInfo)
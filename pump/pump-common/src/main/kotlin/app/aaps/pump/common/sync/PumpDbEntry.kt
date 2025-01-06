package app.aaps.pump.common.sync

import app.aaps.core.data.model.BS
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpSync

interface PumpDbEntry {

    var temporaryId: Long
    var date: Long
    var pumpType: PumpType
    var serialNumber: String
    var pumpId: Long?
}

data class PumpDbEntryBolus(
    override var temporaryId: Long,
    override var date: Long,
    override var pumpType: PumpType,
    override var serialNumber: String,
    override var pumpId: Long? = null,
    var insulin: Double,
    var carbs: Double,
    var bolusType: BS.Type
) : PumpDbEntry {

    constructor(
        temporaryId: Long,
        date: Long,
        pumpType: PumpType,
        serialNumber: String,
        detailedBolusInfo: DetailedBolusInfo
    ) : this(
        temporaryId, date, pumpType, serialNumber, null,
        detailedBolusInfo.insulin,
        detailedBolusInfo.carbs,
        detailedBolusInfo.bolusType
    )

}

data class PumpDbEntryCarbs(
    var date: Long,
    var carbs: Double,
    var pumpType: PumpType,
    var serialNumber: String,
    var pumpId: Long? = null
) {

    constructor(
        detailedBolusInfo: DetailedBolusInfo,
        creator: PumpSyncEntriesCreator
    ) : this(
        detailedBolusInfo.timestamp,
        detailedBolusInfo.carbs,
        creator.model(),
        creator.serialNumber()
    )
}

data class PumpDbEntryTBR(
    override var temporaryId: Long,
    override var date: Long,
    override var pumpType: PumpType,
    override var serialNumber: String,
    override var pumpId: Long? = null,
    var rate: Double,
    var isAbsolute: Boolean,
    var durationInSeconds: Int,
    var tbrType: PumpSync.TemporaryBasalType
) : PumpDbEntry {

    constructor(
        rate: Double,
        isAbsolute: Boolean,
        durationInSeconds: Int,
        tbrType: PumpSync.TemporaryBasalType
    ) : this(
        0, 0, PumpType.GENERIC_AAPS, "", null,
        rate, isAbsolute, durationInSeconds, tbrType
    )

    constructor(
        temporaryId: Long,
        date: Long,
        pumpType: PumpType,
        serialNumber: String,
        entry: PumpDbEntryTBR,
        pumpId: Long?
    ) : this(
        temporaryId, date, pumpType, serialNumber, pumpId,
        entry.rate, entry.isAbsolute, entry.durationInSeconds, entry.tbrType
    )

}

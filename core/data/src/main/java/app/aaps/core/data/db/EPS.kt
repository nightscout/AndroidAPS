package app.aaps.core.data.db

import app.aaps.core.data.db.data.Block
import app.aaps.core.data.db.data.TargetBlock
import java.util.TimeZone

data class EPS(
    var id: Long = 0,
    var version: Int = 0,
    var dateCreated: Long = -1,
    var isValid: Boolean = true,
    var referenceId: Long? = null,
    var ids: IDs = IDs(),
    var timestamp: Long,
    var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    var basalBlocks: List<Block>,
    var isfBlocks: List<Block>,
    var icBlocks: List<Block>,
    var targetBlocks: List<TargetBlock>,
    var glucoseUnit: GlucoseUnit,
    // Previous values from PS request
    var originalProfileName: String,
    var originalCustomizedName: String,
    var originalTimeshift: Long,  // [milliseconds]
    var originalPercentage: Int, // 1 ~ XXX [%]
    var originalDuration: Long, // [milliseconds]
    var originalEnd: Long, // not used (calculated from duration)
    var iCfg: ICfg
)  {

    fun contentEqualsTo(other: EPS): Boolean =
        isValid == other.isValid &&
            timestamp == other.timestamp &&
            utcOffset == other.utcOffset &&
            basalBlocks == other.basalBlocks &&
            isfBlocks == other.isfBlocks &&
            icBlocks == other.icBlocks &&
            targetBlocks == other.targetBlocks &&
            glucoseUnit == other.glucoseUnit &&
            originalProfileName == other.originalProfileName &&
            originalCustomizedName == other.originalCustomizedName &&
            originalTimeshift == other.originalTimeshift &&
            originalPercentage == other.originalPercentage &&
            originalDuration == other.originalDuration &&
            originalEnd == other.originalEnd

    fun onlyNsIdAdded(previous: EPS): Boolean =
        previous.id != id &&
            contentEqualsTo(previous) &&
            previous.ids.nightscoutId == null &&
            ids.nightscoutId != null

    companion object
}
package app.aaps.core.data.model

import app.aaps.core.data.model.data.Block
import app.aaps.core.data.model.data.TargetBlock
import java.util.TimeZone

data class EPS(
    override var id: Long = 0,
    override var version: Int = 0,
    override var dateCreated: Long = -1,
    override var isValid: Boolean = true,
    override var referenceId: Long? = null,
    override var ids: IDs = IDs(),
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
    /** Applied insulin configuration */
    var iCfg: ICfg
) : HasIDs {

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
            originalEnd == other.originalEnd &&
            iCfg == other.iCfg

    fun onlyNsIdAdded(previous: EPS): Boolean =
        previous.id != id &&
            contentEqualsTo(previous) &&
            previous.ids.nightscoutId == null &&
            ids.nightscoutId != null

    companion object
}
package app.aaps.core.data.db

import app.aaps.core.data.db.data.Block
import app.aaps.core.data.db.data.TargetBlock
import java.util.TimeZone

data class PS(
    var id: Long = 0,
    var version: Int = 0,
    var dateCreated: Long = -1,
    var isValid: Boolean = true,
    var referenceId: Long? = null,
    override var ids: IDs = IDs(),
    var timestamp: Long,
    var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    var basalBlocks: List<Block>,
    var isfBlocks: List<Block>,
    var icBlocks: List<Block>,
    var targetBlocks: List<TargetBlock>,
    var glucoseUnit: GlucoseUnit,
    var profileName: String,
    var timeshift: Long,  // [milliseconds]
    var percentage: Int, // 1 ~ XXX [%]
    var duration: Long, // [milliseconds]
    var iCfg: ICfg
) : HasIDs {

    fun copy(): PS =
        PS(
            isValid = isValid,
            timestamp = timestamp,
            utcOffset = utcOffset,
            basalBlocks = basalBlocks,
            isfBlocks = isfBlocks,
            icBlocks = icBlocks,
            targetBlocks = targetBlocks,
            glucoseUnit = glucoseUnit,
            profileName = profileName,
            timeshift = timeshift,
            percentage = percentage,
            duration = duration,
            iCfg = iCfg,
            ids = ids
        )

    fun contentEqualsTo(other: PS): Boolean =
        isValid == other.isValid &&
            timestamp == other.timestamp &&
            utcOffset == other.utcOffset &&
            basalBlocks == other.basalBlocks &&
            isfBlocks == other.isfBlocks &&
            icBlocks == other.icBlocks &&
            targetBlocks == other.targetBlocks &&
            glucoseUnit == other.glucoseUnit &&
            profileName == other.profileName &&
            timeshift == other.timeshift &&
            percentage == other.percentage &&
            duration == other.duration

    fun onlyNsIdAdded(previous: PS): Boolean =
        previous.id != id &&
            contentEqualsTo(previous) &&
            previous.ids.nightscoutId == null &&
            ids.nightscoutId != null

    val end
        get() = timestamp + duration

    companion object
}
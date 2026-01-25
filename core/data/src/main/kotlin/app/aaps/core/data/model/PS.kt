package app.aaps.core.data.model

import app.aaps.core.data.model.data.Block
import app.aaps.core.data.model.data.TargetBlock
import java.util.TimeZone

data class PS(
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
    var profileName: String,
    var timeshift: Long,  // [milliseconds]
    var percentage: Int, // 1 ~ XXX [%]
    /** Duration in milliseconds */
    var duration: Long,
    /** Applied insulin configuration */
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
            duration == other.duration &&
            iCfg == other.iCfg

    fun onlyNsIdAdded(previous: PS): Boolean =
        previous.id != id &&
            contentEqualsTo(previous) &&
            previous.ids.nightscoutId == null &&
            ids.nightscoutId != null

    val end
        get() = timestamp + duration

    companion object
}
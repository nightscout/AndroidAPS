package app.aaps.core.data.model

import java.util.TimeZone

/** Steps count values measured by a user smart watch or the like. */
data class SC(
    override var id: Long = 0,
    /** Duration milliseconds */
    var duration: Long,
    /** Milliseconds since the epoch. End of the sampling period, i.e. the value is
     *  sampled from timestamp-duration to timestamp. */
    var timestamp: Long,
    var steps5min: Int,
    var steps10min: Int,
    var steps15min: Int,
    var steps30min: Int,
    var steps60min: Int,
    var steps180min: Int,
    /** Source device that measured the steps count. */
    var device: String,
    var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    override var version: Int = 0,
    override var dateCreated: Long = -1,
    override var isValid: Boolean = true,
    override var referenceId: Long? = null,
    override var ids: IDs = IDs()
) : HasIDs {

    fun contentEqualsTo(other: SC): Boolean {
        return this === other || (
            duration == other.duration &&
                timestamp == other.timestamp &&
                steps5min == other.steps5min &&
                steps10min == other.steps10min &&
                steps15min == other.steps15min &&
                steps30min == other.steps30min &&
                steps60min == other.steps60min &&
                steps180min == other.steps180min &&
                isValid == other.isValid)
    }
}
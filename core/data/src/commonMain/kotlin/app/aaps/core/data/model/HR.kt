package app.aaps.core.data.model

import app.aaps.core.data.time.systemUtcOffsetAt

/** Heart rate values measured by a user smartwatch or the like. */
data class HR(
    var id: Long = 0,
    /** Duration milliseconds */
    var duration: Long,
    /** Milliseconds since the epoch. End of the sampling period, i.e. the value is
     *  sampled from timestamp-duration to timestamp. */
    override var timestamp: Long,
    var beatsPerMinute: Double,
    /** Source device that measured the heart rate. */
    var device: String,
    var utcOffset: Long = systemUtcOffsetAt(timestamp),
    var version: Int = 0,
    var dateCreated: Long = -1,
    var isValid: Boolean = true,
    var referenceId: Long? = null,
    var ids: IDs = IDs()
) : TimeStamped {

    fun contentEqualsTo(other: HR): Boolean {
        return this === other || (
            duration == other.duration &&
                timestamp == other.timestamp &&
                beatsPerMinute == other.beatsPerMinute &&
                isValid == other.isValid)
    }
}

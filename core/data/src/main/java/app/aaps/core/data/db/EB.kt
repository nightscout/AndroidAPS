package app.aaps.core.data.db

import java.util.TimeZone

data class EB(
    var id: Long = 0,
    var version: Int = 0,
    var dateCreated: Long = -1,
    var isValid: Boolean = true,
    var referenceId: Long? = null,
    var ids: IDs = IDs(),
    var timestamp: Long,
    var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    var duration: Long,
    var amount: Double,
    var isEmulatingTempBasal: Boolean = false
) {

    init {
        require(duration > 0)
    }

    fun contentEqualsTo(other: EB): Boolean =
        isValid == other.isValid &&
            timestamp == other.timestamp &&
            utcOffset == other.utcOffset &&
            isEmulatingTempBasal == other.isEmulatingTempBasal &&
            duration == other.duration &&
            rate == other.rate

    fun onlyNsIdAdded(previous: EB): Boolean =
        previous.id != id &&
            contentEqualsTo(previous) &&
            previous.ids.nightscoutId == null &&
            ids.nightscoutId != null

    val rate: Double // in U/h
        get() = amount * (60 * 60 * 1000.0) / duration

    val end
        get() = timestamp + duration

    companion object
}
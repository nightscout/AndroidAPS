package app.aaps.core.data.model

import java.util.TimeZone

data class CA(
    override var id: Long = 0,
    override var version: Int = 0,
    override var dateCreated: Long = -1,
    override var isValid: Boolean = true,
    override var referenceId: Long? = null,
    override var ids: IDs = IDs(),
    var timestamp: Long,
    var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    var duration: Long, // in milliseconds
    var amount: Double,
    var notes: String? = null
) : HasIDs {

    fun contentEqualsTo(other: CA): Boolean =
        isValid == other.isValid &&
            timestamp == other.timestamp &&
            utcOffset == other.utcOffset &&
            amount == other.amount &&
            notes == other.notes &&
            duration == other.duration

    fun onlyNsIdAdded(previous: CA): Boolean =
        previous.id != id &&
            contentEqualsTo(previous) &&
            previous.ids.nightscoutId == null &&
            ids.nightscoutId != null

    companion object
}
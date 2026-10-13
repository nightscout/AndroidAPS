package app.aaps.core.data.model

import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.abs

data class CA(
    override var id: Long = 0,
    override var version: Int = 0,
    override var dateCreated: Long = -1,
    override var isValid: Boolean = true,
    override var referenceId: Long? = null,
    override var ids: IDs = IDs(),
    var timestamp: Long,
    var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    /** Duration in milliseconds */
    var duration: Long,
    var amount: Double,
    var notes: String? = null
) : HasIDs {

    init {
        require(duration <= TimeUnit.HOURS.toMillis(10)) { "Duration must be less than 10 hours" } // UI and sync limit in HardLimits interface
        require(abs(amount) <= 400) { "Amount must be less than 400" } // UI and sync limit in HardLimits interface
    }

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
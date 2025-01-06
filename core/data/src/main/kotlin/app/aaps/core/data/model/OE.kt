package app.aaps.core.data.model

import java.util.TimeZone

data class OE(
    override var id: Long = 0,
    override var version: Int = 0,
    override var dateCreated: Long = -1,
    override var isValid: Boolean = true,
    override var referenceId: Long? = null,
    override var ids: IDs = IDs(),
    var timestamp: Long,
    var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    var reason: Reason,
    var duration: Long // in millis
) : HasIDs {

    fun contentEqualsTo(other: OE): Boolean =
        timestamp == other.timestamp &&
            utcOffset == other.utcOffset &&
            reason == other.reason &&
            duration == other.duration &&
            isValid == other.isValid

    fun onlyNsIdAdded(previous: OE): Boolean =
        previous.id != id &&
            contentEqualsTo(previous) &&
            previous.ids.nightscoutId == null &&
            ids.nightscoutId != null

    enum class Reason {
        DISCONNECT_PUMP,
        SUSPEND,
        DISABLE_LOOP,
        SUPER_BOLUS,
        OTHER
        ;

        companion object {

            fun fromString(reason: String?) = entries.firstOrNull { it.name == reason } ?: OTHER
        }
    }

    companion object
}
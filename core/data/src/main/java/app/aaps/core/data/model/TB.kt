package app.aaps.core.data.model

import java.util.TimeZone

data class TB(
    override var id: Long = 0,
    override var version: Int = 0,
    override var dateCreated: Long = -1,
    override var isValid: Boolean = true,
    override var referenceId: Long? = null,
    override var ids: IDs = IDs(),
    var timestamp: Long,
    var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    var type: Type,
    var isAbsolute: Boolean,
    var rate: Double,
    var duration: Long
) : HasIDs {

    init {
        require(duration > 0)
    }

    fun contentEqualsTo(other: TB): Boolean =
        isValid == other.isValid &&
            timestamp == other.timestamp &&
            utcOffset == other.utcOffset &&
            isAbsolute == other.isAbsolute &&
            type == other.type &&
            duration == other.duration &&
            rate == other.rate

    fun onlyNsIdAdded(previous: TB): Boolean =
        previous.id != id &&
            contentEqualsTo(previous) &&
            previous.ids.nightscoutId == null &&
            ids.nightscoutId != null

    enum class Type {
        NORMAL,
        EMULATED_PUMP_SUSPEND,
        PUMP_SUSPEND,
        SUPERBOLUS,
        FAKE_EXTENDED // in memory only
        ;

        companion object {

            fun fromString(name: String?) = entries.firstOrNull { it.name == name } ?: NORMAL
        }
    }

    val isInProgress: Boolean
        get() = System.currentTimeMillis() in timestamp..timestamp + duration

    val end
        get() = timestamp + duration

    companion object
}
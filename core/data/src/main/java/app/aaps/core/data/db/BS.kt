package app.aaps.core.data.db

import java.util.TimeZone

data class BS(
    var id: Long = 0,
    var version: Int = 0,
    var dateCreated: Long = -1,
    var isValid: Boolean = true,
    var referenceId: Long? = null,
    override var ids: IDs = IDs(),
    var timestamp: Long,
    var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    var amount: Double,
    var type: Type,
    var notes: String? = null,
    var isBasalInsulin: Boolean = false,
    var icfg: ICfg? = null
) : HasIDs {

    fun contentEqualsTo(other: BS): Boolean =
        isValid == other.isValid &&
            timestamp == other.timestamp &&
            utcOffset == other.utcOffset &&
            amount == other.amount &&
            type == other.type &&
            notes == other.notes &&
            isBasalInsulin == other.isBasalInsulin

    fun onlyNsIdAdded(previous: BS): Boolean =
        previous.id != id &&
            contentEqualsTo(previous) &&
            previous.ids.nightscoutId == null &&
            ids.nightscoutId != null

    enum class Type {
        NORMAL,
        SMB,
        PRIMING;

        companion object {

            fun fromString(name: String?) = entries.firstOrNull { it.name == name } ?: NORMAL
        }
    }

    companion object
}
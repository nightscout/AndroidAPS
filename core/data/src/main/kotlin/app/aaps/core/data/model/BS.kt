package app.aaps.core.data.model

import java.util.TimeZone

data class BS(
    override var id: Long = 0,
    override var version: Int = 0,
    override var dateCreated: Long = -1,
    override var isValid: Boolean = true,
    override var referenceId: Long? = null,
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
package info.nightscout.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.database.entities.interfaces.DBEntryWithTimeAndDuration
import info.nightscout.database.entities.interfaces.TraceableDBEntry
import java.util.TimeZone

@Entity(tableName = TABLE_TEMPORARY_BASALS,
        foreignKeys = [ForeignKey(
        entity = TemporaryBasal::class,
        parentColumns = ["id"],
        childColumns = ["referenceId"])],
        indices = [
        Index("id"),
        Index("isValid"),
        Index("nightscoutId"),
        Index("pumpType"),
        Index("endId"),
        Index("pumpSerial"),
        Index("temporaryId"),
        Index("referenceId"),
        Index("timestamp")
    ])
data class TemporaryBasal(
    @PrimaryKey(autoGenerate = true)
    override var id: Long = 0,
    override var version: Int = 0,
    override var dateCreated: Long = -1,
    override var isValid: Boolean = true,
    override var referenceId: Long? = null,
    @Embedded
    override var interfaceIDs_backing: InterfaceIDs? = InterfaceIDs(),
    override var timestamp: Long,
    override var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    var type: Type,
    var isAbsolute: Boolean,
    var rate: Double,
    override var duration: Long
) : TraceableDBEntry, DBEntryWithTimeAndDuration {

    init {
        require(duration > 0)
    }

    fun contentEqualsTo(other: TemporaryBasal): Boolean =
        isValid == other.isValid &&
            timestamp == other.timestamp &&
            utcOffset == other.utcOffset &&
            isAbsolute == other.isAbsolute &&
            type == other.type &&
            duration == other.duration &&
            rate == other.rate

    fun onlyNsIdAdded(previous: TemporaryBasal): Boolean =
        previous.id != id &&
            contentEqualsTo(previous) &&
            previous.interfaceIDs.nightscoutId == null &&
            interfaceIDs.nightscoutId != null

    enum class Type {
        NORMAL,
        EMULATED_PUMP_SUSPEND,
        PUMP_SUSPEND,
        SUPERBOLUS,
        FAKE_EXTENDED // in memory only
        ;

        companion object {

            fun fromString(name: String?) = values().firstOrNull { it.name == name } ?: NORMAL
        }
    }

    val isInProgress: Boolean
        get() = System.currentTimeMillis() in timestamp..timestamp + duration
}
package info.nightscout.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import info.nightscout.database.entities.embedments.InsulinConfiguration
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.database.entities.interfaces.DBEntryWithTime
import info.nightscout.database.entities.interfaces.TraceableDBEntry
import java.util.TimeZone

@Entity(
    tableName = TABLE_BOLUSES,
    foreignKeys = [
        ForeignKey(
            entity = Bolus::class,
            parentColumns = ["id"],
            childColumns = ["referenceId"]
        )],
    indices = [
        Index("id"),
        Index("isValid"),
        Index("temporaryId"),
        Index("pumpId"),
        Index("pumpSerial"),
        Index("pumpType"),
        Index("referenceId"),
        Index("timestamp")
    ]
)
data class Bolus(
    @PrimaryKey(autoGenerate = true)
    override var id: Long = 0,
    override var version: Int = 0,
    override var dateCreated: Long = -1,
    override var isValid: Boolean = true,
    override var referenceId: Long? = null,
    @Embedded
    override var interfaceIDs_backing: InterfaceIDs? = null,
    override var timestamp: Long,
    override var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    var amount: Double,
    var type: Type,
    var notes: String? = null,
    var isBasalInsulin: Boolean = false,
    @Embedded
    var insulinConfiguration: InsulinConfiguration? = null
) : TraceableDBEntry, DBEntryWithTime {

    fun contentEqualsTo(other: Bolus): Boolean =
        isValid == other.isValid &&
            timestamp == other.timestamp &&
            utcOffset == other.utcOffset &&
            amount == other.amount &&
            type == other.type &&
            notes == other.notes &&
            isBasalInsulin == other.isBasalInsulin

    fun onlyNsIdAdded(previous: Bolus): Boolean =
        previous.id != id &&
            contentEqualsTo(previous) &&
            previous.interfaceIDs.nightscoutId == null &&
            interfaceIDs.nightscoutId != null

    enum class Type {
        NORMAL,
        SMB,
        PRIMING;

        companion object {

            fun fromString(name: String?) = values().firstOrNull { it.name == name } ?: NORMAL
        }
    }
}
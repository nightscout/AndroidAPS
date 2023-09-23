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

@Entity(
    tableName = TABLE_EXTENDED_BOLUSES,
    foreignKeys = [ForeignKey(
        entity = ExtendedBolus::class,
        parentColumns = ["id"],
        childColumns = ["referenceId"]
    )],
    indices = [
        Index("id"),
        Index("isValid"),
        Index("endId"),
        Index("pumpSerial"),
        Index("pumpId"),
        Index("pumpType"),
        Index("referenceId"),
        Index("timestamp")
    ]
)
data class ExtendedBolus(
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
    override var duration: Long,
    var amount: Double,
    var isEmulatingTempBasal: Boolean = false
) : TraceableDBEntry, DBEntryWithTimeAndDuration {

    init {
        require(duration > 0)
    }

    fun contentEqualsTo(other: ExtendedBolus): Boolean =
        isValid == other.isValid &&
            timestamp == other.timestamp &&
            utcOffset == other.utcOffset &&
            isEmulatingTempBasal == other.isEmulatingTempBasal &&
            duration == other.duration &&
            rate == other.rate

    fun onlyNsIdAdded(previous: ExtendedBolus): Boolean =
        previous.id != id &&
            contentEqualsTo(previous) &&
            previous.interfaceIDs.nightscoutId == null &&
            interfaceIDs.nightscoutId != null

    val rate: Double // in U/h
        get() = amount * (60 * 60 * 1000.0) / duration

    companion object
}
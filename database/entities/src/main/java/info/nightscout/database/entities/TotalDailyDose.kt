package info.nightscout.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.database.entities.interfaces.DBEntryWithTime
import info.nightscout.database.entities.interfaces.TraceableDBEntry
import java.util.TimeZone

@Entity(tableName = TABLE_TOTAL_DAILY_DOSES,
        foreignKeys = [ForeignKey(
        entity = TotalDailyDose::class,
        parentColumns = ["id"],
        childColumns = ["referenceId"])],
        indices = [
        Index("id"),
        Index("pumpId"),
        Index("pumpType"),
        Index("pumpSerial"),
        Index("isValid"),
        Index("referenceId"),
        Index("timestamp")
    ])
data class TotalDailyDose(
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
    var basalAmount: Double = 0.0,
    var bolusAmount: Double = 0.0,
    var totalAmount: Double = 0.0, // if zero it's calculated as basalAmount + bolusAmount
    var carbs: Double = 0.0
) : TraceableDBEntry, DBEntryWithTime {
    companion object
}
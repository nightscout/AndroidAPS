package app.aaps.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.aaps.database.entities.embedments.InsulinConfiguration
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.entities.interfaces.DBEntryWithTime
import app.aaps.database.entities.interfaces.TraceableDBEntry
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
        Index("pumpId"),
        Index("referenceId"),
        Index("timestamp"),
        Index("nightscoutId")
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
    var insulinConfiguration: InsulinConfiguration
) : TraceableDBEntry, DBEntryWithTime {

    enum class Type {
        NORMAL,
        SMB,
        PRIMING;
    }
}
package app.aaps.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.entities.interfaces.DBEntryWithTime
import app.aaps.database.entities.interfaces.TraceableDBEntry
import java.util.TimeZone

@Entity(
    tableName = TABLE_CALIBRATION_ENTRIES,
    foreignKeys = [ForeignKey(
        entity = CalibrationEntry::class,
        parentColumns = ["id"],
        childColumns = ["referenceId"]
    )],
    indices = [
        Index("nightscoutId"),
        Index("referenceId"),
        Index("timestamp")
    ]
)
data class CalibrationEntry(
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
    var fingerstickMgdl: Double,
    var sensorMgdlAtPairing: Double
) : TraceableDBEntry, DBEntryWithTime {

    fun contentEqualsTo(other: CalibrationEntry): Boolean =
        isValid == other.isValid &&
            timestamp == other.timestamp &&
            utcOffset == other.utcOffset &&
            fingerstickMgdl == other.fingerstickMgdl &&
            sensorMgdlAtPairing == other.sensorMgdlAtPairing

    fun copyFrom(other: CalibrationEntry) {
        isValid = other.isValid
        timestamp = other.timestamp
        utcOffset = other.utcOffset
        fingerstickMgdl = other.fingerstickMgdl
        sensorMgdlAtPairing = other.sensorMgdlAtPairing
        interfaceIDs.nightscoutId = other.interfaceIDs.nightscoutId
    }
}

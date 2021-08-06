package info.nightscout.androidaps.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import info.nightscout.androidaps.database.TABLE_BOLUSES
import info.nightscout.androidaps.database.embedments.InsulinConfiguration
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.interfaces.DBEntryWithTime
import info.nightscout.androidaps.database.interfaces.TraceableDBEntry
import java.util.*

@Entity(tableName = TABLE_BOLUSES,
    foreignKeys = [
        ForeignKey(
            entity = Bolus::class,
            parentColumns = ["id"],
            childColumns = ["referenceId"])],
    indices = [
        Index("id"),
        Index("isValid"),
        Index("temporaryId"),
        Index("pumpId"),
        Index("pumpSerial"),
        Index("pumpType"),
        Index("referenceId"),
        Index("timestamp")
    ])
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
    var isBasalInsulin: Boolean = false,
    @Embedded
    var insulinConfiguration: InsulinConfiguration? = null
) : TraceableDBEntry, DBEntryWithTime {

    enum class Type {
        NORMAL,
        SMB,
        PRIMING;

        companion object {

            fun fromString(name: String?) = values().firstOrNull { it.name == name } ?: NORMAL
        }
    }
}
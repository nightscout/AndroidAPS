package info.nightscout.androidaps.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import info.nightscout.androidaps.database.TABLE_CARBS
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.interfaces.DBEntryWithTimeAndDuration
import info.nightscout.androidaps.database.interfaces.TraceableDBEntry
import java.util.*

@Entity(tableName = TABLE_CARBS,
    foreignKeys = [ForeignKey(
        entity = Carbs::class,
        parentColumns = ["id"],
        childColumns = ["referenceId"])],
    indices = [
        Index("id"),
        Index("isValid"),
        Index("nightscoutId"),
        Index("referenceId"),
        Index("timestamp")
    ])
data class Carbs(
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
    override var duration: Long, // in milliseconds
    var amount: Double
) : TraceableDBEntry, DBEntryWithTimeAndDuration
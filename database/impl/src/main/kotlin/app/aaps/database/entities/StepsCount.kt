package app.aaps.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.entities.interfaces.DBEntryWithTimeAndDuration
import app.aaps.database.entities.interfaces.TraceableDBEntry
import java.util.TimeZone

/** Steps count values measured by a user smart watch or the like. */
@Entity(
    tableName = TABLE_STEPS_COUNT,
    indices = [Index("id"), Index("timestamp")]
)
data class StepsCount(
    @PrimaryKey(autoGenerate = true)
    override var id: Long = 0,
    /** Duration milliseconds */
    override var duration: Long,
    /** Milliseconds since the epoch. End of the sampling period, i.e. the value is
     *  sampled from timestamp-duration to timestamp. */
    override var timestamp: Long,
    var steps5min: Int,
    var steps10min: Int,
    var steps15min: Int,
    var steps30min: Int,
    var steps60min: Int,
    var steps180min: Int,
    /** Source device that measured the steps count. */
    var device: String,
    override var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    override var version: Int = 0,
    override var dateCreated: Long = -1,
    override var isValid: Boolean = true,
    override var referenceId: Long? = null,
    @Embedded
    override var interfaceIDs_backing: InterfaceIDs? = null
) : TraceableDBEntry, DBEntryWithTimeAndDuration
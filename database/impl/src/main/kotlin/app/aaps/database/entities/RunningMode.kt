package app.aaps.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.entities.interfaces.DBEntryWithTimeAndDuration
import app.aaps.database.entities.interfaces.TraceableDBEntry
import java.util.TimeZone

@Entity(
    tableName = TABLE_RUNNING_MODE,
    foreignKeys = [ForeignKey(
        entity = RunningMode::class,
        parentColumns = ["id"],
        childColumns = ["referenceId"]
    )],
    indices = [
        Index("id"),
        Index("nightscoutId"),
        Index("referenceId"),
        Index("timestamp")
    ]
)
data class RunningMode(
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
    /** Current running mode. */
    var mode: Mode,
    /** Planned duration in milliseconds */
    override var duration: Long,
    /**
     * true if forced automatically in loop plugin,
     * false if initiated by user
     */
    var autoForced: Boolean = false,
    /** List of reasons for automated mode change */
    var reasons: String? = null
) : TraceableDBEntry, DBEntryWithTimeAndDuration {

    enum class Mode {
        DISABLED_LOOP,
        OPEN_LOOP,
        CLOSED_LOOP,
        CLOSED_LOOP_LGS,
        SUPER_BOLUS,
        DISCONNECTED_PUMP,
        SUSPENDED_BY_PUMP,
        SUSPENDED_BY_USER,
        SUSPENDED_BY_DST
        ;
    }
}
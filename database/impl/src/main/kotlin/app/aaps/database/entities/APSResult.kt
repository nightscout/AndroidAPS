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
    tableName = TABLE_APS_RESULTS,
    foreignKeys = [ForeignKey(
        entity = APSResult::class,
        parentColumns = ["id"],
        childColumns = ["referenceId"]
    )],
    indices = [Index("referenceId"), Index("timestamp")]
)
data class APSResult(
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
    var algorithm: Algorithm,
    var glucoseStatusJson: String?,
    var currentTempJson: String?,
    var iobDataJson: String?,
    var profileJson: String?,
    var autosensDataJson: String?,
    var mealDataJson: String?,
    var resultJson: String
) : TraceableDBEntry, DBEntryWithTime {

    enum class Algorithm {
        UNKNOWN,
        AMA,
        SMB,
        AUTO_ISF
    }
}
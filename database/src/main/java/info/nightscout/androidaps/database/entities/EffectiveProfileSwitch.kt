package info.nightscout.androidaps.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import info.nightscout.androidaps.database.TABLE_EFFECTIVE_PROFILE_SWITCHES
import info.nightscout.androidaps.database.data.Block
import info.nightscout.androidaps.database.data.TargetBlock
import info.nightscout.androidaps.database.embedments.InsulinConfiguration
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.interfaces.DBEntryWithTime
import info.nightscout.androidaps.database.interfaces.TraceableDBEntry
import java.util.*

@Entity(tableName = TABLE_EFFECTIVE_PROFILE_SWITCHES,
    foreignKeys = [ForeignKey(
        entity = EffectiveProfileSwitch::class,
        parentColumns = ["id"],
        childColumns = ["referenceId"])],
    indices = [
        Index("id"),
        Index("referenceId"),
        Index("timestamp"),
        Index("isValid")
    ])
data class EffectiveProfileSwitch(
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
    var basalBlocks: List<Block>,
    var isfBlocks: List<Block>,
    var icBlocks: List<Block>,
    var targetBlocks: List<TargetBlock>,
    var glucoseUnit: GlucoseUnit,
    // Previous values from PS request
    var originalProfileName: String,
    var originalCustomizedName: String,
    var originalTimeshift: Long,  // [milliseconds]
    var originalPercentage: Int, // 1 ~ XXX [%]
    var originalDuration: Long, // [milliseconds]
    var originalEnd: Long,
    @Embedded
    var insulinConfiguration: InsulinConfiguration
) : TraceableDBEntry, DBEntryWithTime {

    enum class GlucoseUnit {
        MGDL,
        MMOL
    }
}
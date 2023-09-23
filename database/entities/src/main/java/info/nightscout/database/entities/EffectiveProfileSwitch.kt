package info.nightscout.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import info.nightscout.database.entities.data.Block
import info.nightscout.database.entities.data.TargetBlock
import info.nightscout.database.entities.embedments.InsulinConfiguration
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.database.entities.interfaces.DBEntryWithTime
import info.nightscout.database.entities.interfaces.TraceableDBEntry
import java.util.TimeZone

@Entity(
    tableName = TABLE_EFFECTIVE_PROFILE_SWITCHES,
    foreignKeys = [ForeignKey(
        entity = EffectiveProfileSwitch::class,
        parentColumns = ["id"],
        childColumns = ["referenceId"]
    )],
    indices = [
        Index("id"),
        Index("referenceId"),
        Index("timestamp"),
        Index("isValid")
    ]
)
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
    var originalEnd: Long, // not used (calculated from duration)
    @Embedded
    var insulinConfiguration: InsulinConfiguration
) : TraceableDBEntry, DBEntryWithTime {

    fun contentEqualsTo(other: EffectiveProfileSwitch): Boolean =
        isValid == other.isValid &&
            timestamp == other.timestamp &&
            utcOffset == other.utcOffset &&
            basalBlocks == other.basalBlocks &&
            isfBlocks == other.isfBlocks &&
            icBlocks == other.icBlocks &&
            targetBlocks == other.targetBlocks &&
            glucoseUnit == other.glucoseUnit &&
            originalProfileName == other.originalProfileName &&
            originalCustomizedName == other.originalCustomizedName &&
            originalTimeshift == other.originalTimeshift &&
            originalPercentage == other.originalPercentage &&
            originalDuration == other.originalDuration &&
            originalEnd == other.originalEnd

    fun onlyNsIdAdded(previous: EffectiveProfileSwitch): Boolean =
        previous.id != id &&
            contentEqualsTo(previous) &&
            previous.interfaceIDs.nightscoutId == null &&
            interfaceIDs.nightscoutId != null

    enum class GlucoseUnit {
        MGDL,
        MMOL;

        companion object
    }

    companion object
}
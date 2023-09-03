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
import info.nightscout.database.entities.interfaces.DBEntryWithTimeAndDuration
import info.nightscout.database.entities.interfaces.TraceableDBEntry
import java.util.TimeZone

@Entity(
    tableName = TABLE_PROFILE_SWITCHES,
    foreignKeys = [ForeignKey(
        entity = ProfileSwitch::class,
        parentColumns = ["id"],
        childColumns = ["referenceId"]
    )],
    indices = [
        Index("referenceId"),
        Index("timestamp"),
        Index("isValid"),
        Index("id"),
        Index("nightscoutId")
    ]
)
data class ProfileSwitch(
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
    var basalBlocks: List<Block>,
    var isfBlocks: List<Block>,
    var icBlocks: List<Block>,
    var targetBlocks: List<TargetBlock>,
    var glucoseUnit: GlucoseUnit,
    var profileName: String,
    var timeshift: Long,  // [milliseconds]
    var percentage: Int, // 1 ~ XXX [%]
    override var duration: Long, // [milliseconds]
    @Embedded
    var insulinConfiguration: InsulinConfiguration
) : TraceableDBEntry, DBEntryWithTimeAndDuration {

    fun copy(): ProfileSwitch =
        ProfileSwitch(
            isValid = isValid,
            timestamp = timestamp,
            utcOffset = utcOffset,
            basalBlocks = basalBlocks,
            isfBlocks = isfBlocks,
            icBlocks = icBlocks,
            targetBlocks = targetBlocks,
            glucoseUnit = glucoseUnit,
            profileName = profileName,
            timeshift = timeshift,
            percentage = percentage,
            duration = duration,
            insulinConfiguration = insulinConfiguration,
            interfaceIDs_backing = interfaceIDs_backing
        )

    fun contentEqualsTo(other: ProfileSwitch): Boolean =
        isValid == other.isValid &&
            timestamp == other.timestamp &&
            utcOffset == other.utcOffset &&
            basalBlocks == other.basalBlocks &&
            isfBlocks == other.isfBlocks &&
            icBlocks == other.icBlocks &&
            targetBlocks == other.targetBlocks &&
            glucoseUnit == other.glucoseUnit &&
            profileName == other.profileName &&
            timeshift == other.timeshift &&
            percentage == other.percentage &&
            duration == other.duration

    fun onlyNsIdAdded(previous: ProfileSwitch): Boolean =
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
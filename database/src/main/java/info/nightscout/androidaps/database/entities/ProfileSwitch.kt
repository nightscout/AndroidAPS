package info.nightscout.androidaps.database.entities

import androidx.room.*
import info.nightscout.androidaps.database.data.Block
import info.nightscout.androidaps.database.TABLE_PROFILE_SWITCHES
import info.nightscout.androidaps.database.data.TargetBlock
import info.nightscout.androidaps.database.embedments.InsulinConfiguration
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.interfaces.DBEntryWithTimeAndDuration
import info.nightscout.androidaps.database.interfaces.TraceableDBEntry
import java.util.TimeZone

@Entity(tableName = TABLE_PROFILE_SWITCHES,
        foreignKeys = [ForeignKey(
                entity = ProfileSwitch::class,
                parentColumns = ["id"],
                childColumns = ["referenceId"])],
        indices = [Index("referenceId"), Index("timestamp")])
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
    var profileName: String,
    var glucoseUnit: GlucoseUnit,
    var basalBlocks: List<Block>,
    var isfBlocks: List<Block>,
    var icBlocks: List<Block>,
    var targetBlocks: List<TargetBlock>,
    @Embedded
        var insulinConfiguration: InsulinConfiguration,
    var timeshift: Int,
    var percentage: Int,
    override var duration: Long
) : TraceableDBEntry, DBEntryWithTimeAndDuration {
    enum class GlucoseUnit {
        MGDL,
        MMOL
    }
}
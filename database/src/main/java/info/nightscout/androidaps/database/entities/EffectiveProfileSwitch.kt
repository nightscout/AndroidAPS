package info.nightscout.androidaps.database.entities

import androidx.room.*
import info.nightscout.androidaps.database.data.Block
import info.nightscout.androidaps.database.TABLE_EFFECTIVE_PROFILE_SWITCHES
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.interfaces.DBEntryWithTimeAndDuration
import info.nightscout.androidaps.database.interfaces.TraceableDBEntry
import java.util.TimeZone

@Entity(tableName = TABLE_EFFECTIVE_PROFILE_SWITCHES,
        foreignKeys = [ForeignKey(
                entity = EffectiveProfileSwitch::class,
                parentColumns = ["id"],
                childColumns = ["referenceId"])],
        indices = [Index("referenceId"), Index("timestamp")])
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
    override var duration: Long,
    var basalBlocks: List<Block>
) : TraceableDBEntry, DBEntryWithTimeAndDuration
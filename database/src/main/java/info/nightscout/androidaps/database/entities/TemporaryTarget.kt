package info.nightscout.androidaps.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import info.nightscout.androidaps.database.TABLE_TEMPORARY_TARGETS
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.interfaces.DBEntryWithTimeAndDuration
import info.nightscout.androidaps.database.interfaces.TraceableDBEntry
import java.util.*

@Entity(tableName = TABLE_TEMPORARY_TARGETS,
    foreignKeys = [ForeignKey(
        entity = TemporaryTarget::class,
        parentColumns = ["id"],
        childColumns = ["referenceId"])],
    indices = [Index("referenceId"), Index("timestamp")])
data class TemporaryTarget(
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
    var reason: Reason,
    var highTarget: Double, // in mgdl
    var lowTarget: Double, // in mgdl
    override var duration: Long // in millis
) : TraceableDBEntry, DBEntryWithTimeAndDuration {

    fun contentEqualsTo(other: TemporaryTarget): Boolean =
        timestamp == other.timestamp &&
            utcOffset == other.utcOffset &&
            reason == other.reason &&
            highTarget == other.highTarget &&
            lowTarget == other.lowTarget &&
            duration == other.duration &&
            isValid == other.isValid

    fun isRecordDeleted(other: TemporaryTarget): Boolean =
        isValid && !other.isValid

    enum class Reason(val text: String) {
        @SerializedName("Custom")
        CUSTOM("Custom"),
        @SerializedName("Hypo")
        HYPOGLYCEMIA("Hypo"),
        @SerializedName("Activity")
        ACTIVITY("Activity"),
        @SerializedName("Eating Soon")
        EATING_SOON("Eating Soon"),
        @SerializedName("Automation")
        AUTOMATION("Automation")
        ;

        companion object {
            fun fromString(direction: String?) = values().firstOrNull { it.text == direction }
                ?: CUSTOM
        }
    }
}
package info.nightscout.androidaps.database.entities

import androidx.room.*
import info.nightscout.androidaps.database.TABLE_THERAPY_EVENTS
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.interfaces.DBEntryWithTimeAndDuration
import info.nightscout.androidaps.database.interfaces.TraceableDBEntry
import java.util.TimeZone

@Entity(tableName = TABLE_THERAPY_EVENTS,
        foreignKeys = [ForeignKey(
                entity = TherapyEvent::class,
                parentColumns = ["id"],
                childColumns = ["referenceId"])],
        indices = [Index("referenceId"), Index("timestamp")])
data class TherapyEvent(
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
    override var duration: Long = 0,
    var type: Type,
    var note: String? = null,
    var amount: Double? = null
) : TraceableDBEntry, DBEntryWithTimeAndDuration {
    enum class Type {
        CANNULA_CHANGED,
        TUBE_CHANGED,
        RESERVOIR_CHANGED,
        BATTERY_CHANGED,
        LEAKING_INFUSION_SET,
        SENSOR_INSERTED,
        SENSOR_STARTED,
        SENSOR_STOPPED,
        FINGER_STICK_BG_VALUE,
        ACTIVITY,
        FALLING_ASLEEP,
        WAKING_UP,
        SICKNESS,
        STRESS,
        PRE_PERIOD,
        ALCOHOL,
        CORTISON,
        FEELING_LOW,
        FEELING_HIGH,
        ANNOUNCEMENT,
        QUESTION,
        NOTE,
        APS_OFFLINE,
        BATTERY_EMPTY,
        RESERVOIR_EMPTY,
        OCCLUSION,
        PUMP_STOPPED,
        PUMP_STARTED,
        PUMP_PAUSED
    }
}
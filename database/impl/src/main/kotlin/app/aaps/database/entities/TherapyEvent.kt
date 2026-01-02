package app.aaps.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.aaps.database.entities.data.GlucoseUnit
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.entities.interfaces.DBEntryWithTimeAndDuration
import app.aaps.database.entities.interfaces.TraceableDBEntry
import java.util.TimeZone

@Entity(
    tableName = TABLE_THERAPY_EVENTS,
    foreignKeys = [ForeignKey(
        entity = TherapyEvent::class,
        parentColumns = ["id"],
        childColumns = ["referenceId"]
    )],
    indices = [
        Index("id"),
        Index("type"),
        Index("nightscoutId"),
        Index("isValid"),
        Index("referenceId"),
        Index("timestamp")
    ]
)
data class TherapyEvent(
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
    override var duration: Long = 0,
    var type: Type,
    var note: String? = null,
    var enteredBy: String? = null,
    var glucose: Double? = null,
    var glucoseType: MeterType? = null,
    var glucoseUnit: GlucoseUnit,
    var location: Location? = null,
    var arrow: Arrow? = null
) : TraceableDBEntry, DBEntryWithTimeAndDuration {

    enum class MeterType {
        FINGER,
        SENSOR,
        MANUAL
        ;
    }

    @Suppress("unused")
    enum class Type {

        CANNULA_CHANGE,
        INSULIN_CHANGE,
        PUMP_BATTERY_CHANGE,
        SENSOR_CHANGE,
        SENSOR_STARTED,
        SENSOR_STOPPED,
        FINGER_STICK_BG_VALUE,
        EXERCISE,
        ANNOUNCEMENT,
        QUESTION,
        NOTE,
        APS_OFFLINE,
        DAD_ALERT,
        NS_MBG,

        // Used but not as a Therapy Event (use constants only)
        CARBS_CORRECTION,
        BOLUS_WIZARD,
        CORRECTION_BOLUS,
        MEAL_BOLUS,
        COMBO_BOLUS,
        TEMPORARY_TARGET,
        TEMPORARY_TARGET_CANCEL,
        PROFILE_SWITCH,
        SNACK_BOLUS,
        TEMPORARY_BASAL,
        TEMPORARY_BASAL_START,
        TEMPORARY_BASAL_END,

        // Not supported by NS
        SETTINGS_EXPORT,
        TUBE_CHANGE,
        FALLING_ASLEEP,
        BATTERY_EMPTY,
        RESERVOIR_EMPTY,
        OCCLUSION,
        PUMP_STOPPED,
        PUMP_STARTED,
        PUMP_PAUSED,
        WAKING_UP,
        SICKNESS,
        STRESS,
        PRE_PERIOD,
        ALCOHOL,
        CORTISONE,
        FEELING_LOW,
        FEELING_HIGH,
        LEAKING_INFUSION_SET,

        // Default
        NONE
        ;
    }
    enum class Location {
        FRONT_RIGHT_UPPER_CHEST,
        FRONT_LEFT_UPPER_CHEST,
        SIDE_RIGHT_UPPER_ARM,
        SIDE_LEFT_UPPER_ARM,
        BACK_RIGHT_UPPER_ARM,
        BACK_LEFT_UPPER_ARM,
        SIDE_RIGHT_UPPER_ABDOMEN,
        SIDE_LEFT_UPPER_ABDOMEN,
        SIDE_RIGHT_LOWER_ABDOMEN,
        SIDE_LEFT_LOWER_ABDOMEN,
        FRONT_RIGHT_UPPER_ABDOMEN,
        FRONT_LEFT_UPPER_ABDOMEN,
        FRONT_RIGHT_LOWER_ABDOMEN,
        FRONT_LEFT_LOWER_ABDOMEN,
        BACK_RIGHT_BUTTOCK,
        BACK_LEFT_BUTTOCK,
        FRONT_RIGHT_UPPER_THIGH,
        FRONT_LEFT_UPPER_THIGH,
        FRONT_RIGHT_LOWER_THIGH,
        FRONT_LEFT_LOWER_THIGH,
        SIDE_RIGHT_UPPER_THIGH,
        SIDE_LEFT_UPPER_THIGH,
        SIDE_RIGHT_LOWER_THIGH,
        SIDE_LEFT_LOWER_THIGH,
        NONE
        ;
    }

    enum class Arrow {
        UP,
        UP_RIGHT,
        RIGHT,
        DOWN_RIGHT,
        DOWN,
        DOWN_LEFT,
        LEFT,
        UP_LEFT,
        CENTER,
        NONE
        ;
    }
}
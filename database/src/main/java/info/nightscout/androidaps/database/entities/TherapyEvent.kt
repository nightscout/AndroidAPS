package info.nightscout.androidaps.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import info.nightscout.androidaps.database.TABLE_THERAPY_EVENTS
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.interfaces.DBEntryWithTimeAndDuration
import info.nightscout.androidaps.database.interfaces.TraceableDBEntry
import java.util.*

@Entity(tableName = TABLE_THERAPY_EVENTS,
    foreignKeys = [ForeignKey(
        entity = TherapyEvent::class,
        parentColumns = ["id"],
        childColumns = ["referenceId"])],
    indices = [
        Index("id"),
        Index("type"),
        Index("nightscoutId"),
        Index("isValid"),
        Index("referenceId"),
        Index("timestamp")
    ])
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
) : TraceableDBEntry, DBEntryWithTimeAndDuration {

    enum class GlucoseUnit {
        MGDL,
        MMOL;
        companion object
    }

    enum class MeterType(val text: String) {
        @SerializedName("Finger")
        FINGER("Finger"),
        @SerializedName("Sensor")
        SENSOR("Sensor"),
        @SerializedName("Manual")
        MANUAL("Manual")
        ;

        companion object {

            fun fromString(text: String?) = values().firstOrNull { it.text == text } ?: MANUAL
        }
    }

    @Suppress("unused")
    enum class Type(val text: String, val nsNative: Boolean = false) {

        @SerializedName("Site Change")
        CANNULA_CHANGE("Site Change", nsNative = true),
        @SerializedName("Insulin Change")
        INSULIN_CHANGE("Insulin Change", nsNative = true),
        @SerializedName("Pump Battery Change")
        PUMP_BATTERY_CHANGE("Pump Battery Change", nsNative = true),
        @SerializedName("Sensor Change")
        SENSOR_CHANGE("Sensor Change", nsNative = true),
        @SerializedName("Sensor Start")
        SENSOR_STARTED("Sensor Start", nsNative = true),
        @SerializedName("Sensor Stop")
        SENSOR_STOPPED("Sensor Stop", nsNative = true),
        @SerializedName("BG Check")
        FINGER_STICK_BG_VALUE("BG Check", nsNative = true),
        @SerializedName("Exercise")
        EXERCISE("Exercise", nsNative = true),
        @SerializedName("Announcement")
        ANNOUNCEMENT("Announcement", nsNative = true),
        @SerializedName("Question")
        QUESTION("Question", nsNative = true),
        @SerializedName("Note")
        NOTE("Note", nsNative = true),
        @SerializedName("OpenAPS Offline")
        APS_OFFLINE("OpenAPS Offline", nsNative = true),
        @SerializedName("D.A.D. Alert")
        DAD_ALERT("D.A.D. Alert", nsNative = true),
        @SerializedName("Mbg")
        NS_MBG("Mbg", nsNative = true),

        // Used but not as a Therapy Event (use constants only)
        @SerializedName("Carb Correction")
        CARBS_CORRECTION("Carb Correction", nsNative = true),
        @SerializedName("Bolus Wizard")
        BOLUS_WIZARD("Bolus Wizard", nsNative = true),
        @SerializedName("Correction Bolus")
        CORRECTION_BOLUS("Correction Bolus", nsNative = true),
        @SerializedName("Meal Bolus")
        MEAL_BOLUS("Meal Bolus", nsNative = true),
        @SerializedName("Combo Bolus")
        COMBO_BOLUS("Combo Bolus", nsNative = true),
        @SerializedName("Temporary Target")
        TEMPORARY_TARGET("Temporary Target", nsNative = true),
        @SerializedName("Temporary Target Cancel")
        TEMPORARY_TARGET_CANCEL("Temporary Target Cancel", nsNative = true),
        @SerializedName("Profile Switch")
        PROFILE_SWITCH("Profile Switch", nsNative = true),
        @SerializedName("Snack Bolus")
        SNACK_BOLUS("Snack Bolus", nsNative = true),
        @SerializedName("Temp Basal")
        TEMPORARY_BASAL("Temp Basal", nsNative = true),
        @SerializedName("Temp Basal Start")
        TEMPORARY_BASAL_START("Temp Basal Start", nsNative = true),
        @SerializedName("Temp Basal End")
        TEMPORARY_BASAL_END("Temp Basal End", nsNative = true),

        // Not supported by NS
        @SerializedName("Tube Change")
        TUBE_CHANGE("Tube Change"),
        @SerializedName("Falling Asleep")
        FALLING_ASLEEP("Falling Asleep"),
        @SerializedName("Battery Empty")
        BATTERY_EMPTY("Battery Empty"),
        @SerializedName("Reservoir Empty")
        RESERVOIR_EMPTY("Reservoir Empty"),
        @SerializedName("Occlusion")
        OCCLUSION("Occlusion"),
        @SerializedName("Pump Stopped")
        PUMP_STOPPED("Pump Stopped"),
        @SerializedName("Pump Started")
        PUMP_STARTED("Pump Started"),
        @SerializedName("Pump Paused")
        PUMP_PAUSED("Pump Paused"),
        @SerializedName("Waking Up")
        WAKING_UP("Waking Up"),
        @SerializedName("Sickness")
        SICKNESS("Sickness"),
        @SerializedName("Stress")
        STRESS("Stress"),
        @SerializedName("Pre Period")
        PRE_PERIOD("Pre Period"),
        @SerializedName("Alcohol")
        ALCOHOL("Alcohol"),
        @SerializedName("Cortisone")
        CORTISONE("Cortisone"),
        @SerializedName("Feeling Low")
        FEELING_LOW("Feeling Low"),
        @SerializedName("Feeling High")
        FEELING_HIGH("Feeling High"),
        @SerializedName("Leaking Infusion Set")
        LEAKING_INFUSION_SET("Leaking Infusion Set"),

        // Default
        @SerializedName("<none>")
        NONE("<none>")
        ;

        companion object {

            fun fromString(text: String?) = values().firstOrNull { it.text == text } ?: NONE
        }
    }
}
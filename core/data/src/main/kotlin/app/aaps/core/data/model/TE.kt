package app.aaps.core.data.model

import java.util.TimeZone

data class TE(
    override var id: Long = 0,
    override var version: Int = 0,
    override var dateCreated: Long = -1,
    override var isValid: Boolean = true,
    override var referenceId: Long? = null,
    override var ids: IDs = IDs(),
    var timestamp: Long,
    var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    /** Duration in milliseconds */
    var duration: Long = 0,
    var type: Type,
    var note: String? = null,
    var enteredBy: String? = null,
    var glucose: Double? = null,
    var glucoseType: MeterType? = null,
    var glucoseUnit: GlucoseUnit,
    var location: Location? = null,
    var arrow: Arrow? = null
) : HasIDs {

    fun contentEqualsTo(other: TE): Boolean =
        isValid == other.isValid &&
            timestamp == other.timestamp &&
            utcOffset == other.utcOffset &&
            duration == other.duration &&
            type == other.type &&
            note == other.note &&
            enteredBy == other.enteredBy &&
            glucose == other.glucose &&
            glucoseType == other.glucoseType &&
            glucoseUnit == other.glucoseUnit

    fun onlyNsIdAdded(previous: TE): Boolean =
        previous.id != id &&
            contentEqualsTo(previous) &&
            previous.ids.nightscoutId == null &&
            ids.nightscoutId != null

    enum class MeterType(val text: String) {
        FINGER("Finger"),
        SENSOR("Sensor"),
        MANUAL("Manual")
        ;

        companion object {

            fun fromString(text: String?) = entries.firstOrNull { it.text == text }
        }
    }

    @Suppress("unused")
    enum class Type(val text: String, val nsNative: Boolean = false) {

        CANNULA_CHANGE("Site Change", nsNative = true),
        INSULIN_CHANGE("Insulin Change", nsNative = true),
        PUMP_BATTERY_CHANGE("Pump Battery Change", nsNative = true),
        SENSOR_CHANGE("Sensor Change", nsNative = true),
        SENSOR_STARTED("Sensor Start", nsNative = true),
        SENSOR_STOPPED("Sensor Stop", nsNative = true),
        FINGER_STICK_BG_VALUE("BG Check", nsNative = true),
        EXERCISE("Exercise", nsNative = true),
        ANNOUNCEMENT("Announcement", nsNative = true),
        QUESTION("Question", nsNative = true),
        NOTE("Note", nsNative = true),
        APS_OFFLINE("OpenAPS Offline", nsNative = true),
        DAD_ALERT("D.A.D. Alert", nsNative = true),
        NS_MBG("Mbg", nsNative = true),

        // Used but not as a Therapy Event (use constants only)
        CARBS_CORRECTION("Carb Correction", nsNative = true),
        BOLUS_WIZARD("Bolus Wizard", nsNative = true),
        CORRECTION_BOLUS("Correction Bolus", nsNative = true),
        MEAL_BOLUS("Meal Bolus", nsNative = true),
        COMBO_BOLUS("Combo Bolus", nsNative = true),
        TEMPORARY_TARGET("Temporary Target", nsNative = true),
        TEMPORARY_TARGET_CANCEL("Temporary Target Cancel", nsNative = true),
        PROFILE_SWITCH("Profile Switch", nsNative = true),
        SNACK_BOLUS("Snack Bolus", nsNative = true),
        TEMPORARY_BASAL("Temp Basal", nsNative = true),
        TEMPORARY_BASAL_START("Temp Basal Start", nsNative = true),
        TEMPORARY_BASAL_END("Temp Basal End", nsNative = true),

        // Not supported by NS
        TUBE_CHANGE("Tube Change"),
        FALLING_ASLEEP("Falling Asleep"),
        BATTERY_EMPTY("Battery Empty"),
        RESERVOIR_EMPTY("Reservoir Empty"),
        OCCLUSION("Occlusion"),
        PUMP_STOPPED("Pump Stopped"),
        PUMP_STARTED("Pump Started"),
        PUMP_PAUSED("Pump Paused"),
        SETTINGS_EXPORT("Settings Export"),
        WAKING_UP("Waking Up"),
        SICKNESS("Sickness"),
        STRESS("Stress"),
        PRE_PERIOD("Pre Period"),
        ALCOHOL("Alcohol"),
        CORTISONE("Cortisone"),
        FEELING_LOW("Feeling Low"),
        FEELING_HIGH("Feeling High"),
        LEAKING_INFUSION_SET("Leaking Infusion Set"),

        // Default
        NONE("<none>")
        ;

        companion object {

            fun fromString(text: String?) = entries.firstOrNull { it.text == text } ?: NONE
        }
    }

    @Suppress("unused")
    enum class Location(val text: String, val pump: Boolean = true) {
        FRONT_RIGHT_UPPER_CHEST("Front Right Upper Chest", false),
        FRONT_LEFT_UPPER_CHEST("Front Left Upper Chest", false),
        SIDE_RIGHT_UPPER_ARM("Side Right Upper Arm"),
        SIDE_LEFT_UPPER_ARM("Side Left Upper Arm"),
        BACK_RIGHT_UPPER_ARM("Back Right Upper Arm"),
        BACK_LEFT_UPPER_ARM("Back Left Upper Arm"),
        SIDE_RIGHT_UPPER_ABDOMEN("Side Right Upper Abdomen"),
        SIDE_LEFT_UPPER_ABDOMEN("Side Left Upper Abdomen"),
        SIDE_RIGHT_LOWER_ABDOMEN("Side Right Lower Abdomen"),
        SIDE_LEFT_LOWER_ABDOMEN("Side Left Lower Abdomen"),
        FRONT_RIGHT_UPPER_ABDOMEN("Front Right Upper Abdomen"),
        FRONT_LEFT_UPPER_ABDOMEN("Front Left Upper Abdomen"),
        FRONT_RIGHT_LOWER_ABDOMEN("Front Right Lower Abdomen"),
        FRONT_LEFT_LOWER_ABDOMEN("Front Left Lower Abdomen"),
        BACK_RIGHT_BUTTOCK("Back Right Buttock"),
        BACK_LEFT_BUTTOCK("Back Left Buttock"),
        FRONT_RIGHT_UPPER_THIGH("Front Right Upper Thigh"),
        FRONT_LEFT_UPPER_THIGH("Front Left Upper Thigh"),
        FRONT_RIGHT_LOWER_THIGH("Front Right Lower Thigh"),
        FRONT_LEFT_LOWER_THIGH("Front Left Lower Thigh"),
        SIDE_RIGHT_UPPER_THIGH("Side Right Upper Thigh"),
        SIDE_LEFT_UPPER_THIGH("Side Left Upper Thigh"),
        SIDE_RIGHT_LOWER_THIGH("Side Right Lower Thigh"),
        SIDE_LEFT_LOWER_THIGH("Side Left Lower Thigh"),
        NONE("<none>", false);

        companion object{

            fun fromString(text: String?) = Location.entries.firstOrNull { it.text == text } ?: NONE
        }
    }

    @Suppress("unused")
    enum class Arrow(val text: String) {
        UP("Up"),
        UP_RIGHT("Up Right"),
        RIGHT("Right"),
        DOWN_RIGHT("Down Right"),
        DOWN("Down"),
        DOWN_LEFT("Down Left"),
        LEFT("Left"),
        UP_LEFT("Up Left"),
        CENTER("Center"),
        NONE("<none>");

        companion object{

            fun fromString(text: String?) = Arrow.entries.firstOrNull { it.text == text } ?: NONE
        }
    }

    companion object
}
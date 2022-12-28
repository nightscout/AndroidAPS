package info.nightscout.sdk.localmodel.treatment

import com.google.gson.annotations.SerializedName

@Suppress("unused")
enum class EventType(val text: String) {

    @SerializedName("Site Change") CANNULA_CHANGE("Site Change"),
    @SerializedName("Insulin Change") INSULIN_CHANGE("Insulin Change"),
    @SerializedName("Pump Battery Change") PUMP_BATTERY_CHANGE("Pump Battery Change"),
    @SerializedName("Sensor Change") SENSOR_CHANGE("Sensor Change"),
    @SerializedName("Sensor Start") SENSOR_STARTED("Sensor Start"),
    @SerializedName("Sensor Stop") SENSOR_STOPPED("Sensor Stop"),
    @SerializedName("BG Check") FINGER_STICK_BG_VALUE("BG Check"),
    @SerializedName("Exercise") EXERCISE("Exercise"),
    @SerializedName("Announcement") ANNOUNCEMENT("Announcement"),
    @SerializedName("Question") QUESTION("Question"),
    @SerializedName("Note") NOTE("Note"),
    @SerializedName("OpenAPS Offline") APS_OFFLINE("OpenAPS Offline"),
    @SerializedName("D.A.D. Alert") DAD_ALERT("D.A.D. Alert"),
    @SerializedName("Mbg") NS_MBG("Mbg"),

    // Used but not as a Therapy Event (use constants only)
    @SerializedName("Carb Correction") CARBS_CORRECTION("Carb Correction"),
    @SerializedName("Bolus Wizard") BOLUS_WIZARD("Bolus Wizard"),
    @SerializedName("Correction Bolus") CORRECTION_BOLUS("Correction Bolus"),
    @SerializedName("Meal Bolus") MEAL_BOLUS("Meal Bolus"),
    @SerializedName("Combo Bolus") COMBO_BOLUS("Combo Bolus"),
    @SerializedName("Temporary Target") TEMPORARY_TARGET("Temporary Target"),
    @SerializedName("Temporary Target Cancel") TEMPORARY_TARGET_CANCEL("Temporary Target Cancel"),
    @SerializedName("Profile Switch") PROFILE_SWITCH("Profile Switch"),
    @SerializedName("Snack Bolus") SNACK_BOLUS("Snack Bolus"),
    @SerializedName("Temp Basal") TEMPORARY_BASAL("Temp Basal"),
    @SerializedName("Temp Basal Start") TEMPORARY_BASAL_START("Temp Basal Start"),
    @SerializedName("Temp Basal End") TEMPORARY_BASAL_END("Temp Basal End"),

    @SerializedName("") ERROR(""),
    @SerializedName("<none>") NONE("<none>");

    companion object {

        fun fromString(text: String?) = values().firstOrNull { it.text == text } ?: NONE
    }
}
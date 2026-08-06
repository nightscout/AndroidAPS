package app.aaps.core.nssdk.localmodel.treatment

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Suppress("unused")
@Serializable
enum class EventType(val text: String) {

    @SerialName("Site Change") CANNULA_CHANGE("Site Change"),
    @SerialName("Insulin Change") INSULIN_CHANGE("Insulin Change"),
    @SerialName("Pump Battery Change") PUMP_BATTERY_CHANGE("Pump Battery Change"),
    @SerialName("Sensor Change") SENSOR_CHANGE("Sensor Change"),
    @SerialName("Sensor Start") SENSOR_STARTED("Sensor Start"),
    @SerialName("Sensor Stop") SENSOR_STOPPED("Sensor Stop"),
    @SerialName("BG Check") FINGER_STICK_BG_VALUE("BG Check"),
    @SerialName("Exercise") EXERCISE("Exercise"),
    @SerialName("Announcement") ANNOUNCEMENT("Announcement"),
    @SerialName("SettingsExport") SETTINGS_EXPORT("Settings Export"),
    @SerialName("Question") QUESTION("Question"),
    @SerialName("Note") NOTE("Note"),
    @SerialName("OpenAPS Offline") APS_OFFLINE("OpenAPS Offline"),
    @SerialName("D.A.D. Alert") DAD_ALERT("D.A.D. Alert"),
    @SerialName("Mbg") NS_MBG("Mbg"),

    // Used but not as a Therapy Event (use constants only)
    @SerialName("Carb Correction") CARBS_CORRECTION("Carb Correction"),
    @SerialName("Bolus Wizard") BOLUS_WIZARD("Bolus Wizard"),
    @SerialName("Correction Bolus") CORRECTION_BOLUS("Correction Bolus"),
    @SerialName("Meal Bolus") MEAL_BOLUS("Meal Bolus"),
    @SerialName("Combo Bolus") COMBO_BOLUS("Combo Bolus"),
    @SerialName("Temporary Target") TEMPORARY_TARGET("Temporary Target"),
    @SerialName("Temporary Target Cancel") TEMPORARY_TARGET_CANCEL("Temporary Target Cancel"),
    @SerialName("Profile Switch") PROFILE_SWITCH("Profile Switch"),
    @SerialName("Snack Bolus") SNACK_BOLUS("Snack Bolus"),
    @SerialName("Temp Basal") TEMPORARY_BASAL("Temp Basal"),
    @SerialName("Temp Basal Start") TEMPORARY_BASAL_START("Temp Basal Start"),
    @SerialName("Temp Basal End") TEMPORARY_BASAL_END("Temp Basal End"),

    @SerialName("") ERROR(""),
    @SerialName("<none>") NONE("<none>");

    companion object {
        fun fromString(text: String?) = entries.firstOrNull { it.text == text } ?: NONE
    }
}
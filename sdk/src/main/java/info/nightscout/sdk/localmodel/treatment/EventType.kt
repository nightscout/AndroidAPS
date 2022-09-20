package info.nightscout.sdk.localmodel.treatment

enum class EventType {
    FINGER_STICK_BG_VALUE,      // BG Check
    SNACK_BOLUS,
    MEAL_BOLUS,
    CORRECTION_BOLUS,
    CARBS_CORRECTION,
    COMBO_BOLUS,
    ANNOUNCEMENT,
    NOTE,
    QUESTION,
    EXERCISE,
    CANNULA_CHANGE,             // Site Change
    SENSOR_STARTED,             // Sensor Start
    SENSOR_CHANGE,
    PUMP_BATTERY_CHANGE,
    INSULIN_CHANGE,
    TEMPORARY_BASAL,            // Temp Basal
    PROFILE_SWITCH,
    DAD_ALERT,
    TEMPORARY_TARGET,
    APS_OFFLINE,                // OpenAPS Offline
    BOLUS_WIZARD,
    // below other EventType found in AAPS that should be "compatible" with NS...
    SENSOR_STOPPED,
    NS_MBG,
    TEMPORARY_TARGET_CANCEL,
    TEMPORARY_BASAL_START,      // Temp Basal Start
    TEMPORARY_BASAL_END,        // Temp Basal Stop
    // not in NS (nsNative false in AAPS)
    NONE
}

//example: "BG Check", "Snack Bolus", "Meal Bolus", "Correction Bolus", "Carb Correction", "Combo Bolus", "Announcement", "Note", "Question", "Exercise", "Site Change", "Sensor Start", "Sensor Change", "Pump Battery Change", "Insulin Change", "Temp Basal", "Profile Switch", "D.A.D. Alert", "Temporary Target", "OpenAPS Offline", "Bolus Wizard"
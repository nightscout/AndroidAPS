package app.aaps.core.keys

enum class StringKey(
    override val key: Int,
    val defaultValue: String,
    override val defaultedBySM: Boolean = false,
    val showInApsMode: Boolean = true,
    val showInNsClientMode: Boolean = true,
    val showInPumpControlMode: Boolean = true,
    val hideParentScreenIfHidden: Boolean = false // PreferenceScreen is final so we cannot extend and modify behavior
) : PreferenceKey {

    GeneralUnits(R.string.key_units, "mg/dl"),
    GeneralLanguage(R.string.key_language, "default", defaultedBySM = true),
    GeneralSkin(R.string.key_skin, ""),
    GeneralDarkMode(R.string.key_use_dark_mode, "dark", defaultedBySM = true),
    SafetyAge(R.string.key_safety_age, "adult"),
    LoopApsMode(R.string.key_aps_mode, "open"),
    MaintenanceEmail(R.string.key_maintenance_logs_email, "logs@aaps.app", defaultedBySM = true, hideParentScreenIfHidden = true),
    MaintenanceIdentification(R.string.key_email_for_crash_report, "", defaultedBySM = true, hideParentScreenIfHidden = true),
}
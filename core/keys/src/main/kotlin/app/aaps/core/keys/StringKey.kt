package app.aaps.core.keys

enum class StringKey(
    override val key: Int,
    val defaultValue: String,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: Int = 0,
    override val negativeDependency: Int = 0,
    override val hideParentScreenIfHidden: Boolean = false
) : PreferenceKey {

    GeneralUnits(R.string.key_units, "mg/dl"),
    GeneralLanguage(R.string.key_language, "default", defaultedBySM = true),
    GeneralSkin(R.string.key_skin, ""),
    GeneralDarkMode(R.string.key_use_dark_mode, "dark", defaultedBySM = true),
    SafetyAge(R.string.key_safety_age, "adult"),
    LoopApsMode(R.string.key_aps_mode, "open"),
    MaintenanceEmail(R.string.key_maintenance_logs_email, "logs@aaps.app", defaultedBySM = true, hideParentScreenIfHidden = true),
    MaintenanceIdentification(R.string.key_email_for_crash_report, "", defaultedBySM = true, hideParentScreenIfHidden = true),
    AutomationLocation(R.string.key_location, "PASSIVE", hideParentScreenIfHidden = true)
}
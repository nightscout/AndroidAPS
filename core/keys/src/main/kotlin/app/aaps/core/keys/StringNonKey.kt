package app.aaps.core.keys

enum class StringNonKey(
    override val key: String,
    override val defaultValue: String,
) : StringNonPreferenceKey {

    QuickWizard(key = "QuickWizard", defaultValue = "[]"),
    WearCwfWatchfaceName(key = "wear_cwf_watchface_name", defaultValue = ""),
    WearCwfAuthorVersion(key = "wear_cwf_author_version", defaultValue = ""),
    WearCwfFileName(key = "wear_cwf_filename", defaultValue = ""),
    BolusInfoStorage(key = "key_bolus_storage", defaultValue = ""),
    ActivePumpType(key = "active_pump_type", defaultValue = ""),
    ActivePumpSerialNumber(key = "active_pump_serial_number", defaultValue = ""),
}
package app.aaps.core.keys

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

enum class StringNonKey(
    override val key: String,
    override val defaultValue: String,
    override val exportable: Boolean = true
) : StringNonPreferenceKey {

    QuickWizard(key = "QuickWizard", defaultValue = "[]"),
    WearCwfWatchfaceName(key = "wear_cwf_watchface_name", defaultValue = ""),
    WearCwfAuthorVersion(key = "wear_cwf_author_version", defaultValue = ""),
    WearCwfFileName(key = "wear_cwf_filename", defaultValue = ""),
    BolusInfoStorage(key = "key_bolus_storage", defaultValue = ""),
    ActivePumpType(key = "active_pump_type", defaultValue = ""),
    ActivePumpSerialNumber(key = "active_pump_serial_number", defaultValue = ""),
    SmsOtpSecret("smscommunicator_otp_secret", defaultValue = ""),
    TotalBaseBasal("TBB", defaultValue = "10.00")
}

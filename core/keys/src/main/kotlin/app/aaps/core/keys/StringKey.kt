package app.aaps.core.keys

enum class StringKey(
    override val key: Int,
    override val defaultValue: String,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false
) : StringPreferenceKey {

    GeneralUnits(R.string.key_units, "mg/dl"),
    GeneralLanguage(R.string.key_language, "default", defaultedBySM = true),
    GeneralSkin(R.string.key_skin, ""),
    GeneralDarkMode(R.string.key_use_dark_mode, "dark", defaultedBySM = true),
    SafetyAge(R.string.key_safety_age, "adult"),
    LoopApsMode(R.string.key_aps_mode, "open" /* ApsMode.OPEN.name */),
    MaintenanceEmail(R.string.key_maintenance_logs_email, "logs@aaps.app", defaultedBySM = true, hideParentScreenIfHidden = true),
    MaintenanceIdentification(R.string.key_email_for_crash_report, "", defaultedBySM = true, hideParentScreenIfHidden = true),
    AutomationLocation(R.string.key_location, "PASSIVE", hideParentScreenIfHidden = true),

    SmsAllowedNumbers(R.string.key_smscommunicator_allowednumbers, ""),
    SmsOtpPassword(R.string.key_smscommunicator_otp_password, "", dependency = BooleanKey.SmsAllowRemoteCommands),

    VirtualPumpType(R.string.key_virtual_pump_type, "Generic AAPS"),

    NsClientUrl(R.string.key_nsclientinternal_url, ""),
    NsClientApiSecret(R.string.key_nsclientinternal_api_secret, ""),
    NsClientWifiSsids(R.string.key_ns_wifi_ssids, "", dependency = BooleanKey.NsClientUseWifi),
    NsClientAccessToken(R.string.key_ns_client_token, ""),
}
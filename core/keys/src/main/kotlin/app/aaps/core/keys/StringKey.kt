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
    GeneralPatientName(R.string.key_patient_name, ""),
    GeneralSkin(R.string.key_skin, ""),
    GeneralDarkMode(R.string.key_use_dark_mode, "dark", defaultedBySM = true),

    ProtectionMasterPassword(R.string.key_master_password, ""),
    ProtectionSettingsPassword(R.string.key_settings_password, ""),
    ProtectionSettingsPin(R.string.key_settings_pin, ""),
    ProtectionApplicationPassword(R.string.key_application_password, ""),
    ProtectionApplicationPin(R.string.key_application_pin, ""),
    ProtectionBolusPassword(R.string.key_bolus_password, ""),
    ProtectionBolusPin(R.string.key_bolus_pin, ""),

    SafetyAge(R.string.key_safety_age, "adult"),
    LoopApsMode(R.string.key_aps_mode, "open" /* ApsMode.OPEN.name */),
    MaintenanceEmail(R.string.key_maintenance_logs_email, "logs@aaps.app", defaultedBySM = true),
    MaintenanceIdentification(R.string.key_identification_for_crash_report, ""),
    AutomationLocation(R.string.key_location, "PASSIVE", hideParentScreenIfHidden = true),

    SmsAllowedNumbers(R.string.key_smscommunicator_allowednumbers, ""),
    SmsOtpPassword(R.string.key_smscommunicator_otp_password, "", dependency = BooleanKey.SmsAllowRemoteCommands),

    VirtualPumpType(R.string.key_virtual_pump_type, "Generic AAPS"),

    NsClientUrl(R.string.key_nsclientinternal_url, ""),
    NsClientApiSecret(R.string.key_nsclientinternal_api_secret, ""),
    NsClientWifiSsids(R.string.key_ns_wifi_ssids, "", dependency = BooleanKey.NsClientUseWifi),
    NsClientAccessToken(R.string.key_ns_client_token, ""),
    TidepoolUsername(R.string.key_tidepool_username, ""),
    TidepoolPassword(R.string.key_tidepool_password, ""),
}
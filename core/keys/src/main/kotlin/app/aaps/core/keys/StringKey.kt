package app.aaps.core.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.PreferenceEnabledCondition
import app.aaps.core.keys.interfaces.PreferenceVisibility
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.StringValidator

enum class StringKey(
    override val key: String,
    override val defaultValue: String,
    override val titleResId: Int,
    override val summaryResId: Int? = null,
    override val preferenceType: PreferenceType = PreferenceType.TEXT_FIELD,
    override val entries: Map<String, Int> = emptyMap(),
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val isPassword: Boolean = false,
    override val isPin: Boolean = false,
    override val isHashed: Boolean = false,
    override val exportable: Boolean = true,
    override val validator: StringValidator = StringValidator.NONE,
    override val visibility: PreferenceVisibility = PreferenceVisibility.ALWAYS,
    override val enabledCondition: PreferenceEnabledCondition = PreferenceEnabledCondition.ALWAYS
) : StringPreferenceKey {

    GeneralUnits(
        key = "units",
        defaultValue = "mg/dl",
        titleResId = R.string.pref_title_units,
        preferenceType = PreferenceType.LIST,
        entries = mapOf(
            "mg/dl" to R.string.units_mgdl,
            "mmol" to R.string.units_mmol
        )
    ),
    GeneralLanguage(
        key = "language",
        defaultValue = "default",
        titleResId = R.string.pref_title_language,
        preferenceType = PreferenceType.LIST,
        entries = mapOf(
            "default" to R.string.lang_default,
            "en" to R.string.lang_en,
            "af" to R.string.lang_af,
            "bg" to R.string.lang_bg,
            "cs" to R.string.lang_cs,
            "de" to R.string.lang_de,
            "dk" to R.string.lang_dk,
            "fr" to R.string.lang_fr,
            "nl" to R.string.lang_nl,
            "es" to R.string.lang_es,
            "el" to R.string.lang_el,
            "ga" to R.string.lang_ga,
            "it" to R.string.lang_it,
            "ko" to R.string.lang_ko,
            "lt" to R.string.lang_lt,
            "nb" to R.string.lang_nb,
            "pl" to R.string.lang_pl,
            "pt" to R.string.lang_pt,
            "pt_BR" to R.string.lang_pt_br,
            "ro" to R.string.lang_ro,
            "ru" to R.string.lang_ru,
            "sk" to R.string.lang_sk,
            "sv" to R.string.lang_sv,
            "tr" to R.string.lang_tr,
            "zh_TW" to R.string.lang_zh_tw,
            "zh_CN" to R.string.lang_zh_cn
        ),
        defaultedBySM = true
    ),
    GeneralPatientName(
        key = "patient_name",
        defaultValue = "",
        titleResId = R.string.pref_title_patient_name,
        summaryResId = R.string.pref_summary_patient_name,
        validator = StringValidator.personName()
    ),
    GeneralSkin(key = "skin", defaultValue = "", titleResId = R.string.pref_title_skin, preferenceType = PreferenceType.LIST),
    GeneralDarkMode(
        key = "use_dark_mode",
        defaultValue = "dark",
        titleResId = R.string.pref_title_app_color_scheme,
        summaryResId = R.string.pref_summary_theme_switcher,
        preferenceType = PreferenceType.LIST,
        entries = mapOf(
            "dark" to R.string.pref_dark_theme,
            "light" to R.string.pref_light_theme,
            "system" to R.string.pref_follow_system_theme
        ),
        defaultedBySM = true
    ),

    AapsDirectoryUri(key = "aaps_directory", defaultValue = "", titleResId = R.string.pref_title_aaps_directory),

    ProtectionMasterPassword(key = "master_password", defaultValue = "", titleResId = R.string.pref_title_master_password, isPassword = true, isHashed = true),
    ProtectionSettingsPassword(
        key = "settings_password", defaultValue = "", titleResId = R.string.pref_title_settings_password, isPassword = true, isHashed = true,
        visibility = PreferenceVisibility.intEquals({ IntKey.ProtectionTypeSettings }, ProtectionType.CUSTOM_PASSWORD.ordinal)
    ),
    ProtectionSettingsPin(
        key = "settings_pin", defaultValue = "", titleResId = R.string.pref_title_settings_pin, isPin = true, isHashed = true,
        visibility = PreferenceVisibility.intEquals({ IntKey.ProtectionTypeSettings }, ProtectionType.CUSTOM_PIN.ordinal)
    ),
    ProtectionApplicationPassword(
        key = "application_password", defaultValue = "", titleResId = R.string.pref_title_application_password, isPassword = true, isHashed = true,
        visibility = PreferenceVisibility.intEquals({ IntKey.ProtectionTypeApplication }, ProtectionType.CUSTOM_PASSWORD.ordinal)
    ),
    ProtectionApplicationPin(
        key = "application_pin", defaultValue = "", titleResId = R.string.pref_title_application_pin, isPin = true, isHashed = true,
        visibility = PreferenceVisibility.intEquals({ IntKey.ProtectionTypeApplication }, ProtectionType.CUSTOM_PIN.ordinal)
    ),
    ProtectionBolusPassword(
        key = "bolus_password", defaultValue = "", titleResId = R.string.pref_title_bolus_password, isPassword = true, isHashed = true,
        visibility = PreferenceVisibility.intEquals({ IntKey.ProtectionTypeBolus }, ProtectionType.CUSTOM_PASSWORD.ordinal)
    ),
    ProtectionBolusPin(
        key = "bolus_pin", defaultValue = "", titleResId = R.string.pref_title_bolus_pin, isPin = true, isHashed = true,
        visibility = PreferenceVisibility.intEquals({ IntKey.ProtectionTypeBolus }, ProtectionType.CUSTOM_PIN.ordinal)
    ),

    OverviewCopySettingsFromNs(key = "statuslights_copy_ns", defaultValue = "", titleResId = R.string.pref_title_copy_settings_from_ns),

    SafetyAge(key = "age", defaultValue = "adult", titleResId = R.string.pref_title_patient_age, preferenceType = PreferenceType.LIST),
    MaintenanceEmail(
        key = "maintenance_logs_email",
        defaultValue = "logs@aaps.app",
        titleResId = R.string.maintenance_email,
        defaultedBySM = true,
        validator = StringValidator.email()
    ),
    MaintenanceIdentification(key = "email_for_crash_report", defaultValue = "", titleResId = R.string.pref_title_identification),
    AutomationLocation(
        key = "location",
        defaultValue = "PASSIVE",
        titleResId = R.string.pref_title_automation_location,
        preferenceType = PreferenceType.LIST,
        entries = mapOf(
            "PASSIVE" to R.string.automation_location_passive,
            "NETWORK" to R.string.automation_location_network,
            "GPS" to R.string.automation_location_gps
        ),
        hideParentScreenIfHidden = true
    ),

    SmsAllowedNumbers(
        key = "smscommunicator_allowednumbers",
        defaultValue = "",
        titleResId = R.string.smscommunicator_allowednumbers,
        summaryResId = R.string.smscommunicator_allowednumbers_summary,
        validator = StringValidator.multiPhone()
    ),
    SmsOtpPassword(
        key = "smscommunicator_otp_password",
        defaultValue = "",
        titleResId = R.string.smscommunicator_otp_pin,
        summaryResId = R.string.smscommunicator_otp_pin_summary,
        dependency = BooleanKey.SmsAllowRemoteCommands,
        isPassword = true,
        validator = StringValidator.pinStrength()
    ),

    VirtualPumpType(key = "virtualpump_type", defaultValue = "Generic AAPS", titleResId = R.string.pref_title_virtual_pump_type, preferenceType = PreferenceType.LIST),

    NsClientUrl(
        key = "nsclientinternal_url",
        defaultValue = "",
        titleResId = R.string.ns_client_url_title,
        summaryResId = R.string.ns_client_url_summary,
        validator = StringValidator.httpsUrl()
    ),
    NsClientApiSecret(
        key = "nsclientinternal_api_secret",
        defaultValue = "",
        titleResId = R.string.ns_client_secret_title,
        summaryResId = R.string.ns_client_secret_summary,
        isPassword = true,
        validator = StringValidator.minLength(12)
    ),
    NsClientWifiSsids(
        key = "ns_wifi_ssids",
        defaultValue = "",
        titleResId = R.string.ns_wifi_ssids,
        summaryResId = R.string.ns_wifi_ssids_summary,
        dependency = BooleanKey.NsClientUseWifi
    ),
    NsClientAccessToken(
        key = "nsclient_token",
        defaultValue = "",
        titleResId = R.string.nsclient_token_title,
        summaryResId = R.string.nsclient_token_summary,
        isPassword = true,
        validator = StringValidator.minLength(17)
    ),

}

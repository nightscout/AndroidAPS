package app.aaps.core.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.ElementVisibility
import app.aaps.core.keys.interfaces.PreferenceEnabledCondition
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.StringValidator
import app.aaps.core.keys.interfaces.SyncChannel
import app.aaps.core.keys.interfaces.SyncDirection
import app.aaps.core.keys.interfaces.SyncSpec
import app.aaps.core.keys.interfaces.TextRef

enum class StringKey(
    override val key: String,
    override val defaultValue: String,
    override val title: TextRef,
    override val summary: TextRef? = null,
    override val preferenceType: PreferenceType = PreferenceType.TEXT_FIELD,
    private val entriesRefs: Map<String, TextRef> = emptyMap(),
    /**
     * Entry labels that are not a resource in this module. Only the units preference needs this:
     * "mg/dL" and "mmol/L" read the same in every language, and the `units_*` strings themselves
     * now live in `:core:ui`, which this module cannot depend on.
     */
    private val entriesLiterals: Map<String, String> = emptyMap(),
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
    override val visibility: ElementVisibility = ElementVisibility.ALWAYS,
    override val enabledCondition: PreferenceEnabledCondition = PreferenceEnabledCondition.ALWAYS,
    override val sync: SyncSpec? = null
) : StringPreferenceKey {

    GeneralUnits(
        key = "units",
        defaultValue = "mg/dl",
        title = KeysStrings.pref_title_units,
        preferenceType = PreferenceType.LIST,
        entriesLiterals = mapOf(
            "mg/dl" to "mg/dL",
            "mmol" to "mmol/L"
        ),
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    GeneralLanguage(
        key = "language",
        defaultValue = "default",
        title = KeysStrings.pref_title_language,
        preferenceType = PreferenceType.LIST,
        entriesRefs = mapOf(
            "default" to KeysStrings.lang_default,
            "en" to KeysStrings.lang_en,
            "af" to KeysStrings.lang_af,
            "bg" to KeysStrings.lang_bg,
            "cs" to KeysStrings.lang_cs,
            "de" to KeysStrings.lang_de,
            "dk" to KeysStrings.lang_dk,
            "fr" to KeysStrings.lang_fr,
            "nl" to KeysStrings.lang_nl,
            "es" to KeysStrings.lang_es,
            "el" to KeysStrings.lang_el,
            "ga" to KeysStrings.lang_ga,
            "it" to KeysStrings.lang_it,
            "ko" to KeysStrings.lang_ko,
            "lt" to KeysStrings.lang_lt,
            "nb" to KeysStrings.lang_nb,
            "pl" to KeysStrings.lang_pl,
            "pt" to KeysStrings.lang_pt,
            "pt_BR" to KeysStrings.lang_pt_br,
            "ro" to KeysStrings.lang_ro,
            "ru" to KeysStrings.lang_ru,
            "sk" to KeysStrings.lang_sk,
            "sv" to KeysStrings.lang_sv,
            "tr" to KeysStrings.lang_tr,
            "zh_TW" to KeysStrings.lang_zh_tw,
            "zh_CN" to KeysStrings.lang_zh_cn
        ),
        defaultedBySM = true
    ),
    GeneralPatientName(
        key = "patient_name",
        defaultValue = "",
        title = KeysStrings.pref_title_patient_name,
        summary = KeysStrings.pref_summary_patient_name,
        validator = StringValidator.personName()
    ),
    GeneralDarkMode(
        key = "use_dark_mode",
        defaultValue = "dark",
        title = KeysStrings.pref_title_app_color_scheme,
        summary = KeysStrings.pref_summary_theme_switcher,
        preferenceType = PreferenceType.LIST,
        entriesRefs = mapOf(
            "dark" to KeysStrings.pref_dark_theme,
            "light" to KeysStrings.pref_light_theme,
            "system" to KeysStrings.pref_follow_system_theme
        ),
        defaultedBySM = true
    ),

    AapsDirectoryUri(key = "aaps_directory", defaultValue = "", title = KeysStrings.pref_title_aaps_directory),

    ProtectionMasterPassword(key = "master_password", defaultValue = "", title = KeysStrings.pref_title_master_password, isPassword = true, isHashed = true),
    ProtectionSettingsPassword(
        key = "settings_password", defaultValue = "", title = KeysStrings.pref_title_settings_password, isPassword = true, isHashed = true,
        visibility = ElementVisibility.intEquals({ IntKey.ProtectionTypeSettings }, ProtectionType.CUSTOM_PASSWORD.ordinal)
    ),
    ProtectionSettingsPin(
        key = "settings_pin", defaultValue = "", title = KeysStrings.pref_title_settings_pin, isPin = true, isHashed = true,
        visibility = ElementVisibility.intEquals({ IntKey.ProtectionTypeSettings }, ProtectionType.CUSTOM_PIN.ordinal)
    ),
    ProtectionApplicationPassword(
        key = "application_password", defaultValue = "", title = KeysStrings.pref_title_application_password, isPassword = true, isHashed = true,
        visibility = ElementVisibility.intEquals({ IntKey.ProtectionTypeApplication }, ProtectionType.CUSTOM_PASSWORD.ordinal)
    ),
    ProtectionApplicationPin(
        key = "application_pin", defaultValue = "", title = KeysStrings.pref_title_application_pin, isPin = true, isHashed = true,
        visibility = ElementVisibility.intEquals({ IntKey.ProtectionTypeApplication }, ProtectionType.CUSTOM_PIN.ordinal)
    ),
    ProtectionBolusPassword(
        key = "bolus_password", defaultValue = "", title = KeysStrings.pref_title_bolus_password, isPassword = true, isHashed = true,
        visibility = ElementVisibility.intEquals({ IntKey.ProtectionTypeBolus }, ProtectionType.CUSTOM_PASSWORD.ordinal)
    ),
    ProtectionBolusPin(
        key = "bolus_pin", defaultValue = "", title = KeysStrings.pref_title_bolus_pin, isPin = true, isHashed = true,
        visibility = ElementVisibility.intEquals({ IntKey.ProtectionTypeBolus }, ProtectionType.CUSTOM_PIN.ordinal)
    ),

    SafetyAge(key = "age", defaultValue = "adult", title = KeysStrings.pref_title_patient_age, preferenceType = PreferenceType.LIST, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    MaintenanceEmail(
        key = "maintenance_logs_email",
        defaultValue = "logs@aaps.app",
        title = KeysStrings.maintenance_email,
        defaultedBySM = true,
        validator = StringValidator.email()
    ),
    MaintenanceIdentification(key = "email_for_crash_report", defaultValue = "", title = KeysStrings.pref_title_identification),
    AutomationLocation(
        key = "location",
        defaultValue = "PASSIVE",
        title = KeysStrings.pref_title_automation_location,
        preferenceType = PreferenceType.LIST,
        entriesRefs = mapOf(
            "PASSIVE" to KeysStrings.automation_location_passive,
            "NETWORK" to KeysStrings.automation_location_network,
            "GPS" to KeysStrings.automation_location_gps
        ),
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),

    SmsAllowedNumbers(
        key = "smscommunicator_allowednumbers",
        defaultValue = "",
        title = KeysStrings.smscommunicator_allowednumbers,
        summary = KeysStrings.smscommunicator_allowednumbers_summary,
        validator = StringValidator.multiPhone()
    ),
    SmsOtpPassword(
        key = "smscommunicator_otp_password",
        defaultValue = "",
        title = KeysStrings.smscommunicator_otp_pin,
        summary = KeysStrings.smscommunicator_otp_pin_summary,
        dependency = BooleanKey.SmsAllowRemoteCommands,
        isPassword = true,
        validator = StringValidator.pinStrength()
    ),

    VirtualPumpType(key = "virtualpump_type", defaultValue = "Generic AAPS", title = KeysStrings.pref_title_virtual_pump_type, preferenceType = PreferenceType.LIST),

    NsClientUrl(
        key = "nsclientinternal_url",
        defaultValue = "",
        title = KeysStrings.ns_client_url_title,
        summary = KeysStrings.ns_client_url_summary,
        validator = StringValidator.httpsUrl()
    ),
    NsClientApiSecret(
        key = "nsclientinternal_api_secret",
        defaultValue = "",
        title = KeysStrings.ns_client_secret_title,
        summary = KeysStrings.ns_client_secret_summary,
        isPassword = true,
        validator = StringValidator.minLength(12)
    ),
    NsClientWifiSsids(
        key = "ns_wifi_ssids",
        defaultValue = "",
        title = KeysStrings.ns_wifi_ssids,
        summary = KeysStrings.ns_wifi_ssids_summary,
        dependency = BooleanKey.NsClientUseWifi
    ),
    NsClientAccessToken(
        key = "nsclient_token",
        defaultValue = "",
        title = KeysStrings.nsclient_token_title,
        summary = KeysStrings.nsclient_token_summary,
        isPassword = true,
        validator = StringValidator.minLength(17)
    ),

    ;

    override val entries: Map<String, TextRef> =
        entriesRefs + entriesLiterals.mapValues { TextRef.Literal(it.value) }
}

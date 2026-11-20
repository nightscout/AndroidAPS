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
     * Entry labels that are not a resource and are the same in every language: "mg/dL" and "mmol/L"
     * (the `units_*` strings live in `:core:ui`, which this module cannot depend on), and the language
     * names. They follow the [entriesRefs] entries in the list.
     */
    private val entriesLiterals: Map<String, String> = emptyMap(),
    override val defaultedBySM: Boolean = false,
    override val dependency: BooleanPreferenceKey? = null,
    override val isPassword: Boolean = false,
    override val isPin: Boolean = false,
    override val isHashed: Boolean = false,
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
        // Not translated on purpose: a user who selected a language they cannot read must still be able
        // to find this setting and their own language. Each language is written in that language.
        title = TextRef.Literal("Language"),
        preferenceType = PreferenceType.LIST,
        entriesRefs = mapOf("default" to KeysStrings.lang_default),
        // English first, then the Latin script in alphabetical order, then the other scripts
        entriesLiterals = mapOf(
            "en" to "English",
            "af" to "Afrikaans",
            "cs" to "Čeština",
            "dk" to "Dansk",
            "de" to "Deutsch",
            "es" to "Español",
            "fr" to "Français",
            "ga" to "Gaeilge",
            "it" to "Italiano",
            "lt" to "Lietuvių",
            "nl" to "Nederlands",
            "nb" to "Norsk bokmål",
            "pl" to "Polski",
            "pt" to "Português",
            "pt_BR" to "Português (Brasil)",
            "ro" to "Română",
            "sk" to "Slovenčina",
            "sv" to "Svenska",
            "tr" to "Türkçe",
            "el" to "Ελληνικά",
            "bg" to "Български",
            "ru" to "Русский",
            "he" to "עברית",
            "ko" to "한국어",
            "zh_TW" to "繁體中文",
            "zh_CN" to "简体中文"
        ),
        defaultedBySM = true,
        // Android 12 has no app languages: AAPS follows the phone there
        visibility = ElementVisibility { it.supportsAppLanguage }
    ),
    // Not the patient: the person using THIS phone. It names this AAPS instance (export file names,
    // the OTP label, the watch), so it stays device-local and carries no SyncSpec - a client must not
    // take the master's name. The stored key keeps its old "patient_name" spelling on purpose, so no
    // installation loses its value.
    GeneralInstanceName(
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

    /**
     * Which embedded Watch Face Format face the wear app installs through Watch Face Push on a
     * Wear OS 6+ watch. Watch Face Push gives an app one slot, so this is a choice, not a set.
     * Values in [PushedWatchfaceId]. Older watches receive and keep the value but cannot act on it.
     *
     * Not in any preference screen: the Wear plugin's main page shows it as a card, and only once
     * the watch has reported that it has Watch Face Push. No summary for that reason.
     */
    WearPushedWatchface(
        key = "wear_pushed_watchface",
        defaultValue = PushedWatchfaceId.CWF,
        title = KeysStrings.pref_title_wear_pushed_watchface,
        preferenceType = PreferenceType.LIST,
        entriesRefs = mapOf(
            PushedWatchfaceId.WFS to KeysStrings.wear_pushed_watchface_wfs,
            PushedWatchfaceId.CWF to KeysStrings.wear_pushed_watchface_cwf
        )
    ),

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

    // Sound of each CGM alarm: "default" (the standard AAPS alarm sound), "none" (notification only),
    // or a Trio sound (see core/ui/SOUNDS.md). The value of a Trio sound is its file name. Urgent low
    // has no "none".
    AlertUrgentLowSound(
        key = "alert_cgm_urgent_low_sound",
        defaultValue = "default",
        title = KeysStrings.pref_title_alert_cgm_sound,
        summary = KeysStrings.pref_summary_alert_cgm_sound,
        preferenceType = PreferenceType.LIST,
        entriesRefs = cgmAlarmSoundEntries("ulg", allowNone = false),
        dependency = BooleanKey.AlertUrgentLow,
    ),
    AlertUrgentLowSoonSound(
        key = "alert_cgm_urgent_low_soon_sound",
        defaultValue = "default",
        title = KeysStrings.pref_title_alert_cgm_sound,
        summary = KeysStrings.pref_summary_alert_cgm_sound,
        preferenceType = PreferenceType.LIST,
        entriesRefs = cgmAlarmSoundEntries("lgs", allowNone = true),
        dependency = BooleanKey.AlertUrgentLowSoon,
    ),
    AlertHypoSound(
        key = "alert_cgm_low_sound",
        defaultValue = "default",
        title = KeysStrings.pref_title_alert_cgm_sound,
        summary = KeysStrings.pref_summary_alert_cgm_sound,
        preferenceType = PreferenceType.LIST,
        entriesRefs = cgmAlarmSoundEntries("lg", allowNone = true),
        dependency = BooleanKey.AlertHypo,
    ),
    AlertRapidFallSound(
        key = "alert_cgm_fall_rate_sound",
        defaultValue = "default",
        title = KeysStrings.pref_title_alert_cgm_sound,
        summary = KeysStrings.pref_summary_alert_cgm_sound,
        preferenceType = PreferenceType.LIST,
        entriesRefs = cgmAlarmSoundEntries("lgs", allowNone = true),
        dependency = BooleanKey.AlertRapidFall,
    ),
    AlertHyperSound(
        key = "alert_cgm_high_sound",
        defaultValue = "none",
        title = KeysStrings.pref_title_alert_cgm_sound,
        summary = KeysStrings.pref_summary_alert_cgm_sound,
        preferenceType = PreferenceType.LIST,
        entriesRefs = cgmAlarmSoundEntries("hg", allowNone = true),
        dependency = BooleanKey.AlertHyper,
    ),
    AlertRapidRiseSound(
        key = "alert_cgm_rise_rate_sound",
        defaultValue = "none",
        title = KeysStrings.pref_title_alert_cgm_sound,
        summary = KeysStrings.pref_summary_alert_cgm_sound,
        preferenceType = PreferenceType.LIST,
        entriesRefs = cgmAlarmSoundEntries("hg", allowNone = true),
        dependency = BooleanKey.AlertRapidRise,
    ),
    AlertCgmSensorFailedSound(
        key = "alert_cgm_sensor_failed_sound",
        defaultValue = "default",
        title = KeysStrings.pref_title_alert_cgm_sound,
        summary = KeysStrings.pref_summary_alert_cgm_sound,
        preferenceType = PreferenceType.LIST,
        entriesRefs = cgmAlarmSoundEntries("dcr", allowNone = true),
        dependency = BooleanKey.AlertCgmSensorFailed,
    ),
    AlertCgmSensorExpiredSound(
        key = "alert_cgm_sensor_ended_sound",
        defaultValue = "default",
        title = KeysStrings.pref_title_alert_cgm_sound,
        summary = KeysStrings.pref_summary_alert_cgm_sound,
        preferenceType = PreferenceType.LIST,
        entriesRefs = cgmAlarmSoundEntries("dts", allowNone = true),
        dependency = BooleanKey.AlertCgmSensorExpired,
    ),
    AlertCgmSignalLostSound(
        key = "alert_cgm_signal_loss_sound",
        defaultValue = "none",
        title = KeysStrings.pref_title_alert_cgm_sound,
        summary = KeysStrings.pref_summary_alert_cgm_sound,
        preferenceType = PreferenceType.LIST,
        entriesRefs = cgmAlarmSoundEntries("dts", allowNone = true),
        dependency = BooleanKey.AlertCgmSignalLost,
    ),
    AlertCgmSensorIssueSound(
        key = "alert_cgm_sensor_issue_sound",
        defaultValue = "none",
        title = KeysStrings.pref_title_alert_cgm_sound,
        summary = KeysStrings.pref_summary_alert_cgm_sound,
        preferenceType = PreferenceType.LIST,
        entriesRefs = cgmAlarmSoundEntries("dts", allowNone = true),
        dependency = BooleanKey.AlertCgmSensorIssue,
    ),

    ;

    override val entries: Map<String, TextRef> =
        entriesRefs + entriesLiterals.mapValues { TextRef.Literal(it.value) }
}

/**
 * The sound choices of one CGM alarm: the AAPS sound, no sound (unless [allowNone] is false), the six
 * Trio sounds made for [trioSet] (acoustic and electronic, mild to harsh), then the eight Trio tones.
 * The value of a Trio sound is its file name in `core/ui/res/raw`, which `AlarmSound.fileName` holds.
 */
private fun cgmAlarmSoundEntries(trioSet: String, allowNone: Boolean): Map<String, TextRef> = buildMap {
    put("default", KeysStrings.alert_sound_default)
    if (allowNone) put("none", KeysStrings.alert_sound_none)
    put("trio_acoustic_${trioSet}_mild", KeysStrings.alert_sound_trio_acoustic_mild)
    put("trio_acoustic_${trioSet}_medium", KeysStrings.alert_sound_trio_acoustic_medium)
    put("trio_acoustic_${trioSet}_harsh", KeysStrings.alert_sound_trio_acoustic_harsh)
    put("trio_electronic_${trioSet}_mild", KeysStrings.alert_sound_trio_electronic_mild)
    put("trio_electronic_${trioSet}_medium", KeysStrings.alert_sound_trio_electronic_medium)
    put("trio_electronic_${trioSet}_harsh", KeysStrings.alert_sound_trio_electronic_harsh)
    put("trio_tone_glass_bell", KeysStrings.alert_sound_trio_glass_bell)
    put("trio_tone_harp", KeysStrings.alert_sound_trio_harp)
    put("trio_tone_meow", KeysStrings.alert_sound_trio_meow)
    put("trio_tone_saw_wave", KeysStrings.alert_sound_trio_saw_wave)
    put("trio_tone_hard_saw_wave", KeysStrings.alert_sound_trio_hard_saw_wave)
    put("trio_tone_sine_wave", KeysStrings.alert_sound_trio_sine_wave)
    put("trio_tone_open_can", KeysStrings.alert_sound_trio_open_can)
    put("trio_tone_open_wrapper", KeysStrings.alert_sound_trio_open_wrapper)
}

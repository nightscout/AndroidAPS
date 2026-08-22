package app.aaps.core.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.ElementVisibility
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.PreferenceEnabledCondition
import app.aaps.core.keys.interfaces.SyncChannel
import app.aaps.core.keys.interfaces.SyncDirection
import app.aaps.core.keys.interfaces.SyncSpec
import app.aaps.core.keys.interfaces.TextRef

enum class IntKey(
    override val key: String,
    override val defaultValue: Int,
    override val min: Int,
    override val max: Int,
    override val title: TextRef,
    override val summary: TextRef? = null,
    override val preferenceType: PreferenceType = PreferenceType.TEXT_FIELD,
    private val entriesRefs: Map<Int, TextRef> = emptyMap(),
    override val defaultedBySM: Boolean = false,
    override val calculatedDefaultValue: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val engineeringModeOnly: Boolean = false,
    override val exportable: Boolean = true,
    override val visibility: ElementVisibility = ElementVisibility.ALWAYS,
    override val enabledCondition: PreferenceEnabledCondition = PreferenceEnabledCondition.ALWAYS,
    override val unitType: UnitType = UnitType.NONE,
    override val sync: SyncSpec? = null
) : IntPreferenceKey {

    OverviewCarbsButtonIncrement1(
        key = "carbs_button_increment_1",
        defaultValue = 5,
        min = -50,
        max = 50,
        title = KeysStrings.pref_title_carbs_button_increment_1,
        summary = KeysStrings.carb_increment_button_message,
        defaultedBySM = true,
        dependency = BooleanKey.OverviewShowCarbsButton,
        unitType = UnitType.GRAMS,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    OverviewCarbsButtonIncrement2(
        key = "carbs_button_increment_2",
        defaultValue = 10,
        min = -50,
        max = 50,
        title = KeysStrings.pref_title_carbs_button_increment_2,
        summary = KeysStrings.carb_increment_button_message,
        defaultedBySM = true,
        dependency = BooleanKey.OverviewShowCarbsButton,
        unitType = UnitType.GRAMS,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    OverviewCarbsButtonIncrement3(
        key = "carbs_button_increment_3",
        defaultValue = 20,
        min = -50,
        max = 50,
        title = KeysStrings.pref_title_carbs_button_increment_3,
        summary = KeysStrings.carb_increment_button_message,
        defaultedBySM = true,
        dependency = BooleanKey.OverviewShowCarbsButton,
        unitType = UnitType.GRAMS,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),

    OverviewCageWarning(
        key = "statuslights_cage_warning",
        defaultValue = 48,
        min = 24,
        max = 240,
        title = KeysStrings.pref_title_cage_warning,
        defaultedBySM = true,
        unitType = UnitType.HOURS,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    OverviewCageCritical(
        key = "statuslights_cage_critical",
        defaultValue = 72,
        min = 24,
        max = 240,
        title = KeysStrings.pref_title_cage_critical,
        defaultedBySM = true,
        unitType = UnitType.HOURS,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    OverviewIageWarning(
        key = "statuslights_iage_warning",
        defaultValue = 72,
        min = 24,
        max = 240,
        title = KeysStrings.pref_title_iage_warning,
        defaultedBySM = true,
        visibility = ElementVisibility.NON_PATCH_PUMP,
        unitType = UnitType.HOURS,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    OverviewIageCritical(
        key = "statuslights_iage_critical",
        defaultValue = 144,
        min = 24,
        max = 240,
        title = KeysStrings.pref_title_iage_critical,
        defaultedBySM = true,
        visibility = ElementVisibility.NON_PATCH_PUMP,
        unitType = UnitType.HOURS,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    OverviewSageWarning(
        key = "statuslights_sage_warning",
        defaultValue = 216,
        min = 24,
        max = 720,
        title = KeysStrings.pref_title_sage_warning,
        defaultedBySM = true,
        unitType = UnitType.HOURS,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    OverviewSageCritical(
        key = "statuslights_sage_critical",
        defaultValue = 240,
        min = 24,
        max = 720,
        title = KeysStrings.pref_title_sage_critical,
        defaultedBySM = true,
        unitType = UnitType.HOURS,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    OverviewSbatWarning(
        key = "statuslights_sbat_warning",
        defaultValue = 25,
        min = 0,
        max = 100,
        title = KeysStrings.pref_title_sbat_warning,
        defaultedBySM = true,
        unitType = UnitType.PERCENT,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    OverviewSbatCritical(
        key = "statuslights_sbat_critical",
        defaultValue = 5,
        min = 0,
        max = 100,
        title = KeysStrings.pref_title_sbat_critical,
        defaultedBySM = true,
        unitType = UnitType.PERCENT,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    OverviewBageWarning(
        key = "statuslights_bage_warning",
        defaultValue = 216,
        min = 24,
        max = 1000,
        title = KeysStrings.pref_title_bage_warning,
        defaultedBySM = true,
        unitType = UnitType.HOURS,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    OverviewBageCritical(
        key = "statuslights_bage_critical",
        defaultValue = 240,
        min = 24,
        max = 1000,
        title = KeysStrings.pref_title_bage_critical,
        defaultedBySM = true,
        unitType = UnitType.HOURS,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    OverviewResWarning(
        key = "statuslights_res_warning",
        defaultValue = 80,
        min = 0,
        max = 300,
        title = KeysStrings.pref_title_res_warning,
        defaultedBySM = true,
        unitType = UnitType.INSULIN_INT,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    OverviewResCritical(
        key = "statuslights_res_critical",
        defaultValue = 10,
        min = 0,
        max = 300,
        title = KeysStrings.pref_title_res_critical,
        defaultedBySM = true,
        unitType = UnitType.INSULIN_INT,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    OverviewBattWarning(
        key = "statuslights_bat_warning",
        defaultValue = 51,
        min = 0,
        max = 100,
        title = KeysStrings.pref_title_batt_warning,
        defaultedBySM = true,
        unitType = UnitType.PERCENT,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    OverviewBattCritical(
        key = "statuslights_bat_critical",
        defaultValue = 26,
        min = 0,
        max = 100,
        title = KeysStrings.pref_title_batt_critical,
        defaultedBySM = true,
        unitType = UnitType.PERCENT,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    OverviewBolusPercentage(
        key = "boluswizard_percentage",
        defaultValue = 100,
        min = 10,
        max = 100,
        title = KeysStrings.pref_title_bolus_percentage,
        summary = KeysStrings.deliverpartofboluswizard,
        unitType = UnitType.PERCENT,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    OverviewResetBolusPercentageTime(
        key = "key_reset_boluswizard_percentage_time",
        defaultValue = 16,
        min = 6,
        max = 120,
        title = KeysStrings.pref_title_reset_bolus_percentage_time,
        summary = KeysStrings.deliver_part_of_boluswizard_reset_time,
        defaultedBySM = true,
        engineeringModeOnly = true,
        unitType = UnitType.MIN,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ProtectionTimeout(
        key = "protection_timeout",
        defaultValue = 1,
        min = 0,
        max = 180,
        title = KeysStrings.pref_title_protection_timeout,
        defaultedBySM = true,
        unitType = UnitType.SEC,
        visibility = ElementVisibility.stringNotEmpty { StringKey.ProtectionMasterPassword }
    ),

    // Protection types sorted by level: 0 (Application) → 1 (Bolus) → 2 (Settings)
    // Application is independent; Bolus requires Settings to be set
    ProtectionTypeApplication(
        key = "application_protection",
        defaultValue = ProtectionType.NONE.ordinal,
        min = ProtectionType.NONE.ordinal,
        max = ProtectionType.CUSTOM_PIN.ordinal,
        title = KeysStrings.pref_title_protection_type_application,
        summary = KeysStrings.pref_summary_protection_type_application,
        preferenceType = PreferenceType.LIST,
        entriesRefs = mapOf(
            ProtectionType.NONE.ordinal to KeysStrings.noprotection,
            ProtectionType.BIOMETRIC.ordinal to KeysStrings.biometric,
            ProtectionType.MASTER_PASSWORD.ordinal to KeysStrings.master_password,
            ProtectionType.CUSTOM_PASSWORD.ordinal to KeysStrings.custom_password,
            ProtectionType.CUSTOM_PIN.ordinal to KeysStrings.custom_pin
        ),
        visibility = ElementVisibility.stringNotEmpty { StringKey.ProtectionMasterPassword }
    ),
    ProtectionTypeBolus(
        key = "bolus_protection",
        defaultValue = ProtectionType.NONE.ordinal,
        min = ProtectionType.NONE.ordinal,
        max = ProtectionType.CUSTOM_PIN.ordinal,
        title = KeysStrings.pref_title_protection_type_bolus,
        summary = KeysStrings.pref_summary_protection_type_bolus,
        preferenceType = PreferenceType.LIST,
        entriesRefs = mapOf(
            ProtectionType.NONE.ordinal to KeysStrings.noprotection,
            ProtectionType.BIOMETRIC.ordinal to KeysStrings.biometric,
            ProtectionType.MASTER_PASSWORD.ordinal to KeysStrings.master_password,
            ProtectionType.CUSTOM_PASSWORD.ordinal to KeysStrings.custom_password,
            ProtectionType.CUSTOM_PIN.ordinal to KeysStrings.custom_pin
        ),
        visibility = ElementVisibility.stringNotEmpty { StringKey.ProtectionMasterPassword },
        enabledCondition = PreferenceEnabledCondition { ctx ->
            ctx.preferences.get(ProtectionTypeSettings) != ProtectionType.NONE.ordinal
        }
    ),
    ProtectionTypeSettings(
        key = "settings_protection",
        defaultValue = ProtectionType.NONE.ordinal,
        min = ProtectionType.NONE.ordinal,
        max = ProtectionType.CUSTOM_PIN.ordinal,
        title = KeysStrings.pref_title_protection_type_settings,
        summary = KeysStrings.pref_summary_protection_type_settings,
        preferenceType = PreferenceType.LIST,
        entriesRefs = mapOf(
            ProtectionType.NONE.ordinal to KeysStrings.noprotection,
            ProtectionType.BIOMETRIC.ordinal to KeysStrings.biometric,
            ProtectionType.MASTER_PASSWORD.ordinal to KeysStrings.master_password,
            ProtectionType.CUSTOM_PASSWORD.ordinal to KeysStrings.custom_password,
            ProtectionType.CUSTOM_PIN.ordinal to KeysStrings.custom_pin
        ),
        visibility = ElementVisibility.stringNotEmpty { StringKey.ProtectionMasterPassword }
    ),
    SafetyMaxCarbs(key = "treatmentssafety_maxcarbs", defaultValue = 48, min = 1, max = 200, title = KeysStrings.pref_title_max_carbs, unitType = UnitType.GRAMS, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    LoopOpenModeMinChange(
        key = "loop_openmode_min_change",
        defaultValue = 30,
        min = 0,
        max = 50,
        title = KeysStrings.pref_title_open_mode_min_change,
        summary = KeysStrings.loop_open_mode_min_change_summary,
        defaultedBySM = true,
        unitType = UnitType.PERCENT
    ),
    ApsMaxSmbFrequency(
        key = "smbinterval",
        defaultValue = 3,
        min = 1,
        max = 10,
        title = KeysStrings.pref_title_smb_frequency,
        defaultedBySM = true,
        dependency = BooleanKey.ApsUseSmb,
        unitType = UnitType.MIN,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsMaxMinutesOfBasalToLimitSmb(
        key = "smbmaxminutes",
        defaultValue = 30,
        min = 15,
        max = 120,
        title = KeysStrings.pref_title_smb_max_minutes,
        defaultedBySM = true,
        dependency = BooleanKey.ApsUseSmb,
        unitType = UnitType.MIN,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsUamMaxMinutesOfBasalToLimitSmb(
        key = "uamsmbmaxminutes", defaultValue = 30, min = 15, max = 120, title = KeysStrings.pref_title_uam_smb_max_minutes, summary = KeysStrings.uam_smb_max_minutes, defaultedBySM = true, dependency = BooleanKey.ApsUseSmb,
        visibility = ElementVisibility { it.preferences.get(BooleanKey.ApsUseUam) },
        unitType = UnitType.MIN,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsCarbsRequestThreshold(
        key = "carbsReqThreshold",
        defaultValue = 1,
        min = 1,
        max = 100,
        title = KeysStrings.pref_title_carbs_request_threshold,
        summary = KeysStrings.carbs_req_threshold_summary,
        defaultedBySM = true,
        unitType = UnitType.GRAMS,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAutoIsfHalfBasalExerciseTarget(
        key = "half_basal_exercise_target",
        defaultValue = 160,
        min = 120,
        max = 200,
        title = KeysStrings.pref_title_half_basal_exercise_target,
        summary = KeysStrings.half_basal_exercise_target_summary,
        defaultedBySM = true,
        unitType = UnitType.MGDL,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAutoIsfIobThPercent(
        key = "iob_threshold_percent",
        defaultValue = 100,
        min = 10,
        max = 100,
        title = KeysStrings.pref_title_iob_threshold_percent,
        summary = KeysStrings.openapsama_iob_threshold_percent_summary,
        defaultedBySM = true,
        unitType = UnitType.PERCENT,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsDynIsfAdjustmentFactor(
        key = "DynISFAdjust",
        defaultValue = 100,
        min = 1,
        max = 300,
        title = KeysStrings.pref_title_dynisf_adjustment_factor,
        summary = KeysStrings.dyn_isf_adjust_summary,
        dependency = BooleanKey.ApsUseDynamicSensitivity,
        unitType = UnitType.PERCENT,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    AutosensPeriod(
        key = "openapsama_autosens_period",
        defaultValue = 24,
        min = 4,
        max = 24,
        title = KeysStrings.pref_title_autosens_period,
        summary = KeysStrings.openapsama_autosens_period_summary,
        calculatedDefaultValue = true,
        unitType = UnitType.HOURS,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    MaintenanceLogsAmount(key = "maintenance_logs_amount", defaultValue = 2, min = 1, max = 10, title = KeysStrings.pref_title_logs_amount, defaultedBySM = true),
    AlertsStaleDataThreshold(
        key = "missed_bg_readings_threshold",
        defaultValue = 30,
        min = 15,
        max = 10000,
        title = KeysStrings.pref_title_stale_data_threshold,
        defaultedBySM = true,
        dependency = BooleanKey.AlertMissedBgReading,
        unitType = UnitType.MIN
    ),
    AlertsPumpUnreachableThreshold(
        key = "pump_unreachable_threshold",
        defaultValue = 30,
        min = 30,
        max = 300,
        title = KeysStrings.pref_title_pump_unreachable_threshold,
        defaultedBySM = true,
        dependency = BooleanKey.AlertPumpUnreachable,
        unitType = UnitType.MIN
    ),

    AutotuneDefaultTuneDays(key = "autotune_default_tune_days", defaultValue = 5, min = 1, max = 30, title = KeysStrings.pref_title_autotune_days, summary = KeysStrings.autotune_default_tune_days_summary, unitType = UnitType.DAYS),

    SmsRemoteBolusDistance(
        key = "smscommunicator_remotebolusmindistance",
        defaultValue = 15,
        min = 3,
        max = 60,
        title = KeysStrings.pref_title_sms_remote_bolus_distance,
        unitType = UnitType.MIN,
        // Enabled only when multiple phone numbers are configured (2FA requirement)
        enabledCondition = PreferenceEnabledCondition { ctx ->
            val allowedNumbers = ctx.preferences.get(StringKey.SmsAllowedNumbers)
            allowedNumbers.split(";").filter { it.trim().isNotEmpty() }.size >= 2
        }
    ),

    BgSourceRandomInterval(key = "randombg_interval_min", defaultValue = 5, min = 1, max = 15, title = KeysStrings.pref_title_random_bg_interval, defaultedBySM = true, unitType = UnitType.MIN),
    NsClientAlarmStaleData(key = "ns_alarm_stale_data_value", defaultValue = 16, min = 15, max = 120, title = KeysStrings.pref_title_alarm_stale_data, unitType = UnitType.MIN),
    NsClientUrgentAlarmStaleData(key = "ns_alarm_urgent_stale_data_value", defaultValue = 31, min = 30, max = 180, title = KeysStrings.pref_title_urgent_alarm_stale_data, unitType = UnitType.MIN),

    SiteRotationUserProfile(
        key = "site_rotation_user_profile",
        defaultValue = 0,
        min = 0,
        max = 2,
        title = KeysStrings.pref_title_site_rotation_profile,
        preferenceType = PreferenceType.LIST,
        entriesRefs = mapOf(
            0 to KeysStrings.site_rotation_profile_man,
            1 to KeysStrings.site_rotation_profile_woman,
            2 to KeysStrings.site_rotation_profile_child
        ),
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ;

    override val entries: Map<Int, TextRef> = entriesRefs
}

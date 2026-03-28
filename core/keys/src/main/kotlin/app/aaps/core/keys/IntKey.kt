package app.aaps.core.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.PreferenceEnabledCondition
import app.aaps.core.keys.interfaces.PreferenceVisibility

enum class IntKey(
    override val key: String,
    override val defaultValue: Int,
    override val min: Int,
    override val max: Int,
    override val titleResId: Int,
    override val summaryResId: Int? = null,
    override val preferenceType: PreferenceType = PreferenceType.TEXT_FIELD,
    override val entries: Map<Int, Int> = emptyMap(),
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
    override val visibility: PreferenceVisibility = PreferenceVisibility.ALWAYS,
    override val enabledCondition: PreferenceEnabledCondition = PreferenceEnabledCondition.ALWAYS,
    override val unitType: UnitType = UnitType.NONE
) : IntPreferenceKey {

    OverviewCarbsButtonIncrement1(
        key = "carbs_button_increment_1",
        defaultValue = 5,
        min = -50,
        max = 50,
        titleResId = R.string.pref_title_carbs_button_increment_1,
        summaryResId = R.string.carb_increment_button_message,
        defaultedBySM = true,
        dependency = BooleanKey.OverviewShowCarbsButton,
        unitType = UnitType.GRAMS
    ),
    OverviewCarbsButtonIncrement2(
        key = "carbs_button_increment_2",
        defaultValue = 10,
        min = -50,
        max = 50,
        titleResId = R.string.pref_title_carbs_button_increment_2,
        summaryResId = R.string.carb_increment_button_message,
        defaultedBySM = true,
        dependency = BooleanKey.OverviewShowCarbsButton,
        unitType = UnitType.GRAMS
    ),
    OverviewCarbsButtonIncrement3(
        key = "carbs_button_increment_3",
        defaultValue = 20,
        min = -50,
        max = 50,
        titleResId = R.string.pref_title_carbs_button_increment_3,
        summaryResId = R.string.carb_increment_button_message,
        defaultedBySM = true,
        dependency = BooleanKey.OverviewShowCarbsButton,
        unitType = UnitType.GRAMS
    ),

    @Deprecated(
        message = "Migrated to StringKey.TempTargetPresets JSON array. Used by legacy TempTargetDialog.",
        replaceWith = ReplaceWith("StringKey.TempTargetPresets")
    )
    OverviewEatingSoonDuration(key = "eatingsoon_duration", defaultValue = 45, min = 15, max = 120, titleResId = R.string.pref_title_eating_soon_duration, defaultedBySM = true, hideParentScreenIfHidden = true, unitType = UnitType.MIN),

    @Deprecated(
        message = "Migrated to StringKey.TempTargetPresets JSON array. Used by legacy TempTargetDialog.",
        replaceWith = ReplaceWith("StringKey.TempTargetPresets")
    )
    OverviewActivityDuration(key = "activity_duration", defaultValue = 90, min = 15, max = 600, titleResId = R.string.pref_title_activity_duration, defaultedBySM = true, unitType = UnitType.MIN),

    @Deprecated(
        message = "Migrated to StringKey.TempTargetPresets JSON array. Used by legacy TempTargetDialog.",
        replaceWith = ReplaceWith("StringKey.TempTargetPresets")
    )
    OverviewHypoDuration(key = "hypo_duration", defaultValue = 60, min = 15, max = 180, titleResId = R.string.pref_title_hypo_duration, defaultedBySM = true, unitType = UnitType.MIN),
    OverviewCageWarning(key = "statuslights_cage_warning", defaultValue = 48, min = 24, max = 240, titleResId = R.string.pref_title_cage_warning, defaultedBySM = true, unitType = UnitType.HOURS),
    OverviewCageCritical(key = "statuslights_cage_critical", defaultValue = 72, min = 24, max = 240, titleResId = R.string.pref_title_cage_critical, defaultedBySM = true, unitType = UnitType.HOURS),
    OverviewIageWarning(
        key = "statuslights_iage_warning",
        defaultValue = 72,
        min = 24,
        max = 240,
        titleResId = R.string.pref_title_iage_warning,
        defaultedBySM = true,
        visibility = PreferenceVisibility.NON_PATCH_PUMP,
        unitType = UnitType.HOURS
    ),
    OverviewIageCritical(
        key = "statuslights_iage_critical",
        defaultValue = 144,
        min = 24,
        max = 240,
        titleResId = R.string.pref_title_iage_critical,
        defaultedBySM = true,
        visibility = PreferenceVisibility.NON_PATCH_PUMP,
        unitType = UnitType.HOURS
    ),
    OverviewSageWarning(key = "statuslights_sage_warning", defaultValue = 216, min = 24, max = 720, titleResId = R.string.pref_title_sage_warning, defaultedBySM = true, unitType = UnitType.HOURS),
    OverviewSageCritical(key = "statuslights_sage_critical", defaultValue = 240, min = 24, max = 720, titleResId = R.string.pref_title_sage_critical, defaultedBySM = true, unitType = UnitType.HOURS),
    OverviewSbatWarning(key = "statuslights_sbat_warning", defaultValue = 25, min = 0, max = 100, titleResId = R.string.pref_title_sbat_warning, defaultedBySM = true, unitType = UnitType.PERCENT),
    OverviewSbatCritical(key = "statuslights_sbat_critical", defaultValue = 5, min = 0, max = 100, titleResId = R.string.pref_title_sbat_critical, defaultedBySM = true, unitType = UnitType.PERCENT),
    OverviewBageWarning(key = "statuslights_bage_warning", defaultValue = 216, min = 24, max = 1000, titleResId = R.string.pref_title_bage_warning, defaultedBySM = true, unitType = UnitType.HOURS),
    OverviewBageCritical(key = "statuslights_bage_critical", defaultValue = 240, min = 24, max = 1000, titleResId = R.string.pref_title_bage_critical, defaultedBySM = true, unitType = UnitType.HOURS),
    OverviewResWarning(key = "statuslights_res_warning", defaultValue = 80, min = 0, max = 300, titleResId = R.string.pref_title_res_warning, defaultedBySM = true, unitType = UnitType.INSULIN_INT),
    OverviewResCritical(key = "statuslights_res_critical", defaultValue = 10, min = 0, max = 300, titleResId = R.string.pref_title_res_critical, defaultedBySM = true, unitType = UnitType.INSULIN_INT),
    OverviewBattWarning(key = "statuslights_bat_warning", defaultValue = 51, min = 0, max = 100, titleResId = R.string.pref_title_batt_warning, defaultedBySM = true, unitType = UnitType.PERCENT),
    OverviewBattCritical(key = "statuslights_bat_critical", defaultValue = 26, min = 0, max = 100, titleResId = R.string.pref_title_batt_critical, defaultedBySM = true, unitType = UnitType.PERCENT),
    OverviewBolusPercentage(key = "boluswizard_percentage", defaultValue = 100, min = 10, max = 100, titleResId = R.string.pref_title_bolus_percentage, summaryResId = R.string.deliverpartofboluswizard, unitType = UnitType.PERCENT),
    OverviewResetBolusPercentageTime(
        key = "key_reset_boluswizard_percentage_time",
        defaultValue = 16,
        min = 6,
        max = 120,
        titleResId = R.string.pref_title_reset_bolus_percentage_time,
        summaryResId = R.string.deliver_part_of_boluswizard_reset_time,
        defaultedBySM = true,
        engineeringModeOnly = true,
        unitType = UnitType.MIN
    ),
    ProtectionTimeout(
        key = "protection_timeout",
        defaultValue = 1,
        min = 0,
        max = 180,
        titleResId = R.string.pref_title_protection_timeout,
        defaultedBySM = true,
        unitType = UnitType.SEC,
        visibility = PreferenceVisibility.stringNotEmpty { StringKey.ProtectionMasterPassword }
    ),

    // Protection types sorted by level: 0 (Application) → 1 (Bolus) → 2 (Settings)
    // Application is independent; Bolus requires Settings to be set
    ProtectionTypeApplication(
        key = "application_protection",
        defaultValue = ProtectionType.NONE.ordinal,
        min = ProtectionType.NONE.ordinal,
        max = ProtectionType.CUSTOM_PIN.ordinal,
        titleResId = R.string.pref_title_protection_type_application,
        summaryResId = R.string.pref_summary_protection_type_application,
        preferenceType = PreferenceType.LIST,
        entries = mapOf(
            ProtectionType.NONE.ordinal to R.string.noprotection,
            ProtectionType.BIOMETRIC.ordinal to R.string.biometric,
            ProtectionType.MASTER_PASSWORD.ordinal to R.string.master_password,
            ProtectionType.CUSTOM_PASSWORD.ordinal to R.string.custom_password,
            ProtectionType.CUSTOM_PIN.ordinal to R.string.custom_pin
        ),
        visibility = PreferenceVisibility.stringNotEmpty { StringKey.ProtectionMasterPassword }
    ),
    ProtectionTypeBolus(
        key = "bolus_protection",
        defaultValue = ProtectionType.NONE.ordinal,
        min = ProtectionType.NONE.ordinal,
        max = ProtectionType.CUSTOM_PIN.ordinal,
        titleResId = R.string.pref_title_protection_type_bolus,
        summaryResId = R.string.pref_summary_protection_type_bolus,
        preferenceType = PreferenceType.LIST,
        entries = mapOf(
            ProtectionType.NONE.ordinal to R.string.noprotection,
            ProtectionType.BIOMETRIC.ordinal to R.string.biometric,
            ProtectionType.MASTER_PASSWORD.ordinal to R.string.master_password,
            ProtectionType.CUSTOM_PASSWORD.ordinal to R.string.custom_password,
            ProtectionType.CUSTOM_PIN.ordinal to R.string.custom_pin
        ),
        visibility = PreferenceVisibility.stringNotEmpty { StringKey.ProtectionMasterPassword },
        enabledCondition = PreferenceEnabledCondition { ctx ->
            ctx.preferences.get(ProtectionTypeSettings) != ProtectionType.NONE.ordinal
        }
    ),
    ProtectionTypeSettings(
        key = "settings_protection",
        defaultValue = ProtectionType.NONE.ordinal,
        min = ProtectionType.NONE.ordinal,
        max = ProtectionType.CUSTOM_PIN.ordinal,
        titleResId = R.string.pref_title_protection_type_settings,
        summaryResId = R.string.pref_summary_protection_type_settings,
        preferenceType = PreferenceType.LIST,
        entries = mapOf(
            ProtectionType.NONE.ordinal to R.string.noprotection,
            ProtectionType.BIOMETRIC.ordinal to R.string.biometric,
            ProtectionType.MASTER_PASSWORD.ordinal to R.string.master_password,
            ProtectionType.CUSTOM_PASSWORD.ordinal to R.string.custom_password,
            ProtectionType.CUSTOM_PIN.ordinal to R.string.custom_pin
        ),
        visibility = PreferenceVisibility.stringNotEmpty { StringKey.ProtectionMasterPassword }
    ),
    SafetyMaxCarbs(key = "treatmentssafety_maxcarbs", defaultValue = 48, min = 1, max = 200, titleResId = R.string.pref_title_max_carbs, unitType = UnitType.GRAMS),
    LoopOpenModeMinChange(
        key = "loop_openmode_min_change",
        defaultValue = 30,
        min = 0,
        max = 50,
        titleResId = R.string.pref_title_open_mode_min_change,
        summaryResId = R.string.loop_open_mode_min_change_summary,
        defaultedBySM = true,
        unitType = UnitType.PERCENT
    ),
    ApsMaxSmbFrequency(key = "smbinterval", defaultValue = 3, min = 1, max = 10, titleResId = R.string.pref_title_smb_frequency, defaultedBySM = true, dependency = BooleanKey.ApsUseSmb, unitType = UnitType.MIN),
    ApsMaxMinutesOfBasalToLimitSmb(key = "smbmaxminutes", defaultValue = 30, min = 15, max = 120, titleResId = R.string.pref_title_smb_max_minutes, defaultedBySM = true, dependency = BooleanKey.ApsUseSmb, unitType = UnitType.MIN),
    ApsUamMaxMinutesOfBasalToLimitSmb(
        key = "uamsmbmaxminutes", defaultValue = 30, min = 15, max = 120, titleResId = R.string.pref_title_uam_smb_max_minutes, summaryResId = R.string.uam_smb_max_minutes, defaultedBySM = true, dependency = BooleanKey.ApsUseSmb,
        visibility = PreferenceVisibility { it.preferences.get(BooleanKey.ApsUseUam) },
        unitType = UnitType.MIN
    ),
    ApsCarbsRequestThreshold(
        key = "carbsReqThreshold",
        defaultValue = 1,
        min = 1,
        max = 100,
        titleResId = R.string.pref_title_carbs_request_threshold,
        summaryResId = R.string.carbs_req_threshold_summary,
        defaultedBySM = true,
        unitType = UnitType.GRAMS
    ),
    ApsAutoIsfHalfBasalExerciseTarget(
        key = "half_basal_exercise_target",
        defaultValue = 160,
        min = 120,
        max = 200,
        titleResId = R.string.pref_title_half_basal_exercise_target,
        summaryResId = R.string.half_basal_exercise_target_summary,
        defaultedBySM = true,
        unitType = UnitType.MGDL
    ),
    ApsAutoIsfIobThPercent(
        key = "iob_threshold_percent",
        defaultValue = 100,
        min = 10,
        max = 100,
        titleResId = R.string.pref_title_iob_threshold_percent,
        summaryResId = R.string.openapsama_iob_threshold_percent_summary,
        defaultedBySM = true,
        unitType = UnitType.PERCENT
    ),
    ApsDynIsfAdjustmentFactor(
        key = "DynISFAdjust",
        defaultValue = 100,
        min = 1,
        max = 300,
        titleResId = R.string.pref_title_dynisf_adjustment_factor,
        summaryResId = R.string.dyn_isf_adjust_summary,
        dependency = BooleanKey.ApsUseDynamicSensitivity,
        unitType = UnitType.PERCENT
    ),
    AutosensPeriod(
        key = "openapsama_autosens_period",
        defaultValue = 24,
        min = 4,
        max = 24,
        titleResId = R.string.pref_title_autosens_period,
        summaryResId = R.string.openapsama_autosens_period_summary,
        calculatedDefaultValue = true,
        unitType = UnitType.HOURS
    ),
    MaintenanceLogsAmount(key = "maintenance_logs_amount", defaultValue = 2, min = 1, max = 10, titleResId = R.string.pref_title_logs_amount, defaultedBySM = true),
    AlertsStaleDataThreshold(
        key = "missed_bg_readings_threshold",
        defaultValue = 30,
        min = 15,
        max = 10000,
        titleResId = R.string.pref_title_stale_data_threshold,
        defaultedBySM = true,
        dependency = BooleanKey.AlertMissedBgReading,
        unitType = UnitType.MIN
    ),
    AlertsPumpUnreachableThreshold(
        key = "pump_unreachable_threshold",
        defaultValue = 30,
        min = 30,
        max = 300,
        titleResId = R.string.pref_title_pump_unreachable_threshold,
        defaultedBySM = true,
        dependency = BooleanKey.AlertPumpUnreachable,
        unitType = UnitType.MIN
    ),

    AutotuneDefaultTuneDays(key = "autotune_default_tune_days", defaultValue = 5, min = 1, max = 30, titleResId = R.string.pref_title_autotune_days, summaryResId = R.string.autotune_default_tune_days_summary, unitType = UnitType.DAYS),

    SmsRemoteBolusDistance(
        key = "smscommunicator_remotebolusmindistance",
        defaultValue = 15,
        min = 3,
        max = 60,
        titleResId = R.string.pref_title_sms_remote_bolus_distance,
        unitType = UnitType.MIN,
        // Enabled only when multiple phone numbers are configured (2FA requirement)
        enabledCondition = PreferenceEnabledCondition { ctx ->
            val allowedNumbers = ctx.preferences.get(StringKey.SmsAllowedNumbers)
            allowedNumbers.split(";").filter { it.trim().isNotEmpty() }.size >= 2
        }
    ),

    BgSourceRandomInterval(key = "randombg_interval_min", defaultValue = 5, min = 1, max = 15, titleResId = R.string.pref_title_random_bg_interval, defaultedBySM = true, unitType = UnitType.MIN),
    NsClientAlarmStaleData(key = "ns_alarm_stale_data_value", defaultValue = 16, min = 15, max = 120, titleResId = R.string.pref_title_alarm_stale_data, unitType = UnitType.MIN),
    NsClientUrgentAlarmStaleData(key = "ns_alarm_urgent_stale_data_value", defaultValue = 31, min = 30, max = 180, titleResId = R.string.pref_title_urgent_alarm_stale_data, unitType = UnitType.MIN),

    SiteRotationUserProfile(key = "site_rotation_user_profile", defaultValue = 0, min = 0, max = 2, titleResId = R.string.pref_title_site_rotation_profile),
}
package app.aaps.core.keys

enum class BooleanKey(
    override val key: Int,
    val defaultValue: Boolean,
    override val defaultedBySM: Boolean = false,
    val showInApsMode: Boolean = true,
    val showInNsClientMode: Boolean = true,
    val showInPumpControlMode: Boolean = true,
    val hideParentScreenIfHidden: Boolean = false     // PreferenceScreen is final so we cannot extend and modify behavior
) : PreferenceKey {

    GeneralSimpleMode(R.string.key_simple_mode, true),
    GeneralSetupWizardProcessed(R.string.key_setupwizard_processed, false),
    OverviewKeepScreenOn(R.string.key_keep_screen_on, false, defaultedBySM = true),
    OverviewShowTreatmentButton(R.string.key_show_treatment_button, false, defaultedBySM = true, hideParentScreenIfHidden = true),
    OverviewShowWizardButton(R.string.key_show_wizard_button, true, defaultedBySM = true),
    OverviewShowInsulinButton(R.string.key_show_insulin_button, true, defaultedBySM = true),
    OverviewShowCarbsButton(R.string.key_show_carbs_button, true, defaultedBySM = true),
    OverviewShowCgmButton(R.string.key_show_cgm_button, false, defaultedBySM = true, showInNsClientMode = false),
    OverviewShowCalibrationButton(R.string.key_show_calibration_button, false, defaultedBySM = true, showInNsClientMode = false),
    OverviewShortTabTitles(R.string.key_short_tab_titles, false, defaultedBySM = true),
    OverviewShowNotesInDialogs(R.string.key_show_notes_entry_dialogs, false, defaultedBySM = true),
    OverviewShowStatusLights(R.string.key_show_statuslights, true, defaultedBySM = true, hideParentScreenIfHidden = true),
    OverviewUseBolusAdvisor(R.string.key_use_bolus_advisor, true, defaultedBySM = true),
    OverviewUseBolusReminder(R.string.key_use_bolus_reminder, true, defaultedBySM = true),
    OverviewUseSuperBolus(R.string.key_use_superbolus, false, defaultedBySM = true, hideParentScreenIfHidden = true),
    BgSourceUploadToNs(R.string.key_do_bg_ns_upload, true, defaultedBySM = true, hideParentScreenIfHidden = true),
    DexcomCreateSensorChange(R.string.key_dexcom_log_ns_sensor_change, true, defaultedBySM = true),
    ApsUseAutosens(R.string.key_openaps_use_autosens, true, defaultedBySM = true), // change from default false
    ApsUseSmb(R.string.key_openaps_use_smb, true, defaultedBySM = true), // change from default false
    ApsUseSmbWithHighTt(R.string.key_openaps_allow_smb_with_high_temp_target, false, defaultedBySM = true),
    ApsUseSmbAlways(R.string.key_openaps_enable_smb_always, true, defaultedBySM = true), // change from default false
    ApsUseSmbWithCob(R.string.key_openaps_allow_smb_with_COB, true, defaultedBySM = true), // change from default false
    ApsUseSmbWithLowTt(R.string.key_openaps_allow_smb_with_low_temp_target, true, defaultedBySM = true), // change from default false
    ApsUseSmbAfterCarbs(R.string.key_openaps_enable_smb_after_carbs, true, defaultedBySM = true), // change from default false
    ApsUseUam(R.string.key_openaps_use_uam, true, defaultedBySM = true), // change from default false
    ApsSensitivityRaisesTarget(R.string.key_openaps_sensitivity_raises_target, true, defaultedBySM = true),
    ApsResistanceLowersTarget(R.string.key_openaps_resistance_lowers_target, true, defaultedBySM = true), // change from default false
    ApsAlwaysUseShortDeltas(R.string.key_openaps_always_use_short_deltas, false, defaultedBySM = true, hideParentScreenIfHidden = true),
    ApsDynIsfAdjustSensitivity(R.string.key_dynamic_isf_adjust_sensitivity, false, defaultedBySM = true), // change from default false
    ApsAmaAutosensAdjustTargets(R.string.key_openaps_ama_autosens_adjust_targets, true, defaultedBySM = true),
    MaintenanceEnableFabric(R.string.key_enable_fabric, true, defaultedBySM = true, hideParentScreenIfHidden = true),
}
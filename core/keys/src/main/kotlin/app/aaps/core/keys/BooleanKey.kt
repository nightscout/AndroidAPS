package app.aaps.core.keys

enum class BooleanKey(
    override val key: Int,
    override val defaultValue: Boolean,
    override val calculatedDefaultValue: Boolean = false,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val engineeringModeOnly: Boolean = false
) : BooleanPreferenceKey {

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
    BgSourceCreateSensorChange(R.string.key_dexcom_log_ns_sensor_change, true, defaultedBySM = true),
    ApsUseDynamicSensitivity(R.string.key_use_dynamic_sensitivity, false),
    ApsUseAutosens(R.string.key_openaps_use_autosens, true, defaultedBySM = true, negativeDependency = ApsUseDynamicSensitivity), // change from default false
    ApsUseSmb(R.string.key_openaps_use_smb, true, defaultedBySM = true), // change from default false
    ApsUseSmbWithHighTt(R.string.key_openaps_allow_smb_with_high_temp_target, false, defaultedBySM = true, dependency = ApsUseSmb),
    ApsUseSmbAlways(R.string.key_openaps_enable_smb_always, true, defaultedBySM = true, dependency = ApsUseSmb), // change from default false
    ApsUseSmbWithCob(R.string.key_openaps_allow_smb_with_COB, true, defaultedBySM = true, dependency = ApsUseSmb), // change from default false
    ApsUseSmbWithLowTt(R.string.key_openaps_allow_smb_with_low_temp_target, true, defaultedBySM = true, dependency = ApsUseSmb), // change from default false
    ApsUseSmbAfterCarbs(R.string.key_openaps_enable_smb_after_carbs, true, defaultedBySM = true, dependency = ApsUseSmb), // change from default false
    ApsUseUam(R.string.key_openaps_use_uam, true, defaultedBySM = true), // change from default false
    ApsSensitivityRaisesTarget(R.string.key_openaps_sensitivity_raises_target, true, defaultedBySM = true),
    ApsResistanceLowersTarget(R.string.key_openaps_resistance_lowers_target, true, defaultedBySM = true), // change from default false
    ApsAlwaysUseShortDeltas(R.string.key_openaps_always_use_short_deltas, false, defaultedBySM = true, hideParentScreenIfHidden = true),
    ApsDynIsfAdjustSensitivity(R.string.key_dynamic_isf_adjust_sensitivity, false, defaultedBySM = true, dependency = ApsUseDynamicSensitivity), // change from default false
    ApsAmaAutosensAdjustTargets(R.string.key_openaps_ama_autosens_adjust_targets, true, defaultedBySM = true),
    MaintenanceEnableFabric(R.string.key_enable_fabric, true, defaultedBySM = true, hideParentScreenIfHidden = true),

    AutotuneAutoSwitchProfile(R.string.key_autotune_auto, false),
    AutotuneCategorizeUamAsBasal(R.string.key_autotune_categorize_uam_as_basal, false),
    AutotuneTuneInsulinCurve(R.string.key_autotune_tune_insulin_curve, false),
    AutotuneCircadianIcIsf(R.string.key_autotune_circadian_ic_isf, false),
    AutotuneAdditionalLog(R.string.key_autotune_additional_log, false),

    SmsAllowRemoteCommands(R.string.key_smscommunicator_remote_commands_allowed, false),
    SmsReportPumpUnreachable(R.string.key_smscommunicator_report_pump_unreachable, true),

    VirtualPumpStatusUpload(R.string.key_virtual_pump_upload_status, false, showInNsClientMode = false),
    GarminLocalHttpServer(R.string.key_garmin_communication_http, false, defaultedBySM = true, hideParentScreenIfHidden = true),
    NsClientUploadData(R.string.key_ns_upload, true, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptCgmData(R.string.key_ns_receive_cgm, false, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptProfileStore(R.string.key_ns_receive_profile_store, true, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptTempTarget(R.string.key_ns_receive_temp_target, false, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptProfileSwitch(R.string.key_ns_receive_profile_switch, false, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptInsulin(R.string.key_ns_receive_insulin, false, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptCarbs(R.string.key_ns_receive_carbs, false, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptTherapyEvent(R.string.key_ns_receive_therapy_events, false, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptOfflineEvent(R.string.key_ns_receive_offline_event, false, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptTbrEb(R.string.key_ns_receive_tbr_eb, false, showInNsClientMode = false, hideParentScreenIfHidden = true, engineeringModeOnly = true),
    NsClientNotificationsFromAlarms(R.string.key_ns_alarms, false, calculatedDefaultValue = true),
    NsClientNotificationsFromAnnouncements(R.string.key_ns_announcements, false, calculatedDefaultValue = true),
    NsClientUseCellular(R.string.key_ns_cellular, true),
    NsClientUseRoaming(R.string.key_ns_allow_roaming, true, dependency = NsClientUseCellular),
    NsClientUseWifi(R.string.key_ns_wifi, true),
    NsClientUseOnBattery(R.string.key_ns_battery, true),
    NsClientUseOnCharging(R.string.key_ns_charging, true),
    NsClientLogAppStart(R.string.key_ns_log_app_started_event, false, calculatedDefaultValue = true),
    NsClientCreateAnnouncementsFromErrors(R.string.key_ns_create_announcements_from_errors, false, calculatedDefaultValue = true, showInNsClientMode = false),
    NsClientCreateAnnouncementsFromCarbsReq(R.string.key_ns_create_announcements_from_carbs_req, false, calculatedDefaultValue = true, showInNsClientMode = false),
    NsClientSlowSync(R.string.key_ns_sync_slow, false),
    NsClient3UseWs(R.string.key_ns_use_ws, true),
    TidepoolUseTestServers(R.string.key_tidepool_dev_servers, false),
    OpenHumansWifiOnly(R.string.key_oh_wifi_only, true),
    OpenHumansChargingOnly(R.string.key_oh_charging_only, false),
    XdripSendStatus(R.string.key_xdrip_send_status, false),
    XdripSendDetailedIob(R.string.key_xdrip_status_detailed_iob, true, defaultedBySM = true, hideParentScreenIfHidden = true),
    XdripSendBgi(R.string.key_xdrip_status_show_bgi, true, defaultedBySM = true, hideParentScreenIfHidden = true),
    WearControl(key = R.string.key_wear_control, defaultValue = false, dependency = WearControl),
    WearWizardBg(key = R.string.key_wearwizard_bg, defaultValue = true, dependency = WearControl, hideParentScreenIfHidden = true),
    WearWizardTt(key = R.string.key_wearwizard_tt, defaultValue = false, dependency = WearControl, hideParentScreenIfHidden = true),
    WearWizardTrend(key = R.string.key_wearwizard_trend, defaultValue = false, dependency = WearControl, hideParentScreenIfHidden = true),
    WearWizardCob(key = R.string.key_wearwizard_cob, defaultValue = true, dependency = WearControl, hideParentScreenIfHidden = true),
    WearWizardIob(key = R.string.key_wearwizard_iob, defaultValue = true, dependency = WearControl, hideParentScreenIfHidden = true),
    WearCustomWatchfaceAuthorization(key = R.string.key_wear_custom_watchface_autorization, defaultValue = false),
    WearNotifyOnSmb(key = R.string.key_wear_notify_on_smb, defaultValue = true),
}
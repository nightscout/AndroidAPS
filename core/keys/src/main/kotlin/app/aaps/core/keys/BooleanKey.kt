package app.aaps.core.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.PreferenceVisibility

enum class BooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val titleResId: Int,
    override val summaryResId: Int? = null,
    override val preferenceType: PreferenceType = PreferenceType.SWITCH,
    override val calculatedDefaultValue: Boolean = false,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val engineeringModeOnly: Boolean = false,
    override val exportable: Boolean = true,
    override val visibility: PreferenceVisibility = PreferenceVisibility.ALWAYS
) : BooleanPreferenceKey {

    GeneralSimpleMode("simple_mode", true, R.string.pref_title_simple_mode),
    GeneralInsulinConcentration("insulin_concentration_enabled", false, R.string.pref_title_insulin_concentration, R.string.pref_summary_insulin_concentration, defaultedBySM = true),
    OverviewKeepScreenOn("keep_screen_on", false, R.string.pref_title_keep_screen_on, R.string.pref_summary_keep_screen_on, calculatedDefaultValue = true),
    OverviewShowTreatmentButton("show_treatment_button", false, R.string.pref_title_show_treatment_button, defaultedBySM = true, hideParentScreenIfHidden = true),
    OverviewShowWizardButton("show_wizard_button", true, R.string.pref_title_show_wizard_button, defaultedBySM = true),
    OverviewShowInsulinButton("show_insulin_button", true, R.string.pref_title_show_insulin_button, defaultedBySM = true),
    OverviewShowCarbsButton("show_carbs_button", true, R.string.pref_title_show_carbs_button, defaultedBySM = true),
    OverviewShowCgmButton("show_cgm_button", false, R.string.pref_title_show_cgm_button, R.string.pref_summary_show_cgm_button, defaultedBySM = true, showInNsClientMode = false),
    OverviewShowCalibrationButton("show_calibration_button", false, R.string.pref_title_show_calibration_button, R.string.pref_summary_show_calibration_button, defaultedBySM = true, showInNsClientMode = false),
    OverviewShowNotesInDialogs("show_notes_entry_dialogs", false, R.string.pref_title_show_notes_in_dialogs, defaultedBySM = true),
    OverviewUseBolusAdvisor("use_bolus_advisor", true, R.string.pref_title_use_bolus_advisor, R.string.pref_summary_use_bolus_advisor, defaultedBySM = true),
    OverviewUseBolusReminder("use_bolus_reminder", true, R.string.pref_title_use_bolus_reminder, R.string.pref_summary_use_bolus_reminder, defaultedBySM = true),

    @Deprecated("Remove support")
    OverviewUseSuperBolus("key_usersuperbolus", false, R.string.pref_title_use_super_bolus, R.string.pref_summary_use_super_bolus, defaultedBySM = true, hideParentScreenIfHidden = true),

    PumpBtWatchdog("bt_watchdog", false, R.string.pref_title_bt_watchdog, R.string.pref_summary_bt_watchdog, showInNsClientMode = false, hideParentScreenIfHidden = true),

    AlertMissedBgReading("enable_missed_bg_readings", false, R.string.pref_title_alert_missed_bg_reading),
    AlertPumpUnreachable("enable_pump_unreachable_alert", true, R.string.pref_title_alert_pump_unreachable),
    AlertCarbsRequired("enable_carbs_required_alert_local", true, R.string.pref_title_alert_carbs_required),
    AlertUrgentAsAndroidNotification("raise_urgent_alarms_as_android_notification", true, R.string.pref_title_alert_urgent_as_android_notification),
    AlertIncreaseVolume("gradually_increase_notification_volume", true, R.string.pref_title_alert_increase_volume),

    BgSourceUploadToNs("dexcomg5_nsupload", true, R.string.pref_title_bg_source_upload_to_ns, defaultedBySM = true, hideParentScreenIfHidden = true),
    BgSourceCreateSensorChange("dexcom_lognssensorchange", true, R.string.pref_title_bg_source_create_sensor_change, R.string.pref_summary_bg_source_create_sensor_change, defaultedBySM = true),

    ApsUseDynamicSensitivity("use_dynamic_sensitivity", false, R.string.pref_title_aps_use_dynamic_sensitivity, R.string.pref_summary_aps_use_dynamic_sensitivity),
    ApsUseAutosens("openapsama_useautosens", true, R.string.pref_title_aps_use_autosens, defaultedBySM = true, negativeDependency = ApsUseDynamicSensitivity),
    ApsUseSmb("use_smb", true, R.string.pref_title_aps_use_smb, R.string.pref_summary_aps_use_smb, defaultedBySM = true),
    ApsUseSmbWithHighTt("enableSMB_with_high_temptarget", false, R.string.pref_title_aps_use_smb_with_high_tt, R.string.pref_summary_aps_use_smb_with_high_tt, defaultedBySM = true, dependency = ApsUseSmb),
    ApsUseSmbAlways(
        "enableSMB_always", true, R.string.pref_title_aps_use_smb_always, R.string.pref_summary_aps_use_smb_always, defaultedBySM = true, dependency = ApsUseSmb,
        visibility = PreferenceVisibility.ADVANCED_FILTERING
    ),
    ApsUseSmbWithCob(
        "enableSMB_with_COB", true, R.string.pref_title_aps_use_smb_with_cob, R.string.pref_summary_aps_use_smb_with_cob, defaultedBySM = true, dependency = ApsUseSmb,
        visibility = PreferenceVisibility { !it.preferences.get(ApsUseSmbAlways) || !it.advancedFilteringSupported }
    ),
    ApsUseSmbWithLowTt(
        "enableSMB_with_temptarget", true, R.string.pref_title_aps_use_smb_with_low_tt, R.string.pref_summary_aps_use_smb_with_low_tt, defaultedBySM = true, dependency = ApsUseSmb,
        visibility = PreferenceVisibility { !it.preferences.get(ApsUseSmbAlways) || !it.advancedFilteringSupported }
    ),
    ApsUseSmbAfterCarbs(
        "enableSMB_after_carbs", true, R.string.pref_title_aps_use_smb_after_carbs, R.string.pref_summary_aps_use_smb_after_carbs, defaultedBySM = true, dependency = ApsUseSmb,
        visibility = PreferenceVisibility { !it.preferences.get(ApsUseSmbAlways) && it.advancedFilteringSupported }
    ),
    ApsUseUam("use_uam", true, R.string.pref_title_aps_use_uam, R.string.pref_summary_aps_use_uam, defaultedBySM = true),
    ApsSensitivityRaisesTarget(
        "sensitivity_raises_target", true, R.string.pref_title_aps_sensitivity_raises_target, R.string.pref_summary_aps_sensitivity_raises_target, defaultedBySM = true,
        visibility = PreferenceVisibility {
            if (it.preferences.get(ApsUseDynamicSensitivity)) {
                it.preferences.get(ApsDynIsfAdjustSensitivity)
            } else {
                it.preferences.get(ApsUseAutosens)
            }
        }
    ),
    ApsResistanceLowersTarget(
        "resistance_lowers_target", true, R.string.pref_title_aps_resistance_lowers_target, R.string.pref_summary_aps_resistance_lowers_target, defaultedBySM = true,
        visibility = PreferenceVisibility {
            if (it.preferences.get(ApsUseDynamicSensitivity)) {
                it.preferences.get(ApsDynIsfAdjustSensitivity)
            } else {
                it.preferences.get(ApsUseAutosens)
            }
        }
    ),
    ApsAlwaysUseShortDeltas("always_use_shortavg", false, R.string.pref_title_aps_always_use_short_deltas, R.string.pref_summary_aps_always_use_short_deltas, defaultedBySM = true, hideParentScreenIfHidden = true),
    ApsDynIsfAdjustSensitivity("dynisf_adjust_sensitivity", false, R.string.pref_title_aps_dynisf_adjust_sensitivity, R.string.pref_summary_aps_dynisf_adjust_sensitivity, defaultedBySM = true, dependency = ApsUseDynamicSensitivity),
    ApsAmaAutosensAdjustTargets("autosens_adjust_targets", true, R.string.pref_title_aps_autosens_adjust_targets, R.string.pref_summary_aps_autosens_adjust_targets, defaultedBySM = true),
    ApsAutoIsfHighTtRaisesSens("high_temptarget_raises_sensitivity", false, R.string.pref_title_aps_high_tt_raises_sensitivity, R.string.pref_summary_aps_high_tt_raises_sensitivity, defaultedBySM = true),
    ApsAutoIsfLowTtLowersSens("low_temptarget_lowers_sensitivity", false, R.string.pref_title_aps_low_tt_lowers_sensitivity, R.string.pref_summary_aps_low_tt_lowers_sensitivity, defaultedBySM = true),
    ApsUseAutoIsfWeights("openapsama_enable_autoISF", false, R.string.pref_title_aps_use_autoisf_weights, R.string.pref_summary_aps_use_autoisf_weights, defaultedBySM = true),
    ApsAutoIsfSmbOnEvenTarget("Enable alternative activation of SMB always", false, R.string.pref_title_aps_smb_on_even_target, R.string.pref_summary_aps_smb_on_even_target, defaultedBySM = true),

    MaintenanceEnableFabric("enable_fabric2", true, R.string.pref_title_maintenance_enable_fabric, defaultedBySM = true, hideParentScreenIfHidden = true),
    MaintenanceEnableExportSettingsAutomation("enable_unattended_export", false, R.string.pref_title_maintenance_enable_export_automation, defaultedBySM = false),

    AutotuneAutoSwitchProfile("autotune_auto", false, R.string.pref_title_autotune_auto_switch_profile, R.string.pref_summary_autotune_auto_switch_profile),
    AutotuneCategorizeUamAsBasal("categorize_uam_as_basal", false, R.string.pref_title_autotune_categorize_uam_as_basal, R.string.pref_summary_autotune_categorize_uam_as_basal),
    AutotuneTuneInsulinCurve("autotune_tune_insulin_curve", false, R.string.pref_title_autotune_tune_insulin_curve),
    AutotuneCircadianIcIsf("autotune_circadian_ic_isf", false, R.string.pref_title_autotune_circadian_ic_isf, R.string.pref_summary_autotune_circadian_ic_isf),
    AutotuneAdditionalLog("autotune_additional_log", false, R.string.pref_title_autotune_additional_log),

    SmsAllowRemoteCommands("smscommunicator_remotecommandsallowed", false, R.string.pref_title_sms_allow_remote_commands),
    SmsReportPumpUnreachable("smscommunicator_report_pump_unreachable", true, R.string.pref_title_sms_report_pump_unreachable, R.string.pref_summary_sms_report_pump_unreachable),

    VirtualPumpStatusUpload("virtualpump_uploadstatus", false, R.string.pref_title_virtual_pump_status_upload, showInNsClientMode = false),
    NsClientUploadData("ns_upload", true, R.string.pref_title_ns_upload_data, R.string.pref_summary_ns_upload_data, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptCgmData("ns_receive_cgm", false, R.string.pref_title_ns_receive_cgm, R.string.pref_summary_ns_receive_cgm, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptProfileStore("ns_receive_profile_store", true, R.string.pref_title_ns_receive_profile_store, R.string.pref_summary_ns_receive_profile_store, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptTempTarget("ns_receive_temp_target", false, R.string.pref_title_ns_receive_temp_target, R.string.pref_summary_ns_receive_temp_target, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptProfileSwitch("ns_receive_profile_switch", false, R.string.pref_title_ns_receive_profile_switch, R.string.pref_summary_ns_receive_profile_switch, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptInsulin("ns_receive_insulin", false, R.string.pref_title_ns_receive_insulin, R.string.pref_summary_ns_receive_insulin, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptCarbs("ns_receive_carbs", false, R.string.pref_title_ns_receive_carbs, R.string.pref_summary_ns_receive_carbs, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptTherapyEvent("ns_receive_therapy_events", false, R.string.pref_title_ns_receive_therapy_event, R.string.pref_summary_ns_receive_therapy_event, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptRunningMode("ns_receive_running_mode", false, R.string.pref_title_ns_receive_running_mode, R.string.pref_summary_ns_receive_running_mode, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptTbrEb("ns_receive_tbr_eb", false, R.string.pref_title_ns_receive_tbr_eb, R.string.pref_summary_ns_receive_tbr_eb, showInNsClientMode = false, engineeringModeOnly = true),
    NsClientNotificationsFromAlarms("ns_alarms", false, R.string.pref_title_ns_notifications_from_alarms, calculatedDefaultValue = true),
    NsClientNotificationsFromAnnouncements("ns_announcements", false, R.string.pref_title_ns_notifications_from_announcements, calculatedDefaultValue = true),
    NsClientUseCellular("ns_cellular", true, R.string.pref_title_ns_use_cellular),
    NsClientUseRoaming("ns_allow_roaming", true, R.string.pref_title_ns_use_roaming, dependency = NsClientUseCellular),
    NsClientUseWifi("ns_wifi", true, R.string.pref_title_ns_use_wifi),
    NsClientUseOnBattery("ns_battery", true, R.string.pref_title_ns_use_on_battery),
    NsClientUseOnCharging("ns_charging", true, R.string.pref_title_ns_use_on_charging),
    NsClientLogAppStart("ns_log_app_started_event", false, R.string.pref_title_ns_log_app_start, calculatedDefaultValue = true),
    NsClientCreateAnnouncementsFromErrors("ns_create_announcements_from_errors", false, R.string.pref_title_ns_create_announcements_from_errors, calculatedDefaultValue = true, showInNsClientMode = false),
    NsClientCreateAnnouncementsFromCarbsReq("ns_create_announcements_from_carbs_req", false, R.string.pref_title_ns_create_announcements_from_carbs_req, calculatedDefaultValue = true, showInNsClientMode = false),
    NsClientSlowSync("ns_sync_slow", false, R.string.pref_title_ns_slow_sync),
    NsClient3UseWs("ns_use_ws", true, R.string.pref_title_ns_use_ws, R.string.pref_summary_ns_use_ws),
    OpenHumansWifiOnly("oh_wifi_only", true, R.string.pref_title_openhumans_wifi_only),
    OpenHumansChargingOnly("oh_charging_only", false, R.string.pref_title_openhumans_charging_only),
    XdripSendStatus("xdrip_send_status", false, R.string.pref_title_xdrip_send_status),
    XdripSendDetailedIob("xdripstatus_detailediob", true, R.string.pref_title_xdrip_send_detailed_iob, R.string.pref_summary_xdrip_send_detailed_iob, defaultedBySM = true, hideParentScreenIfHidden = true),
    XdripSendBgi("xdripstatus_showbgi", true, R.string.pref_title_xdrip_send_bgi, R.string.pref_summary_xdrip_send_bgi, defaultedBySM = true, hideParentScreenIfHidden = true),
    WearControl(key = "wearcontrol", defaultValue = false, titleResId = R.string.pref_title_wear_control, summaryResId = R.string.pref_summary_wear_control),
    WearWizardBg(key = "wearwizard_bg", defaultValue = true, titleResId = R.string.pref_title_wear_wizard_bg, dependency = WearControl, hideParentScreenIfHidden = true),
    WearWizardTt(key = "wearwizard_tt", defaultValue = false, titleResId = R.string.pref_title_wear_wizard_tt, dependency = WearControl, hideParentScreenIfHidden = true),
    WearWizardTrend(key = "wearwizard_trend", defaultValue = false, titleResId = R.string.pref_title_wear_wizard_trend, dependency = WearControl, hideParentScreenIfHidden = true),
    WearWizardCob(key = "wearwizard_cob", defaultValue = true, titleResId = R.string.pref_title_wear_wizard_cob, dependency = WearControl, hideParentScreenIfHidden = true),
    WearWizardIob(key = "wearwizard_iob", defaultValue = true, titleResId = R.string.pref_title_wear_wizard_iob, dependency = WearControl, hideParentScreenIfHidden = true),
    WearCustomWatchfaceAuthorization(key = "wear_custom_watchface_autorization", defaultValue = false, titleResId = R.string.pref_title_wear_custom_watchface_authorization),
    WearNotifyOnSmb(key = "wear_notifySMB", defaultValue = true, titleResId = R.string.pref_title_wear_notify_on_smb, summaryResId = R.string.pref_summary_wear_notify_on_smb),
    WearBroadcastData(key = "wear_broadcast_data", defaultValue = false, titleResId = R.string.pref_title_wear_broadcast_data, summaryResId = R.string.pref_summary_wear_broadcast_data, showInApsMode = false, showInPumpControlMode = false),

    @Deprecated("remove after migration")
    WizardCalculationVisible("wizard_calculation_visible", defaultValue = false, titleResId = R.string.pref_title_wizard_calculation_visible),
    WizardCorrectionPercent("wizard_correction_percent", defaultValue = false, titleResId = R.string.pref_title_wizard_correction_percent),
    SiteRotationManagePump("site_rotation_manage_pump", defaultValue = false, titleResId = R.string.pref_title_site_rotation_manage_pump),
    SiteRotationManageCgm("site_rotation_manage_cgm", defaultValue = false, titleResId = R.string.pref_title_site_rotation_manage_cgm),

}

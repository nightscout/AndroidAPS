package app.aaps.core.keys

import app.aaps.core.keys.interfaces.AppPlatform
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.ElementVisibility
import app.aaps.core.keys.interfaces.PreferenceEnabledCondition
import app.aaps.core.keys.interfaces.SyncChannel
import app.aaps.core.keys.interfaces.SyncDirection
import app.aaps.core.keys.interfaces.SyncSpec
import app.aaps.core.keys.interfaces.TextRef

enum class BooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val title: TextRef,
    override val summary: TextRef? = null,
    override val preferenceType: PreferenceType = PreferenceType.SWITCH,
    override val calculatedDefaultValue: Boolean = false,
    override val defaultedBySM: Boolean = false,
    override val platforms: Set<AppPlatform> = AppPlatform.ALL,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val engineeringModeOnly: Boolean = false,
    override val visibility: ElementVisibility = ElementVisibility.ALWAYS,
    override val enabledCondition: PreferenceEnabledCondition = PreferenceEnabledCondition.ALWAYS,
    override val sync: SyncSpec? = null
) : BooleanPreferenceKey {

    GeneralSimpleMode(key = "simple_mode", defaultValue = true, title = KeysStrings.pref_title_simple_mode, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    GeneralInsulinConcentration(
        key = "insulin_concentration_enabled", defaultValue = false, title = KeysStrings.pref_title_insulin_concentration, summary = KeysStrings.pref_summary_insulin_concentration,
        defaultedBySM = true,
        enabledCondition = PreferenceEnabledCondition { it.isConcentrationEnabled },
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    OverviewKeepScreenOn(key = "keep_screen_on", defaultValue = false, title = KeysStrings.pref_title_keep_screen_on, summary = KeysStrings.pref_summary_keep_screen_on, calculatedDefaultValue = true),
    OverviewShowTreatmentButton(key = "show_treatment_button", defaultValue = false, title = KeysStrings.pref_title_show_treatment_button, defaultedBySM = true),
    OverviewShowWizardButton(key = "show_wizard_button", defaultValue = true, title = KeysStrings.pref_title_show_wizard_button, defaultedBySM = true),
    OverviewShowInsulinButton(key = "show_insulin_button", defaultValue = true, title = KeysStrings.pref_title_show_insulin_button, defaultedBySM = true),
    OverviewShowCarbsButton(key = "show_carbs_button", defaultValue = true, title = KeysStrings.pref_title_show_carbs_button, defaultedBySM = true),
    OverviewShowCgmButton(key = "show_cgm_button", defaultValue = false, title = KeysStrings.pref_title_show_cgm_button, summary = KeysStrings.pref_summary_show_cgm_button, defaultedBySM = true, showInNsClientMode = false),
    OverviewShowCalibrationButton(
        key = "show_calibration_button",
        defaultValue = false,
        title = KeysStrings.pref_title_show_calibration_button,
        summary = KeysStrings.pref_summary_show_calibration_button,
        defaultedBySM = true,
        showInNsClientMode = false
    ),
    OverviewShowNotesInDialogs(key = "show_notes_entry_dialogs", defaultValue = false, title = KeysStrings.pref_title_show_notes_in_dialogs, defaultedBySM = true),
    OverviewUseBolusAdvisor("use_bolus_advisor", true, KeysStrings.pref_title_use_bolus_advisor, KeysStrings.pref_summary_use_bolus_advisor, defaultedBySM = true, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    OverviewUseBolusReminder("use_bolus_reminder", true, KeysStrings.pref_title_use_bolus_reminder, KeysStrings.pref_summary_use_bolus_reminder, defaultedBySM = true, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),

    @Deprecated("Remove support")
    OverviewUseSuperBolus("key_usersuperbolus", false, KeysStrings.pref_title_use_super_bolus, KeysStrings.pref_summary_use_super_bolus, defaultedBySM = true, hideParentScreenIfHidden = true),

    PumpBtWatchdog(
        "bt_watchdog", false, KeysStrings.pref_title_bt_watchdog, KeysStrings.pref_summary_bt_watchdog,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),

    AlertMissedBgReading("enable_missed_bg_readings", false, KeysStrings.pref_title_alert_missed_bg_reading),
    AlertPumpUnreachable("enable_pump_unreachable_alert", true, KeysStrings.pref_title_alert_pump_unreachable),
    AlertCarbsRequired("enable_carbs_required_alert_local", true, KeysStrings.pref_title_alert_carbs_required),
    // Android only: it decides whether AAPS raises an OS notification at all. iOS gives an app no say
    // in that - the user grants or denies notifications in Settings - so there is nothing here for
    // the switch to do, and a switch that does nothing reads as a promise.
    AlertUrgentAsAndroidNotification(
        "raise_urgent_alarms_as_android_notification", true, KeysStrings.pref_title_alert_urgent_as_android_notification,
        platforms = AppPlatform.ANDROID_ONLY
    ),
    AlertIncreaseVolume("gradually_increase_notification_volume", true, KeysStrings.pref_title_alert_increase_volume),
    AlertOverrideDoNotDisturb("alert_override_dnd", true, KeysStrings.pref_title_alert_override_dnd, KeysStrings.pref_summary_alert_override_dnd, defaultedBySM = true),

    BgSourceUploadToNs("dexcomg5_nsupload", true, KeysStrings.pref_title_bg_source_upload_to_ns, defaultedBySM = true, hideParentScreenIfHidden = true),
    BgSourceCreateSensorChange("dexcom_lognssensorchange", true, KeysStrings.pref_title_bg_source_create_sensor_change, KeysStrings.pref_summary_bg_source_create_sensor_change, defaultedBySM = true),
    BgSourceRandomBgRandomize("randombg_randomize", true, KeysStrings.pref_title_random_bg_randomize, KeysStrings.pref_summary_random_bg_randomize, defaultedBySM = true),

    ApsUseDynamicSensitivity("use_dynamic_sensitivity", false, KeysStrings.pref_title_aps_use_dynamic_sensitivity, KeysStrings.pref_summary_aps_use_dynamic_sensitivity, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    ApsUseAutosens(
        "openapsama_useautosens", true, KeysStrings.pref_title_aps_use_autosens, defaultedBySM = true,
        // Hidden only while the active APS both offers dynamic sensitivity and has it enabled.
        // A plain negativeDependency on ApsUseDynamicSensitivity would also hide it on algorithms
        // whose screens never show that toggle (AMA, AutoISF), with no way to reveal it (issue #4482).
        visibility = ElementVisibility { !(it.apsOffersDynamicSensitivity && it.preferences.get(ApsUseDynamicSensitivity)) },
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsUseSmb("use_smb", true, KeysStrings.pref_title_aps_use_smb, KeysStrings.pref_summary_aps_use_smb, defaultedBySM = true, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    ApsUseSmbWithHighTt(
        "enableSMB_with_high_temptarget",
        false,
        KeysStrings.pref_title_aps_use_smb_with_high_tt,
        KeysStrings.pref_summary_aps_use_smb_with_high_tt,
        defaultedBySM = true,
        dependency = ApsUseSmb,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsUseSmbAlways(
        "enableSMB_always", true, KeysStrings.pref_title_aps_use_smb_always, KeysStrings.pref_summary_aps_use_smb_always, defaultedBySM = true, dependency = ApsUseSmb,
        visibility = ElementVisibility.ADVANCED_FILTERING,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsUseSmbWithCob(
        "enableSMB_with_COB", true, KeysStrings.pref_title_aps_use_smb_with_cob, KeysStrings.pref_summary_aps_use_smb_with_cob, defaultedBySM = true, dependency = ApsUseSmb,
        visibility = ElementVisibility { !it.preferences.get(ApsUseSmbAlways) || !it.advancedFilteringSupported },
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsUseSmbWithLowTt(
        "enableSMB_with_temptarget", true, KeysStrings.pref_title_aps_use_smb_with_low_tt, KeysStrings.pref_summary_aps_use_smb_with_low_tt, defaultedBySM = true, dependency = ApsUseSmb,
        visibility = ElementVisibility { !it.preferences.get(ApsUseSmbAlways) || !it.advancedFilteringSupported },
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsUseSmbAfterCarbs(
        "enableSMB_after_carbs", true, KeysStrings.pref_title_aps_use_smb_after_carbs, KeysStrings.pref_summary_aps_use_smb_after_carbs, defaultedBySM = true, dependency = ApsUseSmb,
        visibility = ElementVisibility { !it.preferences.get(ApsUseSmbAlways) && it.advancedFilteringSupported },
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsUseUam("use_uam", true, KeysStrings.pref_title_aps_use_uam, KeysStrings.pref_summary_aps_use_uam, defaultedBySM = true, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    ApsSensitivityRaisesTarget(
        "sensitivity_raises_target", true, KeysStrings.pref_title_aps_sensitivity_raises_target, KeysStrings.pref_summary_aps_sensitivity_raises_target, defaultedBySM = true,
        visibility = ElementVisibility {
            if (it.preferences.get(ApsUseDynamicSensitivity)) {
                it.preferences.get(ApsDynIsfAdjustSensitivity)
            } else {
                it.preferences.get(ApsUseAutosens)
            }
        },
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsResistanceLowersTarget(
        "resistance_lowers_target", true, KeysStrings.pref_title_aps_resistance_lowers_target, KeysStrings.pref_summary_aps_resistance_lowers_target, defaultedBySM = true,
        visibility = ElementVisibility {
            if (it.preferences.get(ApsUseDynamicSensitivity)) {
                it.preferences.get(ApsDynIsfAdjustSensitivity)
            } else {
                it.preferences.get(ApsUseAutosens)
            }
        },
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAlwaysUseShortDeltas(
        "always_use_shortavg",
        false,
        KeysStrings.pref_title_aps_always_use_short_deltas,
        KeysStrings.pref_summary_aps_always_use_short_deltas,
        defaultedBySM = true,
        hideParentScreenIfHidden = true,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsDynIsfAdjustSensitivity(
        "dynisf_adjust_sensitivity",
        false,
        KeysStrings.pref_title_aps_dynisf_adjust_sensitivity,
        KeysStrings.pref_summary_aps_dynisf_adjust_sensitivity,
        defaultedBySM = true,
        dependency = ApsUseDynamicSensitivity,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAmaAutosensAdjustTargets(
        "autosens_adjust_targets",
        true,
        KeysStrings.pref_title_aps_autosens_adjust_targets,
        KeysStrings.pref_summary_aps_autosens_adjust_targets,
        defaultedBySM = true,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAutoIsfHighTtRaisesSens(
        "high_temptarget_raises_sensitivity",
        false,
        KeysStrings.pref_title_aps_high_tt_raises_sensitivity,
        KeysStrings.pref_summary_aps_high_tt_raises_sensitivity,
        defaultedBySM = true,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAutoIsfLowTtLowersSens(
        "low_temptarget_lowers_sensitivity",
        false,
        KeysStrings.pref_title_aps_low_tt_lowers_sensitivity,
        KeysStrings.pref_summary_aps_low_tt_lowers_sensitivity,
        defaultedBySM = true,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsUseAutoIsfWeights("openapsama_enable_autoISF", false, KeysStrings.pref_title_aps_use_autoisf_weights, KeysStrings.pref_summary_aps_use_autoisf_weights, defaultedBySM = true, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    ApsAutoIsfSmbOnEvenTarget(
        "Enable alternative activation of SMB always",
        false,
        KeysStrings.pref_title_aps_smb_on_even_target,
        KeysStrings.pref_summary_aps_smb_on_even_target,
        defaultedBySM = true,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),

    MaintenanceEnableFabric("enable_fabric2", true, KeysStrings.pref_title_maintenance_enable_fabric, defaultedBySM = true, hideParentScreenIfHidden = true),

    // Master-only (not a follower client): unattended settings export backs up the local config, which on a
    // client is derived from the master. showInNsClientMode=false hides it in apsMode + pumpControlMode only;
    // hideParentScreenIfHidden collapses the now-empty "Unattended Settings Export" subscreen on a client.
    MaintenanceEnableExportSettingsAutomation("enable_unattended_export", false, KeysStrings.pref_title_maintenance_enable_export_automation, defaultedBySM = false, showInNsClientMode = false, hideParentScreenIfHidden = true),

    AutotuneAutoSwitchProfile("autotune_auto", false, KeysStrings.pref_title_autotune_auto_switch_profile, KeysStrings.pref_summary_autotune_auto_switch_profile),
    AutotuneCategorizeUamAsBasal("categorize_uam_as_basal", false, KeysStrings.pref_title_autotune_categorize_uam_as_basal, KeysStrings.pref_summary_autotune_categorize_uam_as_basal),
    AutotuneTuneInsulinCurve("autotune_tune_insulin_curve", false, KeysStrings.pref_title_autotune_tune_insulin_curve),
    AutotuneCircadianIcIsf("autotune_circadian_ic_isf", false, KeysStrings.pref_title_autotune_circadian_ic_isf, KeysStrings.pref_summary_autotune_circadian_ic_isf),
    AutotuneAdditionalLog("autotune_additional_log", false, KeysStrings.pref_title_autotune_additional_log),

    SmsAllowRemoteCommands("smscommunicator_remotecommandsallowed", false, KeysStrings.pref_title_sms_allow_remote_commands),
    SmsReportPumpUnreachable("smscommunicator_report_pump_unreachable", true, KeysStrings.pref_title_sms_report_pump_unreachable, KeysStrings.pref_summary_sms_report_pump_unreachable),

    VirtualPumpStatusUpload("virtualpump_uploadstatus", false, KeysStrings.pref_title_virtual_pump_status_upload, showInNsClientMode = false),
    NsClientUploadData("ns_upload", true, KeysStrings.pref_title_ns_upload_data, KeysStrings.pref_summary_ns_upload_data, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptCgmData("ns_receive_cgm", false, KeysStrings.pref_title_ns_receive_cgm, KeysStrings.pref_summary_ns_receive_cgm, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptProfileStore("ns_receive_profile_store", false, KeysStrings.pref_title_ns_receive_profile_store, KeysStrings.pref_summary_ns_receive_profile_store, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptTempTarget("ns_receive_temp_target", false, KeysStrings.pref_title_ns_receive_temp_target, KeysStrings.pref_summary_ns_receive_temp_target, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptProfileSwitch("ns_receive_profile_switch", false, KeysStrings.pref_title_ns_receive_profile_switch, KeysStrings.pref_summary_ns_receive_profile_switch, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptInsulin("ns_receive_insulin", false, KeysStrings.pref_title_ns_receive_insulin, KeysStrings.pref_summary_ns_receive_insulin, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptCarbs("ns_receive_carbs", false, KeysStrings.pref_title_ns_receive_carbs, KeysStrings.pref_summary_ns_receive_carbs, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptTherapyEvent("ns_receive_therapy_events", false, KeysStrings.pref_title_ns_receive_therapy_event, KeysStrings.pref_summary_ns_receive_therapy_event, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptRunningMode("ns_receive_running_mode", false, KeysStrings.pref_title_ns_receive_running_mode, KeysStrings.pref_summary_ns_receive_running_mode, showInNsClientMode = false, hideParentScreenIfHidden = true),
    NsClientAcceptTbrEb("ns_receive_tbr_eb", false, KeysStrings.pref_title_ns_receive_tbr_eb, KeysStrings.pref_summary_ns_receive_tbr_eb, showInNsClientMode = false, engineeringModeOnly = true),
    NsClientNotificationsFromAlarms("ns_alarms", false, KeysStrings.pref_title_ns_notifications_from_alarms, calculatedDefaultValue = true),
    NsClientNotificationsFromAnnouncements("ns_announcements", false, KeysStrings.pref_title_ns_notifications_from_announcements, calculatedDefaultValue = true),
    NsClientUseCellular("ns_cellular", true, KeysStrings.pref_title_ns_use_cellular),
    NsClientUseRoaming("ns_allow_roaming", true, KeysStrings.pref_title_ns_use_roaming, dependency = NsClientUseCellular),
    NsClientUseWifi("ns_wifi", true, KeysStrings.pref_title_ns_use_wifi),
    NsClientUseOnBattery("ns_battery", true, KeysStrings.pref_title_ns_use_on_battery),
    NsClientUseOnCharging("ns_charging", true, KeysStrings.pref_title_ns_use_on_charging),
    NsClientLogAppStart("ns_log_app_started_event", false, KeysStrings.pref_title_ns_log_app_start, calculatedDefaultValue = true),
    NsClientCreateAnnouncementsFromErrors("ns_create_announcements_from_errors", false, KeysStrings.pref_title_ns_create_announcements_from_errors, calculatedDefaultValue = true, showInNsClientMode = false),
    NsClientCreateAnnouncementsFromCarbsReq("ns_create_announcements_from_carbs_req", false, KeysStrings.pref_title_ns_create_announcements_from_carbs_req, calculatedDefaultValue = true, showInNsClientMode = false),
    NsClientSlowSync("ns_sync_slow", false, KeysStrings.pref_title_ns_slow_sync),
    NsClient3UseWs("ns_use_ws", true, KeysStrings.pref_title_ns_use_ws, KeysStrings.pref_summary_ns_use_ws),
    NsClientAllowClientControl(
        "ns_allow_client_control", false,
        KeysStrings.pref_title_ns_allow_client_control, KeysStrings.pref_summary_ns_allow_client_control,
        // The rich stop/allow-communication switch lives on the Authorized clients screen; it is ALSO exposed in a
        // "Remote control" category on the NSCv3 settings screen (NSClientV3Plugin.getPreferenceScreenContent) so it
        // is reachable from search. Default OFF, but ON in simple mode (resolved in PreferencesImpl.calculatedDefaultValue). Hidden on a client.
        calculatedDefaultValue = true, showInNsClientMode = false,
        // Remote control rides the WebSocket — hide the toggle (and its single-item "Remote control" parent category)
        // when WS is off, and on a client where the key is already hidden (so the category never shows empty).
        dependency = NsClient3UseWs, hideParentScreenIfHidden = true,
        // Synced master→client (MasterOnly — the client mirrors, never pushes back) so a paired client knows
        // whether the master is accepting commands and can gate its UI. buildSyncedPrefs publishes the EFFECTIVE
        // value for this key (see RunningConfigurationImpl), not the raw default.
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.MasterOnly)
    ),
    OpenHumansWifiOnly("oh_wifi_only", true, KeysStrings.pref_title_openhumans_wifi_only),
    OpenHumansChargingOnly("oh_charging_only", false, KeysStrings.pref_title_openhumans_charging_only),
    XdripSendStatus("xdrip_send_status", false, KeysStrings.pref_title_xdrip_send_status),
    XdripSendDetailedIob("xdripstatus_detailediob", true, KeysStrings.pref_title_xdrip_send_detailed_iob, KeysStrings.pref_summary_xdrip_send_detailed_iob, defaultedBySM = true, hideParentScreenIfHidden = true),
    XdripSendBgi("xdripstatus_showbgi", true, KeysStrings.pref_title_xdrip_send_bgi, KeysStrings.pref_summary_xdrip_send_bgi, defaultedBySM = true, hideParentScreenIfHidden = true),
    WearControl(key = "wearcontrol", defaultValue = false, title = KeysStrings.pref_title_wear_control, summary = KeysStrings.pref_summary_wear_control),
    WearWizardBg(key = "wearwizard_bg", defaultValue = true, title = KeysStrings.pref_title_wear_wizard_bg, dependency = WearControl, hideParentScreenIfHidden = true),
    WearWizardTt(key = "wearwizard_tt", defaultValue = false, title = KeysStrings.pref_title_wear_wizard_tt, dependency = WearControl, hideParentScreenIfHidden = true),
    WearWizardTrend(key = "wearwizard_trend", defaultValue = false, title = KeysStrings.pref_title_wear_wizard_trend, dependency = WearControl, hideParentScreenIfHidden = true),
    WearWizardCob(key = "wearwizard_cob", defaultValue = true, title = KeysStrings.pref_title_wear_wizard_cob, dependency = WearControl, hideParentScreenIfHidden = true),
    WearWizardIob(key = "wearwizard_iob", defaultValue = true, title = KeysStrings.pref_title_wear_wizard_iob, dependency = WearControl, hideParentScreenIfHidden = true),
    WearCustomWatchfaceAuthorization(key = "wear_custom_watchface_autorization", defaultValue = false, title = KeysStrings.pref_title_wear_custom_watchface_authorization),
    WearNotifyOnSmb(key = "wear_notifySMB", defaultValue = true, title = KeysStrings.pref_title_wear_notify_on_smb, summary = KeysStrings.pref_summary_wear_notify_on_smb),
    WearBroadcastData(key = "wear_broadcast_data", defaultValue = false, title = KeysStrings.pref_title_wear_broadcast_data, summary = KeysStrings.pref_summary_wear_broadcast_data, showInApsMode = false, showInPumpControlMode = false),

    SiteRotationManagePump("site_rotation_manage_pump", defaultValue = false, title = KeysStrings.pref_title_site_rotation_manage_pump, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    SiteRotationManageCgm("site_rotation_manage_cgm", defaultValue = false, title = KeysStrings.pref_title_site_rotation_manage_cgm, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),

    ;

}

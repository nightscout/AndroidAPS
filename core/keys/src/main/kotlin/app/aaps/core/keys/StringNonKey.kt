package app.aaps.core.keys

import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import app.aaps.core.keys.interfaces.SyncChannel
import app.aaps.core.keys.interfaces.SyncDirection
import app.aaps.core.keys.interfaces.SyncSpec

enum class StringNonKey(
    override val key: String,
    override val defaultValue: String,
    override val exportable: Boolean = true,
    override val sync: SyncSpec? = null
) : StringNonPreferenceKey {

    QuickWizard(key = "QuickWizard", defaultValue = "[]", sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    WearCwfWatchfaceName(key = "wear_cwf_watchface_name", defaultValue = ""),
    WearCwfAuthorVersion(key = "wear_cwf_author_version", defaultValue = ""),
    WearCwfFileName(key = "wear_cwf_filename", defaultValue = ""),
    BolusInfoStorage(key = "key_bolus_storage", defaultValue = ""),
    ActivePumpType(key = "active_pump_type", defaultValue = ""),
    ActivePumpSerialNumber(key = "active_pump_serial_number", defaultValue = ""),
    SmsOtpSecret("smscommunicator_otp_secret", defaultValue = ""),
    TotalBaseBasal("TBB", defaultValue = "10.00"),
    PumpCommonBolusStorage(key = "pump_sync_storage_bolus", defaultValue = ""),
    PumpCommonTbrStorage(key = "pump_sync_storage_tbr", defaultValue = ""),
    TempTargetPresets(key = "temp_target_presets", defaultValue = "[]", sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    SceneDefinitions(key = "scene_definitions", defaultValue = "[]", sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    ActiveScene(key = "active_scene", defaultValue = ""),

    // Standalone Automation runtime. In core/keys (not the automation module) so the client→master
    // sync publisher/receiver in :plugins:sync can observe it without an inter-module dependency.
    AutomationEvents(key = "AUTOMATION_EVENTS", defaultValue = "", sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    QuickLaunchActions(key = "quick_launch_actions", defaultValue = "[{\"type\":\"wizard\"},{\"type\":\"quick_launch_config\"}]"),
    InsulinConfiguration("insulin_configuration", "{}", sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    ComposeGraphConfig("compose_graphconfig", ""),

    // Synthetic mirror of the active plugin per single-select category (value = plugin pluginId, defaults to
    // javaClass.simpleName). Bidirectional for APS/SENSITIVITY/SMOOTHING/CALIBRATION (a client may switch the
    // master's selection, gated on master reachability); generated-only for PUMP/BGSOURCE.
    // exportable=false: regenerated on start from ConfigBuilderEnabled (which carries selection in backups).
    // Reached only via ConfigBuilderImpl.activePluginKey(PluginType) (the exhaustive-when SSOT).
    ActivePluginAps("active_plugin_aps", "", exportable = false, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    ActivePluginSensitivity("active_plugin_sensitivity", "", exportable = false, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    ActivePluginSmoothing("active_plugin_smoothing", "", exportable = false, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    ActivePluginCalibration("active_plugin_calibration", "", exportable = false, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    ActivePluginPump("active_plugin_pump", "", exportable = false),
    ActivePluginBgSource("active_plugin_bgsource", "", exportable = false),

    NotificationReaderPackages(key = "notification_reader_packages", defaultValue = ""),
    NotificationReaderDedupState(key = "notification_reader_dedup_state", defaultValue = ""),

    // Google Drive settings (internal, no preferences UI)
    @Deprecated("fix")
    GoogleDriveStorageType(key = "google_drive_storage_type", defaultValue = "local"),
    GoogleDriveFolderId(key = "google_drive_folder_id", defaultValue = ""),
    GoogleDriveRefreshToken(key = "google_drive_refresh_token", defaultValue = ""),

    // NSCv3 client-control pairing (excluded from export — secrets / monotonic state)
    NsClientControlAuthorizedClients(key = "nsclient_control_authorized_clients", defaultValue = "[]", exportable = false),
    NsClientControlOwnInstallId(key = "nsclient_control_own_install_id", defaultValue = "", exportable = false),
    NsClientControlMasterInstallId(key = "nsclient_control_master_install_id", defaultValue = "", exportable = false),
    NsClientControlClientId(key = "nsclient_control_client_id", defaultValue = "", exportable = false),
    NsClientControlMasterSecretEnc(key = "nsclient_control_master_secret_enc", defaultValue = "", exportable = false),

}

package app.aaps.core.keys

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

enum class StringNonKey(
    override val key: String,
    override val defaultValue: String,
    override val exportable: Boolean = true
) : StringNonPreferenceKey {

    QuickWizard(key = "QuickWizard", defaultValue = "[]"),
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
    TempTargetPresets(key = "temp_target_presets", defaultValue = "[]"),
    QuickLaunchActions(key = "quick_launch_actions", defaultValue = "[{\"type\":\"wizard\"},{\"type\":\"quick_launch_config\"}]"),
    InsulinConfiguration("insulin_configuration", "{}"),
    ComposeGraphConfig("compose_graphconfig", ""),

    NotificationReaderPackages(key = "notification_reader_packages", defaultValue = ""),

    // Google Drive settings (internal, no preferences UI)
    GoogleDriveStorageType(key = "google_drive_storage_type", defaultValue = "local"),
    GoogleDriveFolderId(key = "google_drive_folder_id", defaultValue = ""),
    GoogleDriveRefreshToken(key = "google_drive_refresh_token", defaultValue = ""),

}

package app.aaps.pump.medtrum.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.pump.medtrum.R

enum class MedtrumBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val titleResId: Int = 0,
    override val summaryResId: Int? = null,
    override val calculatedDefaultValue: Boolean = false,
    override val engineeringModeOnly: Boolean = false,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val exportable: Boolean = true
) : BooleanPreferenceKey {

    MedtrumWarningNotification(
        key = "pump_warning_notification",
        defaultValue = true,
        titleResId = R.string.pump_warning_notification_title,
        summaryResId = R.string.pump_warning_notification_summary
    ),
    MedtrumPatchExpiration(
        key = "patch_expiration",
        defaultValue = true,
        titleResId = R.string.patch_expiration_title,
        summaryResId = R.string.patch_expiration_summary
    ),
    MedtrumScanOnConnectionErrors(
        key = "scan_on_connection_error",
        defaultValue = false,
        titleResId = R.string.scan_on_connection_error_title,
        summaryResId = R.string.scan_on_connection_error_summary
    ),
}

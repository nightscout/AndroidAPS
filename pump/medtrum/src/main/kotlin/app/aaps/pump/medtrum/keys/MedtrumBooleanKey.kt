package app.aaps.pump.medtrum.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey

enum class MedtrumBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
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

    MedtrumWarningNotification("pump_warning_notification", true),
    MedtrumPatchExpiration("patch_expiration", true),
    MedtrumScanOnConnectionErrors("scan_on_connection_error", false),
}

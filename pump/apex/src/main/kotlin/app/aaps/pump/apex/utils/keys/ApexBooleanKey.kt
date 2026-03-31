package app.aaps.pump.apex.utils.keys

import app.aaps.core.keys.BooleanPreferenceKey

enum class ApexBooleanKey(
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
    override val hideParentScreenIfHidden: Boolean = false
) : BooleanPreferenceKey {
    LogInsulinChange("apex_log_insulin_change", true),
    LogBatteryChange("apex_log_battery_change", true),
}

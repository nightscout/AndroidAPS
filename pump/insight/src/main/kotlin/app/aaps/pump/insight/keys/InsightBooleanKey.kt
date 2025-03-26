package app.aaps.pump.insight.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey

enum class InsightBooleanKey(
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

    LogReservoirChanges("insight_log_reservoir_changes", false),
    LogTubeChanges("insight_log_tube_changes", false),
    LogSiteChanges("insight_log_site_changes", false),
    LogBatteryChanges("insight_log_battery_changes", false),
    LogOperatingModeChanges("insight_log_operating_mode_changes", false),
    LogAlerts("insight_log_alerts", false),
    EnableTbrEmulation("insight_enable_tbr_emulation", false),
    DisableVibration("insight_disable_vibration", false),
    DisableVibrationAuto("insight_disable_vibration_auto", false),
}

package app.aaps.pump.insight.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.pump.insight.R

enum class InsightBooleanKey(
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

    LogReservoirChanges("insight_log_reservoir_changes", false, titleResId = R.string.log_reservoir_changes),
    LogTubeChanges("insight_log_tube_changes", false, titleResId = R.string.log_tube_changes),
    LogSiteChanges("insight_log_site_changes", false, titleResId = R.string.log_site_changes),
    LogBatteryChanges("insight_log_battery_changes", false, titleResId = R.string.log_battery_changes),
    LogOperatingModeChanges("insight_log_operating_mode_changes", false, titleResId = R.string.log_operating_mode_changes),
    LogAlerts("insight_log_alerts", false, titleResId = R.string.log_alerts),
    EnableTbrEmulation("insight_enable_tbr_emulation", false, titleResId = R.string.enable_tbr_emulation, summaryResId = R.string.enable_tbr_emulation_summary),
    DisableVibration("insight_disable_vibration", false, titleResId = R.string.disable_vibration, summaryResId = R.string.disable_vibration_summary),
    DisableVibrationAuto("insight_disable_vibration_auto", false, titleResId = R.string.disable_vibration_auto, summaryResId = R.string.disable_vibration_auto_summary),
}

package app.aaps.pump.insight.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.insight.R

enum class InsightBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    private val titleResId: Int,
    private val summaryResId: Int? = null,
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
    ;

    override val title: TextRef = TextRef.AndroidRes(titleResId)
    override val summary: TextRef? = summaryResId?.let { TextRef.AndroidRes(it) }
}

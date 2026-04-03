package app.aaps.pump.common.hw.rileylink.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.pump.common.hw.rileylink.R

enum class RileylinkBooleanPreferenceKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val titleResId: Int = 0,
    override val summaryResId: Int? = null,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val calculatedDefaultValue: Boolean = false,
    override val engineeringModeOnly: Boolean = false,
    override val exportable: Boolean = true
) : BooleanPreferenceKey {

    OrangeUseScanning(
        key = "pref_orange_use_scanning",
        defaultValue = false,
        titleResId = R.string.orange_use_scanning_level,
        summaryResId = R.string.orange_use_scanning_level_summary
    ),
    ShowReportedBatteryLevel(
        key = "pref_riley_link_show_reported_battery_level",
        defaultValue = false,
        titleResId = R.string.riley_link_show_battery_level,
        summaryResId = R.string.riley_link_show_battery_level_summary
    ),
}
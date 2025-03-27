package app.aaps.pump.common.hw.rileylink.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey

enum class RileylinkBooleanPreferenceKey(
    override val key: String,
    override val defaultValue: Boolean,
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

    OrangeUseScanning("pref_orange_use_scanning", false),
    ShowReportedBatteryLevel("pref_riley_link_show_reported_battery_level", false),
}
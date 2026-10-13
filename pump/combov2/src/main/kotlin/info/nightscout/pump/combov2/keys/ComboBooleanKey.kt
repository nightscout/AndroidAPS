package info.nightscout.pump.combov2.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey

enum class ComboBooleanKey(
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

    AutomaticReservoirEntry("combov2_automatic_reservoir_entry", true),
    AutomaticBatteryEntry("combov2_automatic_battery_entry", true),
    VerboseLogging("combov2_verbose_logging", false),
}

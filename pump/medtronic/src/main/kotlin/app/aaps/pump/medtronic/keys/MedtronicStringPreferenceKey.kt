package app.aaps.pump.medtronic.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.StringPreferenceKey

enum class MedtronicStringPreferenceKey(
    override val key: String,
    override val defaultValue: String,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val isPassword: Boolean = false,
    override val isPin: Boolean = false,
    override val exportable: Boolean = true
) : StringPreferenceKey {

    Serial("pref_medtronic_serial", "000000"),
    PumpType("pref_medtronic_pump_type", ""),
    PumpFrequency("pref_medtronic_frequency", "medtronic_pump_frequency_us_ca"),
    BatteryType("pref_medtronic_battery_type", app.aaps.pump.medtronic.defs.BatteryType.None.key),
}
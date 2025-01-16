package app.aaps.pump.medtronic.keys

import app.aaps.core.keys.BooleanPreferenceKey
import app.aaps.core.keys.StringPreferenceKey

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
    override val isPin: Boolean = false
) : StringPreferenceKey {

    Serial("pref_medtronic_serial", "000000"),
    PumpType("pref_medtronic_pump_type", ""),
    PumpFreq("pref_medtronic_frequency", ""),
    Encoding("pref_medtronic_encoding", "RileyLink 4b6b Encoding"),
    BatteryType("pref_medtronic_battery_type", ""),
}
package app.aaps.pump.equil.keys

import app.aaps.core.keys.BooleanPreferenceKey

enum class EquilBooleanKey(
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

    EquilAlarmBattery("key_equil_alarm_battery", true),
    EquilAlarmInsulin("key_equil_alarm_insulin", true),
}

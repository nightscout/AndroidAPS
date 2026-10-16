package app.aaps.pump.equil.keys

import app.aaps.core.keys.interfaces.BooleanNonPreferenceKey

enum class EquilBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
) : BooleanNonPreferenceKey {

    BasalSet("key_equil_basal_set", false),

    // One flag per alarm level: each level alarms once, until the next reservoir change resets them.
    // New levels are added at the end so the existing entries keep their ordinals.
    AlarmBattery10("key_equil_alarm_battery_10", false),
    AlarmInsulin10("key_equil_alarm_insulin_10", false),
    AlarmInsulin5("key_equil_alarm_insulin_5", false),
    AlarmInsulin2("key_equil_alarm_insulin_2", false),
    AlarmInsulin0("key_equil_alarm_insulin_0", false),
    AlarmBattery5("key_equil_alarm_battery_5", false),
}

package app.aaps.pump.omnipod.common.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey

enum class OmnipodIntPreferenceKey(
    override val key: String,
    override val min: Int,
    override val max: Int,
    override val defaultValue: Int,
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
) : IntPreferenceKey {

    ExpirationReminderHours("AAPS.Omnipod.expiration_reminder_hours_before_expiry", min = 1, max = 24, defaultValue = 4, dependency = OmnipodBooleanPreferenceKey.ExpirationReminder),
    ExpirationAlarmHours("AAPS.Omnipod.expiration_alarm_hours_before_shutdown", min = 1, max = 8, defaultValue = 8, dependency = OmnipodBooleanPreferenceKey.ExpirationAlarm),
    LowReservoirAlertUnits("AAPS.Omnipod.low_reservoir_alert_units", min = 5, max = 50, defaultValue = 20, dependency = OmnipodBooleanPreferenceKey.LowReservoirAlert),
}
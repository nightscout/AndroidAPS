package app.aaps.pump.medtrum.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey

enum class MedtrumIntKey(
    override val key: String,
    override val defaultValue: Int,
    override var min: Int = Int.MIN_VALUE,
    override var max: Int = Int.MAX_VALUE,
    override val calculatedDefaultValue: Boolean = false,
    override val engineeringModeOnly: Boolean = false,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val exportable: Boolean = true,
    override val hideParentScreenIfHidden: Boolean = false,
) : IntPreferenceKey {

    MedtrumPumpExpiryWarningHours("pump_expiry_warning_hour", defaultValue = 72, min = 48, max = 80, dependency = MedtrumBooleanKey.MedtrumPatchExpiration),
    MedtrumHourlyMaxInsulin("hourly_max_insulin", defaultValue = 25, min = 10, max = 40),
    MedtrumDailyMaxInsulin("daily_max_insulin", defaultValue = 80, min = 20, max = 180),
}

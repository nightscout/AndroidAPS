package app.aaps.pump.medtrum.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.pump.medtrum.R

enum class MedtrumIntKey(
    override val key: String,
    override val defaultValue: Int,
    override val titleResId: Int = 0,
    override val summaryResId: Int? = null,
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

    MedtrumPumpExpiryWarningHours(
        key = "pump_expiry_warning_hour",
        defaultValue = 72,
        titleResId = R.string.pump_warning_expiry_hour_title,
        summaryResId = R.string.pump_warning_expiry_hour_summary,
        min = 48,
        max = 80,
        dependency = MedtrumBooleanKey.MedtrumPatchExpiration
    ),
    MedtrumHourlyMaxInsulin(
        key = "hourly_max_insulin",
        defaultValue = 25,
        titleResId = R.string.hourly_max_insulin_title,
        summaryResId = R.string.hourly_max_insulin_summary,
        min = 10,
        max = 40
    ),
    MedtrumDailyMaxInsulin(
        key = "daily_max_insulin",
        defaultValue = 80,
        titleResId = R.string.daily_max_insulin_title,
        summaryResId = R.string.daily_max_insulin_summary,
        min = 20,
        max = 180
    ),
}

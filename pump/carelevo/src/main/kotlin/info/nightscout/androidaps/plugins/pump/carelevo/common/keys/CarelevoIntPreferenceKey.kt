package info.nightscout.androidaps.plugins.pump.carelevo.common.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import info.nightscout.androidaps.plugins.pump.carelevo.R

enum class CarelevoIntPreferenceKey(
    override val key: String,
    override val defaultValue: Int,
    override val titleResId: Int = 0,
    override val min: Int = Int.MIN_VALUE,
    override val max: Int = Int.MAX_VALUE,
    override val preferenceType: PreferenceType = PreferenceType.TEXT_FIELD,
    override val entries: Map<Int, Int> = emptyMap(),
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

    CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS(
        "CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS",
        116,
        R.string.carelevo_patch_expiration_reminders_title_value,
        preferenceType = PreferenceType.LIST
    ),
    CARELEVO_LOW_INSULIN_EXPIRATION_REMINDER_HOURS(
        "CARELEVO_LOW_INSULIN_EXPIRATION_REMINDER_HOURS",
        30,
        R.string.carelevo_low_reservoir_reminders_title_value,
        preferenceType = PreferenceType.LIST
    )
}

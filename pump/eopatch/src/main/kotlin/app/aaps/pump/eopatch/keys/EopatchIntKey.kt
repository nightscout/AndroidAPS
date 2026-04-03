package app.aaps.pump.eopatch.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.pump.eopatch.R

enum class EopatchIntKey(
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

    LowReservoirReminder("eopatch_low_reservoir_reminders", 10, titleResId = R.string.low_reservoir, preferenceType = PreferenceType.LIST),
    ExpirationReminder("eopatch_expiration_reminders", 4, titleResId = R.string.patch_expiration_reminders, preferenceType = PreferenceType.LIST),
}

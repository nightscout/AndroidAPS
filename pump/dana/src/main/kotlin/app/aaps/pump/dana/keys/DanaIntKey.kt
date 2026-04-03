package app.aaps.pump.dana.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.pump.dana.R

enum class DanaIntKey(
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

    BolusSpeed(
        key = "danars_bolusspeed",
        defaultValue = 0,
        titleResId = app.aaps.core.ui.R.string.bolusspeed,
        preferenceType = PreferenceType.LIST,
        entries = mapOf(
            0 to R.string.bolus_speed_12,
            1 to R.string.bolus_speed_30,
            2 to R.string.bolus_speed_60
        )
    ),
}

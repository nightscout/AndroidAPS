package app.aaps.pump.diaconn.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.pump.diaconn.R

enum class DiaconnIntKey(
    override val key: String,
    override val defaultValue: Int,
    override val min: Int = Int.MIN_VALUE,
    override val max: Int = Int.MAX_VALUE,
    override val titleResId: Int = 0,
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
        key = "g8_bolusspeed",
        defaultValue = 5,
        titleResId = R.string.bolusspeed,
        preferenceType = PreferenceType.LIST,
        entries = mapOf(
            1 to R.string.bolus_speed_1,
            2 to R.string.bolus_speed_2,
            3 to R.string.bolus_speed_3,
            4 to R.string.bolus_speed_4,
            5 to R.string.bolus_speed_5,
            6 to R.string.bolus_speed_6,
            7 to R.string.bolus_speed_7,
            8 to R.string.bolus_speed_8
        )
    ),
}

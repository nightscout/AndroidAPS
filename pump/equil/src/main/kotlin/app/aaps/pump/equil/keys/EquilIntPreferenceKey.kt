package app.aaps.pump.equil.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.pump.equil.R

enum class EquilIntPreferenceKey(
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

    EquilTone(
        key = "key_equil_tone",
        defaultValue = 3,
        min = 0,
        max = 3,
        titleResId = R.string.equil_tone,
        preferenceType = PreferenceType.LIST,
        entries = mapOf(
            0 to R.string.equil_tone_mode_mute,
            1 to R.string.equil_tone_mode_tone,
            2 to R.string.equil_tone_mode_shake,
            3 to R.string.equil_tone_mode_tone_and_shake
        )
    ),
}

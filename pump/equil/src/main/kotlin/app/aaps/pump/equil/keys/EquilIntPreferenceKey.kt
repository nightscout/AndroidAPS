package app.aaps.pump.equil.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.equil.R

enum class EquilIntPreferenceKey(
    override val key: String,
    override val defaultValue: Int,
    override val min: Int = Int.MIN_VALUE,
    override val max: Int = Int.MAX_VALUE,
    private val titleResId: Int,
    override val preferenceType: PreferenceType = PreferenceType.TEXT_FIELD,
    private val entriesResIds: Map<Int, Int> = emptyMap(),
    override val calculatedDefaultValue: Boolean = false,
    override val engineeringModeOnly: Boolean = false,
    override val defaultedBySM: Boolean = false,
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
        entriesResIds = mapOf(
            0 to R.string.equil_tone_mode_mute,
            1 to R.string.equil_tone_mode_tone,
            2 to R.string.equil_tone_mode_shake,
            3 to R.string.equil_tone_mode_tone_and_shake
        )
    ),
    ;

    override val title: TextRef = TextRef.AndroidRes(titleResId)
    override val entries: Map<Int, TextRef> = entriesResIds.mapValues { TextRef.AndroidRes(it.value) }
}

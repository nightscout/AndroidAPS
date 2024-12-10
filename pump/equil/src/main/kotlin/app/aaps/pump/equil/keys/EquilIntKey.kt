package app.aaps.pump.equil.keys

import app.aaps.core.keys.BooleanPreferenceKey
import app.aaps.core.keys.IntPreferenceKey

enum class EquilIntKey(
    override val key: String,
    override val defaultValue: Int,
    override val min: Int,
    override val max: Int,
    override val calculatedDefaultValue: Boolean = false,
    override val engineeringModeOnly: Boolean = false,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false
) : IntPreferenceKey {

    EquilTone("key_equil_tone", 3, 0 ,3),
}

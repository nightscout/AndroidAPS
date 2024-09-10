package app.aaps.pump.dana.keys

import app.aaps.core.keys.BooleanPreferenceKey
import app.aaps.core.keys.IntPreferenceKey

enum class DanaIntKey(
    override val key: String,
    override val defaultValue: Int,
    override val min: Int = 0,
    override val max: Int = 9999,
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

    DanaRPassword("danar_password", -2),
    DanaRsBolusSpeed("danars_bolusspeed", 0),
}

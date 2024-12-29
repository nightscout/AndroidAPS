package app.aaps.pump.equil.keys

import app.aaps.core.keys.BooleanPreferenceKey
import app.aaps.core.keys.DoublePreferenceKey

enum class EquilDoubleKey(
    override val key: String,
    override val defaultValue: Double,
    override val min: Double,
    override val max: Double,
    override val defaultedBySM: Boolean = false,
    override val calculatedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false
) : DoublePreferenceKey {

    EquilMaxBolus("equil_maxbolus", 10.0, 0.1, 25.0),
}

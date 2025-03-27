package app.aaps.pump.equil.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.DoublePreferenceKey

enum class EquilDoublePreferenceKey(
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
    override val exportable: Boolean = true,
    override val hideParentScreenIfHidden: Boolean = false,
) : DoublePreferenceKey {

    EquilMaxBolus("equil_maxbolus", 10.0, 0.1, 25.0),
}

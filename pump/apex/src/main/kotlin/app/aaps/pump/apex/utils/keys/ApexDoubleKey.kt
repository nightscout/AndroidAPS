package app.aaps.pump.apex.utils.keys

import app.aaps.core.keys.BooleanPreferenceKey
import app.aaps.core.keys.DoublePreferenceKey

enum class ApexDoubleKey(
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
    override val hideParentScreenIfHidden: Boolean = false,
): DoublePreferenceKey {
    // 0 == uninitialized
    MaxBasal("apex_max_basal", 0.0, 0.0, 25.0),
    MaxBolus("apex_max_bolus", 0.0, 0.0, 25.0),
}
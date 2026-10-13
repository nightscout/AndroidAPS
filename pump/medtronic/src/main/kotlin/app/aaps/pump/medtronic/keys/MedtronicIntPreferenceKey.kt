package app.aaps.pump.medtronic.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey

enum class MedtronicIntPreferenceKey(
    override val key: String,
    override val defaultValue: Int,
    override val min: Int = Int.MIN_VALUE,
    override val max: Int = Int.MAX_VALUE,
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

    MaxBasal("pref_medtronic_max_basal", 35, min = 1, max = 35),
    MaxBolus("pref_medtronic_max_bolus", 25, min = 1, max = 25),
    BolusDelay("pref_medtronic_bolus_delay", 10, min = 5, max = 15),
}
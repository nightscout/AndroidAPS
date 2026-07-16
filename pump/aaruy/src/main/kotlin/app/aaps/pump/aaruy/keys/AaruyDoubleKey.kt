package app.aaps.pump.aaruy.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.DoublePreferenceKey
import app.aaps.core.keys.interfaces.LongPreferenceKey
import app.aaps.core.keys.interfaces.StringPreferenceKey

enum class AaruyDoubleKey(
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
    override val exportable: Boolean = true
) : DoublePreferenceKey {
    PumpBolusAmount("key_aaruy_bolus_amount_to_be_delivered", 0.0, 0.0, 25.0),
    PumpLastBolusAmount("key_aaruy_last_bolus_amount", 0.0, 0.0, 25.0),
    PumpMaxBasal("key_param_max_basal", 2.0, 0.1, 35.0),
    PumpMaxBolus("key_param_max_bolus", 10.0, 1.0, 25.0),
    PumpLowAlert("key_param_low_alert", 20.0, 10.0, 50.0),
}
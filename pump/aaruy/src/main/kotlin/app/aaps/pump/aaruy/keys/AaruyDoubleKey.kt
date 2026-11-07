package app.aaps.pump.aaruy.keys

import app.aaps.core.keys.interfaces.DoubleNonPreferenceKey

enum class AaruyDoubleKey(
    override val key: String,
    override val defaultValue: Double,
) : DoubleNonPreferenceKey {

    PumpBolusAmount("key_aaruy_bolus_amount_to_be_delivered", 0.0),
    PumpLastBolusAmount("key_aaruy_last_bolus_amount", 0.0),
    PumpMaxBasal("key_param_max_basal", 2.0),
    PumpMaxBolus("key_param_max_bolus", 10.0),
    PumpLowAlert("key_param_low_alert", 20.0),
}

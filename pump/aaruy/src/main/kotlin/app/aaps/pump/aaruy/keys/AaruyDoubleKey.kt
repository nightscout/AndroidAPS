package app.aaps.pump.aaruy.keys

import app.aaps.core.keys.interfaces.DoubleNonPreferenceKey

/** Only what the pump cannot tell again after an app restart is kept. What only this phone knows is not exported. */
enum class AaruyDoubleKey(
    override val key: String,
    override val defaultValue: Double,
    override val exportable: Boolean = true,
) : DoubleNonPreferenceKey {

    /** The amount asked for the last bolus. The pump reports only what it delivered. */
    PumpBolusAmount("key_aaruy_bolus_amount_to_be_delivered", 0.0, exportable = false),
}

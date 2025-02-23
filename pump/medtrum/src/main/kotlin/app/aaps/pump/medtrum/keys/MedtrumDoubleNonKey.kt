package app.aaps.pump.medtrum.keys

import app.aaps.core.keys.interfaces.DoubleNonPreferenceKey

enum class MedtrumDoubleNonKey(
    override val key: String,
    override val defaultValue: Double,
    override val exportable: Boolean = true
) : DoubleNonPreferenceKey {

    LastBolusAmount("last_bolus_amount", 0.0),
    BolusAmountToBeDelivered("bolus_amount_to_be_delivered", 0.0),
}

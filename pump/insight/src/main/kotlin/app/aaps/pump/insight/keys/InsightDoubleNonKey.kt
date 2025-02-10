package app.aaps.pump.insight.keys

import app.aaps.core.keys.interfaces.DoubleNonPreferenceKey

enum class InsightDoubleNonKey(
    override val key: String,
    override val defaultValue: Double,
    override val exportable: Boolean = true
) : DoubleNonPreferenceKey {

    LastBolusAmount("insight_last_bolus_amount", 0.0),
}


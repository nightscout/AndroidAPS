package app.aaps.plugins.smoothing.keys

import app.aaps.core.keys.interfaces.DoubleNonPreferenceKey

enum class UkfDoubleNonKey(
    override val key: String,
    override val defaultValue: Double,
    override val exportable: Boolean = true
) : DoubleNonPreferenceKey {

    LearnedR("ukf_learned_r", 25.0), //rInit
}
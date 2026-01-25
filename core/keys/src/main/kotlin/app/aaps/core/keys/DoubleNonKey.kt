package app.aaps.core.keys

import app.aaps.core.keys.interfaces.DoubleNonPreferenceKey

enum class DoubleNonKey(
    override val key: String,
    override val defaultValue: Double,
    override val exportable: Boolean = true
) : DoubleNonPreferenceKey {

    NewConcentration("new_concentration", 1.0),
    ApprovedConcentration("approved_concentration", 1.0)
}
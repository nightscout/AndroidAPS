package info.nightscout.pump.combov2.keys

import app.aaps.core.keys.interfaces.IntNonPreferenceKey

enum class ComboIntNonKey(
    override val key: String,
    override val defaultValue: Int,
    override val exportable: Boolean = true
) : IntNonPreferenceKey {

    KeyResponseAddress("combov2-key-response-address-key", 0),
    TbrPercentage("combov2-tbr-percentage", 0),
    TbrDuration("combov2-tbr-duration", 0),
    UtcOffset("combov2-utc-offset", 0),
}
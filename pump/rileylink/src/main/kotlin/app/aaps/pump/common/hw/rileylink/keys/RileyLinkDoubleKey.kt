package app.aaps.pump.common.hw.rileylink.keys

import app.aaps.core.keys.DoubleNonPreferenceKey

enum class RileyLinkDoubleKey(
    override val key: String,
    override val defaultValue: Double
) : DoubleNonPreferenceKey {

    LastGoodDeviceFrequency("AAPS.RileyLink.LastGoodDeviceFrequency", 0.0),
}
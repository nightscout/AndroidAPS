package app.aaps.pump.common.hw.rileylink.keys

import app.aaps.core.keys.interfaces.DoubleNonPreferenceKey

enum class RileyLinkDoubleKey(
    override val key: String,
    override val defaultValue: Double,
    override val exportable: Boolean = true
) : DoubleNonPreferenceKey {

    LastGoodDeviceFrequency("AAPS.RileyLink.LastGoodDeviceFrequency", 0.0),
}
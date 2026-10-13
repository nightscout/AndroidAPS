package info.nightscout.pump.combov2.keys

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

enum class ComboStringNonKey(
    override val key: String,
    override val defaultValue: String,
    override val exportable: Boolean = true
) : StringNonPreferenceKey {

    BtAddress("combov2-bt-address-key", ""),
    Nonce("combov2-nonce-key", ""),
    CpCipher("combov2-cp-cipher-key", ""),
    PcCipher("combov2-pc-cipher-key", ""),
    PumpID("combov2-pump-id-key", ""),
    TbrType("combov2-tbr-type", ""),
}
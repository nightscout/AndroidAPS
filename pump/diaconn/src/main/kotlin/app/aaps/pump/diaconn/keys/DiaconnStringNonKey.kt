package app.aaps.pump.diaconn.keys

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

enum class DiaconnStringNonKey(
    override val key: String,
    override val defaultValue: String,
    override val exportable: Boolean = true
) : StringNonPreferenceKey {

    AppUuid("diaconn_g8_appuid", ""),
    PumpVersion("pump_version", ""),
    Address("diagonn_g8_address", ""),
    Name("Diaconn G8", ""),
}

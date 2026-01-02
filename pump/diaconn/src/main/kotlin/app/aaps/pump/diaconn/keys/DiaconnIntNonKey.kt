package app.aaps.pump.diaconn.keys

import app.aaps.core.keys.interfaces.IntNonPreferenceKey

enum class DiaconnIntNonKey(
    override val key: String,
    override val defaultValue: Int,
    override val exportable: Boolean = true
) : IntNonPreferenceKey {

    ApsIncarnationNo("aps_incarnation_no", 65536),
    PumpSerialNo("pump_serial_no", 0),
}

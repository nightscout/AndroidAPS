package app.aaps.pump.insight.app_layer

enum class Service(val version: Short, var servicePassword: String?, val id: Byte) {
    CONNECTION(0x0000.toShort(), null, 0.toByte()),
    STATUS(0x0100.toShort(), null, 15.toByte()),
    HISTORY(0x0200.toShort(), null, 60.toByte()),
    CONFIGURATION(0x0200.toShort(), "u+5Fhz6Gw4j1Kkas", 85.toByte()),
    PARAMETER(0x0200.toShort(), null, 51.toByte()),
    REMOTE_CONTROL(0x0100.toShort(), "MAbcV2X6PVjxuz+R", 102.toByte());

    companion object {

        fun fromId(id: Byte) = Service.entries.firstOrNull { it.id == id }
    }
}
package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message

enum class MessageType(val value: Byte) {
    CLEAR(0),
    ENCRYPTED(1),
    SESSION_ESTABLISHMENT(2),
    PAIRING(3);

    companion object {

        fun byValue(value: Byte): MessageType =
            MessageType.values().firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unknown MessageType: $value")
    }
}

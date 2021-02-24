package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.command

enum class BleCommandType(val value: Byte) {
    RTS(0x00.toByte()), CTS(0x01.toByte()), NACK(0x02.toByte()), ABORT(0x03.toByte()), SUCCESS(0x04.toByte()), FAIL(0x05.toByte()), HELLO(0x06.toByte());

    companion object {

        fun byValue(value: Byte): BleCommandType {
            for (type in values()) {
                if (type.value == value) {
                    return type
                }
            }
            throw IllegalArgumentException("Unknown BleCommandType: $value")
        }
    }
}
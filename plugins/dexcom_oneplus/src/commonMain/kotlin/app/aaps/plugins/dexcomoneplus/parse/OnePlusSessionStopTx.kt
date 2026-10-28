package app.aaps.plugins.dexcomoneplus.parse

/**
 * Control SessionStop request (opcode 0x28).
 *
 * Layout (7 bytes incl. CRC): opcode | stopDexTime(u32 LE) | crc16
 *
 * Provenance: xDrip `SessionStopTxMessage` at pin
 * `1e86d9a2a52577ed2c30fbf7b69d75fd56e6918f` (GPL-3.0).
 */
object OnePlusSessionStopTx {

    const val OPCODE: Byte = 0x28
    const val PACKET_LENGTH: Int = 7

    /** @param stopTimeDexSeconds transmitter clock seconds at stop */
    fun build(stopTimeDexSeconds: Int): ByteArray {
        val buf = OnePlusByteWriter(PACKET_LENGTH)
        buf.put(OPCODE)
        buf.putInt(stopTimeDexSeconds)
        val packet = buf.array()
        val crc = OnePlusFastCrc16.calculate(packet, PACKET_LENGTH - 2)
        packet[PACKET_LENGTH - 2] = crc[0]
        packet[PACKET_LENGTH - 1] = crc[1]
        return packet
    }
}

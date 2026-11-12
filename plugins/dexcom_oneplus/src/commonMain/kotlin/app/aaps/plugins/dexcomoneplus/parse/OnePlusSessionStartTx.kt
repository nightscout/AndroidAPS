package app.aaps.plugins.dexcomoneplus.parse

/**
 * Control SessionStart request (opcode 0x26) — ONE+ / short-TxId form (no G6 sensor code).
 *
 * Layout (11 bytes incl. CRC): opcode | dexTime(u32 LE) | startUnixSec(u32 LE) | crc16
 *
 * Provenance: xDrip `SessionStartTxMessage` at pin
 * `1e86d9a2a52577ed2c30fbf7b69d75fd56e6918f` (GPL-3.0).
 */
object OnePlusSessionStartTx {

    const val OPCODE: Byte = 0x26
    const val PACKET_LENGTH: Int = 11

    /**
     * @param dexTimeSeconds transmitter time (seconds); 0 if unknown before TransmitterTime sync
     * @param startTimeEpochMs wall-clock session start (ms); encoded as seconds in the packet
     */
    fun build(dexTimeSeconds: Int, startTimeEpochMs: Long): ByteArray {
        val buf = OnePlusByteWriter(PACKET_LENGTH)
        buf.put(OPCODE)
        buf.putInt(dexTimeSeconds)
        buf.putInt((startTimeEpochMs / 1000L).toInt())
        val packet = buf.array()
        val crc = OnePlusFastCrc16.calculate(packet, PACKET_LENGTH - 2)
        packet[PACKET_LENGTH - 2] = crc[0]
        packet[PACKET_LENGTH - 1] = crc[1]
        return packet
    }
}

package app.aaps.plugins.dexcomoneplus.parse

/**
 * Control TransmitterTime request (opcode 0x24).
 *
 * Layout (3 bytes): opcode | crc16 — same pattern as [OnePlusEGlucoseTx.requestWithCrc].
 *
 * Provenance: xDrip `TransmitterTimeTxMessage` at pin
 * `1e86d9a2a52577ed2c30fbf7b69d75fd56e6918f` (GPL-3.0).
 */
object OnePlusTransmitterTimeTx {

    const val OPCODE: Byte = 0x24
    const val PACKET_LENGTH: Int = 3

    fun request(): ByteArray {
        val body = byteArrayOf(OPCODE, 0, 0)
        val crc = OnePlusFastCrc16.calculate(body, 1)
        body[1] = crc[0]
        body[2] = crc[1]
        return body
    }
}

package app.aaps.plugins.dexcomoneplus.parse

/**
 * Control-channel EGV request (opcode 0x4e).
 *
 * Provenance: xDrip `EGlucoseTxMessage` / `BaseMessage.init` at pin
 * `1e86d9a2a52577ed2c30fbf7b69d75fd56e6918f` (GPL-3.0).
 */
object OnePlusEGlucoseTx {

    const val OPCODE: Byte = 0x4e

    /** Short TxId / G7-ONE+ style request (length 1, no CRC). */
    fun requestShort(): ByteArray = byteArrayOf(OPCODE)

    /** Longer request with CRC (length 3). */
    fun requestWithCrc(): ByteArray {
        val body = byteArrayOf(OPCODE, 0, 0)
        val crc = OnePlusFastCrc16.calculate(body, 1)
        body[1] = crc[0]
        body[2] = crc[1]
        return body
    }
}

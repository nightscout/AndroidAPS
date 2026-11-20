package app.aaps.plugins.dexcomoneplus.parse

/**
 * Control ACK for BackFillTxMessage2 (opcode 0x59).
 *
 * Provenance: xDrip `BackFillControlRxMessage` / G7 `BackfillControlRx` at pin
 * `1e86d9a2a52577ed2c30fbf7b69d75fd56e6918f` (GPL-3.0).
 */
object OnePlusBackFillControlRx {

    const val OPCODE: Byte = 0x59

    fun isAck(packet: ByteArray): Boolean =
        packet.isNotEmpty() && packet[0] == OPCODE
}

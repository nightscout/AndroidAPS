package app.aaps.pump.carelevo.ble.commands

import app.aaps.pump.carelevo.ble.BleCommand

/**
 * `CMD_APP_AUTH_IND` (0x4B) → `CMD_APP_AUTH_ACK` (0xBB). The activation app-auth handshake: the app sends its
 * auth [key] and the patch acknowledges with a result code.
 *
 * Request wire format (2 bytes): `[0] 0x4B, [1] key` — [key] is written as a **raw byte**, with no
 * scaling/inversion applied.
 *
 * **Opcode quirk:** the request opcode is 0x4B, but the reply comes back on `CMD_APP_AUTH_ACK` = **0xBB**
 * (not 0x4B, and not the neighbouring `CMD_APP_AUTH_KEY_ACK` = 0xBA), which is why [expectedResponseOpcode]
 * is 0xBB rather than echoing the request opcode.
 *
 * Response (0xBB): `[0] 0xBB, [1] result` → [SimpleResultResponse] (0 = SUCCESS). Only the wire [result]
 * byte is carried.
 */
class AppAuthCommand(
    private val key: Int
) : BleCommand<SimpleResultResponse> {

    init {
        require(key in KEY_RANGE) { "key $key out of range $KEY_RANGE" }
    }

    override val requestOpcode: Byte = REQUEST_OPCODE
    override val expectedResponseOpcode: Byte = RESPONSE_OPCODE

    override fun encode(): ByteArray = byteArrayOf(requestOpcode, key.toByte())

    override fun decode(responsePayload: ByteArray): SimpleResultResponse {
        requireResponseFrame(responsePayload, RESPONSE_OPCODE, MIN_RESPONSE_LENGTH)
        return SimpleResultResponse(responsePayload.u(1))
    }

    companion object {

        const val REQUEST_OPCODE: Byte = 0x4B
        const val RESPONSE_OPCODE: Byte = 0xBB.toByte()

        // key is written as a raw byte; accept the full unsigned-byte range.
        private val KEY_RANGE = 0..255
        private const val MIN_RESPONSE_LENGTH = 2
    }
}

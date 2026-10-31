package app.aaps.pump.carelevo.ble.commands

import app.aaps.pump.carelevo.ble.BleCommand

/**
 * `CMD_BUZZ_CHANGE_REQ` (0x18) → `CMD_BUZZ_CHANGE_RES` (0x78). Turns the patch buzzer reminder on/off.
 *
 * Request wire format (2 bytes): `[0] 0x18, [1] flag`. **Inverted encoding:** `use=true → 0x01` and
 * `use=false → 0x00` (the opposite of the naive mapping).
 *
 * Response (0x78): `[0] 0x78, [1] resultCode` → [SimpleResultResponse].
 */
class BuzzModeCommand(
    private val use: Boolean
) : BleCommand<SimpleResultResponse> {

    override val requestOpcode: Byte = REQUEST_OPCODE
    override val expectedResponseOpcode: Byte = RESPONSE_OPCODE

    override fun encode(): ByteArray = byteArrayOf(requestOpcode, if (use) FLAG_ON else FLAG_OFF)

    override fun decode(responsePayload: ByteArray): SimpleResultResponse {
        requireResponseFrame(responsePayload, RESPONSE_OPCODE, MIN_RESPONSE_LENGTH)
        return SimpleResultResponse(responsePayload.u(1))
    }

    companion object {

        const val REQUEST_OPCODE: Byte = 0x18
        const val RESPONSE_OPCODE: Byte = 0x78
        private const val FLAG_ON: Byte = 0x01 // use=true  → 0x01 (inverted encoding)
        private const val FLAG_OFF: Byte = 0x00 // use=false → 0x00
        private const val MIN_RESPONSE_LENGTH = 2
    }
}

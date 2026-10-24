package app.aaps.pump.carelevo.ble.commands

import app.aaps.pump.carelevo.ble.BleCommand

/**
 * `CMD_PATCH_DISCARD_REQ` (0x36) → `CMD_PATCH_DISCARD_RES` (0x96). Discards (deactivates) the patch.
 * Medium-criticality write with no arguments — sends only the opcode byte.
 *
 * Request wire format (1 byte): `[0] 0x36`.
 * Response (0x96): `[0] 0x96, [1] resultCode` → [SimpleResultResponse] (0 = SUCCESS). Any timestamp/cmd
 * bookkeeping is not on the wire; this decoder keeps only the result byte.
 */
class PatchDiscardCommand : BleCommand<SimpleResultResponse> {

    override val requestOpcode: Byte = REQUEST_OPCODE
    override val expectedResponseOpcode: Byte = RESPONSE_OPCODE

    override fun encode(): ByteArray = byteArrayOf(requestOpcode)

    override fun decode(responsePayload: ByteArray): SimpleResultResponse {
        requireResponseFrame(responsePayload, RESPONSE_OPCODE, MIN_RESPONSE_LENGTH)
        return SimpleResultResponse(responsePayload.u(1))
    }

    companion object {

        const val REQUEST_OPCODE: Byte = 0x36
        const val RESPONSE_OPCODE: Byte = 0x96.toByte()
        private const val MIN_RESPONSE_LENGTH = 2
    }
}

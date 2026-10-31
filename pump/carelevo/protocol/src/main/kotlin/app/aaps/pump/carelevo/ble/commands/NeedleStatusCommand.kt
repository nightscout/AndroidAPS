package app.aaps.pump.carelevo.ble.commands

import app.aaps.pump.carelevo.ble.BleCommand

/**
 * `CMD_NEEDLE_STATUS_REQ` (0x1A) → `CMD_NEEDLE_INSERT_RPT` (0x79). Requests the cannula-insertion status.
 *
 * **Asymmetric opcodes** (0x1A → 0x79, not the usual `+0x60`) and a **long physical wait**: the pump
 * replies only once the needle insertion has finished, with no fixed timeout on the wire. The caller
 * must therefore wrap `request(...)` in a generous `withTimeout(...)`.
 *
 * Request wire format (2 bytes): `[0] 0x1A, [1] 0x00`.
 * Response (0x79): `[0] 0x79, [1] resultCode` → [SimpleResultResponse].
 */
class NeedleStatusCommand : BleCommand<SimpleResultResponse> {

    override val requestOpcode: Byte = REQUEST_OPCODE
    override val expectedResponseOpcode: Byte = RESPONSE_OPCODE

    override fun encode(): ByteArray = byteArrayOf(requestOpcode, REQUEST_FLAG)

    override fun decode(responsePayload: ByteArray): SimpleResultResponse {
        requireResponseFrame(responsePayload, RESPONSE_OPCODE, MIN_RESPONSE_LENGTH)
        return SimpleResultResponse(responsePayload.u(1))
    }

    companion object {

        const val REQUEST_OPCODE: Byte = 0x1A
        const val RESPONSE_OPCODE: Byte = 0x79
        private const val REQUEST_FLAG: Byte = 0x00
        private const val MIN_RESPONSE_LENGTH = 2
    }
}

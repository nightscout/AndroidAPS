package app.aaps.pump.carelevo.ble.commands

import app.aaps.pump.carelevo.ble.BleCommand

/**
 * `CMD_ADD_PRIMING_REQ` (0x1D) → `CMD_ADD_PRIMING_RES` (0x7D). Requests an additional priming pulse on the
 * patch (medium criticality) — used to top up priming when the initial prime was insufficient.
 *
 * Request wire format (1 byte): `[0] 0x1D` — opcode alone, no arguments to encode or range-check.
 *
 * Response (0x7D): `[0] 0x7D, [1] resultCode` → [SimpleResultResponse] (`resultCode` 0 = SUCCESS).
 *
 * Note: the pump later emits an unsolicited `0x98` PulseFinish notification when priming completes; that is a
 * separate message and is **not** part of this request/response pair.
 */
class AdditionalPrimingCommand : BleCommand<SimpleResultResponse> {

    override val requestOpcode: Byte = REQUEST_OPCODE
    override val expectedResponseOpcode: Byte = RESPONSE_OPCODE

    override fun encode(): ByteArray = byteArrayOf(requestOpcode)

    override fun decode(responsePayload: ByteArray): SimpleResultResponse {
        requireResponseFrame(responsePayload, RESPONSE_OPCODE, MIN_RESPONSE_LENGTH)
        return SimpleResultResponse(responsePayload.u(1))
    }

    companion object {

        const val REQUEST_OPCODE: Byte = 0x1D
        const val RESPONSE_OPCODE: Byte = 0x7D
        private const val MIN_RESPONSE_LENGTH = 2
    }
}

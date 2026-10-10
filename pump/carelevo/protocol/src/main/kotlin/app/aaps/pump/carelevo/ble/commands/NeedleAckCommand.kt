package app.aaps.pump.carelevo.ble.commands

import app.aaps.pump.carelevo.ble.BleCommand

/**
 * `CMD_NEEDLE_INSERT_ACK` (0x19) → `CMD_CANNULA_INSERTION_RPT_ACK_RES` (0x7A). Confirms the cannula
 * insertion result back to the patch.
 *
 * Request wire format (2 bytes): `[0] 0x19, [1] flag`, where `isSuccess=true → 0x00`,
 * `isSuccess=false → 0x01`.
 *
 * **Behavioral note:** this command awaits the 0x7A response. Confirm on hardware that the pump actually
 * sends 0x7A before relying on the awaited result. Response (0x7A): `[0] 0x7A, [1] resultCode` →
 * [SimpleResultResponse].
 */
class NeedleAckCommand(
    private val isSuccess: Boolean
) : BleCommand<SimpleResultResponse> {

    override val requestOpcode: Byte = REQUEST_OPCODE
    override val expectedResponseOpcode: Byte = RESPONSE_OPCODE

    override fun encode(): ByteArray = byteArrayOf(requestOpcode, if (isSuccess) FLAG_SUCCESS else FLAG_FAILURE)

    override fun decode(responsePayload: ByteArray): SimpleResultResponse {
        requireResponseFrame(responsePayload, RESPONSE_OPCODE, MIN_RESPONSE_LENGTH)
        return SimpleResultResponse(responsePayload.u(1))
    }

    companion object {

        const val REQUEST_OPCODE: Byte = 0x19
        const val RESPONSE_OPCODE: Byte = 0x7A
        private const val FLAG_SUCCESS: Byte = 0x00
        private const val FLAG_FAILURE: Byte = 0x01
        private const val MIN_RESPONSE_LENGTH = 2
    }
}

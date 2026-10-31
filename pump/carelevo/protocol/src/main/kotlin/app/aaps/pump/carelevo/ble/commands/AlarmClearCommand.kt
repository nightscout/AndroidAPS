package app.aaps.pump.carelevo.ble.commands

import app.aaps.pump.carelevo.ble.BleCommand
import app.aaps.pump.carelevo.ble.BleResponse

/**
 * `CMD_ALARM_CLEAR_REQ` (0x47) → `CMD_ALARM_CLEAR_RES` (0xA7). Clears a raised patch alarm.
 * Criticality: medium.
 *
 * Request wire format (3 bytes): `[0] 0x47, [1] alarmType, [2] cause`.
 * - `alarmType` — raw byte, **no range check**, so values > 0x7F pass straight through as the
 *   two's-complement byte.
 * - `cause` — range-checked `0..100`.
 *
 * Response (0xA7): `[0] 0xA7, [1] subId, [2] cause, [3] resultCode`. `resultCode` 0 = SUCCESS;
 * consumers map it.
 */
class AlarmClearCommand(
    private val alarmType: Int,
    private val cause: Int
) : BleCommand<AlarmClearResponse> {

    init {
        require(cause in CAUSE_RANGE) { "cause $cause out of range $CAUSE_RANGE" }
    }

    override val requestOpcode: Byte = REQUEST_OPCODE
    override val expectedResponseOpcode: Byte = RESPONSE_OPCODE

    override fun encode(): ByteArray = byteArrayOf(requestOpcode, alarmType.toByte(), cause.toByte())

    override fun decode(responsePayload: ByteArray): AlarmClearResponse {
        requireResponseFrame(responsePayload, RESPONSE_OPCODE, MIN_RESPONSE_LENGTH)
        return AlarmClearResponse(
            subId = responsePayload.u(1),
            cause = responsePayload.u(2),
            resultCode = responsePayload.u(3)
        )
    }

    companion object {

        const val REQUEST_OPCODE: Byte = 0x47
        const val RESPONSE_OPCODE: Byte = 0xA7.toByte()
        private val CAUSE_RANGE = 0..100
        private const val MIN_RESPONSE_LENGTH = 4
    }
}

/** Decoded response from [AlarmClearCommand]: echoed [subId] + [cause], plus pump [resultCode] (0 = SUCCESS). */
data class AlarmClearResponse(
    val subId: Int,
    val cause: Int,
    val resultCode: Int
) : BleResponse

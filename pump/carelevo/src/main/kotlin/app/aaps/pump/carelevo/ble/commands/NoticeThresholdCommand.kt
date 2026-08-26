package app.aaps.pump.carelevo.ble.commands

import app.aaps.pump.carelevo.ble.BleCommand
import app.aaps.pump.carelevo.ble.BleResponse

/**
 * `CMD_NOTICE_THRESHOLD_REQ` (0x15) → `CMD_NOTICE_THRESHOLD_RES` (0x75). Sets a reminder threshold —
 * one command class for both consumers, parameterized by [thresholdType]:
 * - [TYPE_LOW_INSULIN] (0): low-insulin reminder amount (value range 20..50 U)
 * - [TYPE_EXPIRY] (1): patch-expiry reminder hours (value range 24..167)
 *
 * Request wire format (3 bytes): `[0] 0x15, [1] thresholdType, [2] value`.
 *
 * Response (0x75): `[0] 0x75, [1] thresholdType` — no result code is carried on the wire, so arrival of
 * the frame is the success signal; [resultCode] is always reported as 0.
 */
class NoticeThresholdCommand(
    private val thresholdType: Int,
    private val value: Int
) : BleCommand<NoticeThresholdResponse> {

    init {
        require(thresholdType == TYPE_LOW_INSULIN || thresholdType == TYPE_EXPIRY) {
            "thresholdType must be $TYPE_LOW_INSULIN or $TYPE_EXPIRY, got $thresholdType"
        }
        val range = if (thresholdType == TYPE_EXPIRY) EXPIRY_RANGE else LOW_INSULIN_RANGE
        require(value in range) { "value $value out of range $range for type $thresholdType" }
    }

    override val requestOpcode: Byte = REQUEST_OPCODE
    override val expectedResponseOpcode: Byte = RESPONSE_OPCODE

    override fun encode(): ByteArray = byteArrayOf(requestOpcode, thresholdType.toByte(), value.toByte())

    override fun decode(responsePayload: ByteArray): NoticeThresholdResponse {
        requireResponseFrame(responsePayload, RESPONSE_OPCODE, MIN_RESPONSE_LENGTH)
        return NoticeThresholdResponse(thresholdType = responsePayload.u(1), resultCode = FABRICATED_RESULT)
    }

    companion object {

        const val REQUEST_OPCODE: Byte = 0x15
        const val RESPONSE_OPCODE: Byte = 0x75
        const val TYPE_LOW_INSULIN = 0
        const val TYPE_EXPIRY = 1

        private val LOW_INSULIN_RANGE = 20..50
        private val EXPIRY_RANGE = 24..167
        private const val MIN_RESPONSE_LENGTH = 2
        private const val FABRICATED_RESULT = 0 // no result code on the wire; frame arrival = success
    }
}

/** Decoded response from [NoticeThresholdCommand]: the echoed [thresholdType]; [resultCode] is always 0 (see command). */
data class NoticeThresholdResponse(
    val thresholdType: Int,
    val resultCode: Int
) : BleResponse

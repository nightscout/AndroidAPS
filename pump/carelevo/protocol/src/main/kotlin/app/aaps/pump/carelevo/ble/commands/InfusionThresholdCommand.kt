package app.aaps.pump.carelevo.ble.commands

import app.aaps.pump.carelevo.ble.BleCommand
import app.aaps.pump.carelevo.ble.BleResponse

/**
 * `CMD_INFUSION_THRESHOLD_REQ` (0x17) → `CMD_INFUSION_THRESHOLD_RES` (0x77). Sets an infusion limit —
 * one command class for both consumers, parameterized by [isMaxVolume]:
 * - `false` = max basal **speed** (U/h), range 0.05..15.0 — flag byte 0x00
 * - `true`  = max bolus **volume** (U),  range 0.05..25.0 — flag byte 0x01
 *
 * The flag byte is inverted: `flag = if (isMaxVolume) 0x01 else 0x00`.
 *
 * Request wire format (4 bytes): `[0] 0x17, [1] flag, [2..3] value = [intPart, centiPart]`.
 * Response (0x77): `[0] 0x77, [1] type, [2] resultCode`.
 */
class InfusionThresholdCommand(
    private val isMaxVolume: Boolean,
    private val value: Double
) : BleCommand<InfusionThresholdResponse> {

    init {
        val range = if (isMaxVolume) MAX_VOLUME_RANGE else MAX_SPEED_RANGE
        require(value in range) { "value $value out of range $range (isMaxVolume=$isMaxVolume)" }
    }

    override val requestOpcode: Byte = REQUEST_OPCODE
    override val expectedResponseOpcode: Byte = RESPONSE_OPCODE

    override fun encode(): ByteArray =
        byteArrayOf(requestOpcode, if (isMaxVolume) FLAG_MAX_VOLUME else FLAG_MAX_SPEED) + encodeUnitCenti(value)

    override fun decode(responsePayload: ByteArray): InfusionThresholdResponse {
        requireResponseFrame(responsePayload, RESPONSE_OPCODE, MIN_RESPONSE_LENGTH)
        return InfusionThresholdResponse(type = responsePayload.u(1), resultCode = responsePayload.u(2))
    }

    companion object {

        const val REQUEST_OPCODE: Byte = 0x17
        const val RESPONSE_OPCODE: Byte = 0x77
        private const val FLAG_MAX_SPEED: Byte = 0x00
        private const val FLAG_MAX_VOLUME: Byte = 0x01
        private val MAX_SPEED_RANGE = 0.05..15.0
        private val MAX_VOLUME_RANGE = 0.05..25.0
        private const val MIN_RESPONSE_LENGTH = 3
    }
}

/** Decoded response from [InfusionThresholdCommand]: echoed [type] flag + pump [resultCode] (0 = SUCCESS). */
data class InfusionThresholdResponse(
    val type: Int,
    val resultCode: Int
) : BleResponse

package app.aaps.pump.carelevo.ble.commands

import app.aaps.pump.carelevo.ble.BleCommand
import app.aaps.pump.carelevo.ble.BleResponse

/**
 * `CMD_EXTENDED_BOLUS_REQ` (0x25) → `CMD_EXTENDED_BOLUS_RES` (0x85). **Safety-critical delivery** —
 * starts an extended (dual-wave) bolus: an immediate dose up-front plus an extended portion delivered
 * at [extendedSpeed] over [hour]h[min]m.
 *
 * Request wire format (7 bytes): `[0] 0x25, [1..2] immediateDose = [intPart, centiPart],
 * [3..4] extendedSpeed = [intPart, centiPart], [5] hour, [6] min`.
 *
 * **No dose range validation:** [immediateDose]/[extendedSpeed] are NOT range-checked; only [hour]
 * (0..24) and [min] (0..59) are validated.
 *
 * Response (0x85): `[0] 0x85, [1] resultCode, [2..3] expectedTime = [2]*60 + [3]` (seconds) →
 * [ExtendBolusResponse].
 *
 * (The async 0x9C extended-bolus delay report is a separate unsolicited message — not this response.)
 */
class ExtendBolusCommand(
    private val immediateDose: Double,
    private val extendedSpeed: Double,
    private val hour: Int,
    private val min: Int
) : BleCommand<ExtendBolusResponse> {

    init {
        require(hour in HOUR_RANGE) { "hour $hour out of range $HOUR_RANGE" }
        require(min in MIN_RANGE) { "min $min out of range $MIN_RANGE" }
    }

    override val requestOpcode: Byte = REQUEST_OPCODE
    override val expectedResponseOpcode: Byte = RESPONSE_OPCODE

    override fun encode(): ByteArray =
        byteArrayOf(requestOpcode) +
            encodeUnitCenti(immediateDose) +
            encodeUnitCenti(extendedSpeed) +
            byteArrayOf(hour.toByte(), min.toByte())

    override fun decode(responsePayload: ByteArray): ExtendBolusResponse {
        requireResponseFrame(responsePayload, RESPONSE_OPCODE, MIN_RESPONSE_LENGTH)
        return ExtendBolusResponse(
            resultCode = responsePayload.u(1),
            expectedTimeSeconds = responsePayload.u(2) * SECONDS_PER_MINUTE + responsePayload.u(3)
        )
    }

    companion object {

        const val REQUEST_OPCODE: Byte = 0x25
        const val RESPONSE_OPCODE: Byte = 0x85.toByte()
        private val HOUR_RANGE = 0..24
        private val MIN_RANGE = 0..59
        private const val MIN_RESPONSE_LENGTH = 4
        private const val SECONDS_PER_MINUTE = 60
    }
}

/**
 * Decoded response from [ExtendBolusCommand]: pump [resultCode] (0 = SUCCESS) and [expectedTimeSeconds],
 * the pump-reported expected total delivery time in seconds (`[2]*60 + [3]`).
 */
data class ExtendBolusResponse(
    val resultCode: Int,
    val expectedTimeSeconds: Int
) : BleResponse

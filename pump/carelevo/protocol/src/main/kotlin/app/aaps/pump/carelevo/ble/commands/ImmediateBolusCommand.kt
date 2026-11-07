package app.aaps.pump.carelevo.ble.commands

import app.aaps.pump.carelevo.ble.BleCommand
import app.aaps.pump.carelevo.ble.BleResponse
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

/**
 * `CMD_IMMED_BOLUS_REQ` (0x24) → `CMD_IMMED_BOLUS_RES` (0x84).
 *
 * **Safety-critical.** Delivers an immediate bolus. The [actionId] is echoed by the
 * pump at byte 1 of the response and is used by [app.aaps.pump.carelevo.ble.BleClientImpl]
 * as a second-level correlation guard on top of the opcode match — a response with
 * the wrong actionId is rejected rather than accepted, which protects against a stale
 * or mis-routed BOLUS_RES from a previous request being interpreted as the answer to
 * this one.
 *
 * Request wire format (4 bytes):
 * ```
 * [0] 0x24                 opcode
 * [1] actionId (1..255)    caller-chosen, pump echoes it back in the response
 * [2] volumeWholeUnits     integer part of volume (U, truncated)
 * [3] volumeCentiUnits     fractional part × 100, rounded HALF_UP to 2 dp
 * ```
 *
 * Example: `ImmediateBolusCommand(actionId = 42, volume = 2.5).encode()` →
 * `[0x24, 0x2A, 0x02, 0x32]` (42 = 0x2A, 50 centi-units = 0x32).
 *
 * Response wire format (≥ 8 bytes):
 * ```
 * [0]    0x84              opcode
 * [1]    actionId          echoed from the request — used for correlation
 * [2]    resultCode        pump-specific result code; 0 = SUCCESS
 * [3]    expectedMinutes   minutes portion of expected completion time
 * [4]    expectedSeconds   seconds portion (combined seconds = [3]*60 + [4])
 * [5..7] remainsWholeU     remaining reservoir units = [5]*100.0 + [6] + [7]/100.0
 * ```
 */
class ImmediateBolusCommand(
    /** Caller-chosen action identifier in 1..255. Pump echoes this back at byte 1 of the response. */
    private val actionId: Int,
    /** Bolus amount in units. Must be > 0. Encoded with 2-decimal precision. */
    private val volume: Double
) : BleCommand<ImmediateBolusResponse> {

    init {
        require(actionId in ACTION_ID_MIN..ACTION_ID_MAX) {
            "actionId must be in $ACTION_ID_MIN..$ACTION_ID_MAX, got $actionId"
        }
        require(volume > 0.0) { "volume must be > 0, got $volume" }
    }

    override val requestOpcode: Byte = REQUEST_OPCODE
    override val expectedResponseOpcode: Byte = RESPONSE_OPCODE
    override val correlationByte: Byte = actionId.toByte()

    override fun encode(): ByteArray {
        // valueOf (canonical decimal), not BigDecimal(double) — see encodeUnitCenti in CommandCodec.
        val rounded = BigDecimal.valueOf(volume).setScale(2, RoundingMode.HALF_UP).toDouble()
        val wholeUnits = rounded.toInt()
        val centiUnits = ((rounded - wholeUnits) * 100).roundToInt()
        return byteArrayOf(
            requestOpcode,
            actionId.toByte(),
            wholeUnits.toByte(),
            centiUnits.toByte()
        )
    }

    override fun decode(responsePayload: ByteArray): ImmediateBolusResponse {
        require(responsePayload.isNotEmpty() && responsePayload[0] == expectedResponseOpcode) {
            "expected opcode 0x${"%02X".format(expectedResponseOpcode)}, got " +
                (responsePayload.getOrNull(0)?.let { "0x${"%02X".format(it)}" } ?: "empty")
        }
        require(responsePayload.size >= MIN_RESPONSE_LENGTH) {
            "response too short: ${responsePayload.size} bytes, need at least $MIN_RESPONSE_LENGTH"
        }

        val actionIdEcho = responsePayload[1].toUByte().toInt()
        val resultCode = responsePayload[2].toUByte().toInt()
        val expectedSeconds =
            responsePayload[3].toUByte().toInt() * SECONDS_PER_MINUTE + responsePayload[4].toUByte().toInt()
        val remainingUnits = responsePayload[5].toUByte().toInt() * REMAINS_HUNDREDS_SCALE +
            responsePayload[6].toUByte().toInt() +
            responsePayload[7].toUByte().toInt() / REMAINS_FRAC_SCALE

        return ImmediateBolusResponse(
            actionId = actionIdEcho,
            resultCode = resultCode,
            expectedCompletionSeconds = expectedSeconds,
            remainingReservoirUnits = remainingUnits
        )
    }

    companion object {

        const val REQUEST_OPCODE: Byte = 0x24
        const val RESPONSE_OPCODE: Byte = 0x84.toByte()

        const val ACTION_ID_MIN = 1
        const val ACTION_ID_MAX = 255

        private const val MIN_RESPONSE_LENGTH = 8
        private const val SECONDS_PER_MINUTE = 60
        private const val REMAINS_HUNDREDS_SCALE = 100.0
        private const val REMAINS_FRAC_SCALE = 100.0
    }
}

/**
 * Decoded response from [ImmediateBolusCommand].
 *
 * [resultCode] is the raw pump-protocol result byte; consumers map it to a domain
 * enum (`0 = SUCCESS`).
 */
data class ImmediateBolusResponse(
    val actionId: Int,
    val resultCode: Int,
    val expectedCompletionSeconds: Int,
    val remainingReservoirUnits: Double
) : BleResponse

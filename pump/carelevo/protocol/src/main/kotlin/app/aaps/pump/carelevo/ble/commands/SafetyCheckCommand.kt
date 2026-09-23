package app.aaps.pump.carelevo.ble.commands

import app.aaps.pump.carelevo.ble.BleResponse
import app.aaps.pump.carelevo.ble.BleStreamCommand

/**
 * `CMD_SAFETY_CHECK_REQ` (0x12) → `CMD_SAFETY_CHECK_RES` (0x72). The activation safety check: the pump
 * primes/checks and streams progress reports (`REP_REQUEST`/`REP_REQUEST1`) then a terminal SUCCESS/error.
 * This is the CareLevo [BleStreamCommand] — collected until [isTerminal].
 *
 * Request wire format (1 byte): `[0] 0x12`.
 *
 * Response wire format (0x72, ≥ 4 bytes):
 * ```
 * [0]      0x72            opcode
 * [1]      resultCode      (SafetyCheckResult: 0 SUCCESS, 4/18 progress, 1/2/3/11/12 errors)
 * [2..3]   insulinVolume = [2]*100 + [3]   (U; ×100 hundreds-byte quirk, no decimal byte)
 * [4..5]   durationSeconds = [4]*60 + [5]  — only if size > 4; else the 210 s fallback
 * ```
 * [isTerminal] is `true` for any non-progress result (SUCCESS **or** an error code), so the stream
 * completes with the terminal frame rather than timing out; the consumer maps [SafetyCheckResponse.resultCode]
 * to SUCCESS vs error.
 */
class SafetyCheckCommand : BleStreamCommand<SafetyCheckResponse> {

    override val requestOpcode: Byte = REQUEST_OPCODE
    override val expectedResponseOpcode: Byte = RESPONSE_OPCODE

    override fun encode(): ByteArray = byteArrayOf(requestOpcode)

    override fun decode(responsePayload: ByteArray): SafetyCheckResponse {
        requireResponseFrame(responsePayload, RESPONSE_OPCODE, MIN_RESPONSE_LENGTH)
        // Duration bytes are read only when size > 4; a short/progress frame falls back to 210 s.
        // Real frames are size 4 or ≥ 6.
        val durationSeconds =
            if (responsePayload.size > 4) responsePayload.u(4) * SECONDS_PER_MINUTE + responsePayload.u(5)
            else DEFAULT_DURATION_SECONDS
        return SafetyCheckResponse(
            resultCode = responsePayload.u(1),
            insulinVolume = responsePayload.u(2) * HUNDRED + responsePayload.u(3),
            durationSeconds = durationSeconds
        )
    }

    override fun isTerminal(response: SafetyCheckResponse): Boolean =
        response.resultCode != REP_REQUEST && response.resultCode != REP_REQUEST1

    companion object {

        const val REQUEST_OPCODE: Byte = 0x12
        const val RESPONSE_OPCODE: Byte = 0x72

        // SafetyCheckResult codes (see CarelevoBtEnums).
        const val RESULT_SUCCESS = 0
        const val REP_REQUEST = 4 // progress
        const val REP_REQUEST1 = 18 // progress

        private const val MIN_RESPONSE_LENGTH = 4
        private const val DEFAULT_DURATION_SECONDS = 210
        private const val SECONDS_PER_MINUTE = 60
        private const val HUNDRED = 100
    }
}

/**
 * A single streamed safety-check frame: [resultCode] (progress `REP_REQUEST`/`REP_REQUEST1`, terminal
 * `RESULT_SUCCESS`, or an error code), remaining [insulinVolume] (U), and the [durationSeconds] the pump
 * expects the check to take (drives the UI countdown).
 */
data class SafetyCheckResponse(
    val resultCode: Int,
    val insulinVolume: Int,
    val durationSeconds: Int
) : BleResponse

package app.aaps.pump.carelevo.ble.commands

import app.aaps.pump.carelevo.ble.BleCommand

/**
 * `CMD_PUMP_STOP_REQ` (0x26) → `CMD_PUMP_STOP_RES` (0x86). **SAFETY-CRITICAL** — suspends (stops) insulin
 * delivery for [durationMinutes], answered with a result-only acknowledgement.
 *
 * The later `CMD_PUMP_STOP_RPT` (0x8A) the pump pushes when the stop period ends is an async/unsolicited
 * report — **not** this command's response; it is handled via [app.aaps.pump.carelevo.ble.BleClient.unsolicitedEvents].
 *
 * Request wire format (4 bytes), splitting the duration into hours+minutes:
 * ```
 * [0] 0x26                         opcode
 * [1] durationMinutes / 60         hours   — range 0..5
 * [2] durationMinutes % 60         minutes — range 0..60
 * [3] subId                        source  — range 0..1
 * ```
 * Each field is one raw byte (`value.toByte()`), range-checked as above. Because `durationMinutes % 60`
 * is always 0..59 for a non-negative duration, the effective span is `0..359` minutes (5 h 59 min).
 *
 * Response (0x86): `[0] 0x86, [1] resultCode` → [SimpleResultResponse]. Any timestamp/cmd bookkeeping is
 * not on the wire, so this pure wire decoder drops it (the caller owns timestamping).
 */
class PumpStopCommand(
    private val durationMinutes: Int,
    private val subId: Int
) : BleCommand<SimpleResultResponse> {

    private val hours = durationMinutes / MINUTES_PER_HOUR
    private val minutes = durationMinutes % MINUTES_PER_HOUR

    init {
        require(hours in HOURS_RANGE) { "durationMinutes $durationMinutes → hours $hours out of range $HOURS_RANGE" }
        require(minutes in MINUTES_RANGE) { "durationMinutes $durationMinutes → minutes $minutes out of range $MINUTES_RANGE" }
        require(subId in SUB_ID_RANGE) { "subId $subId out of range $SUB_ID_RANGE" }
    }

    override val requestOpcode: Byte = REQUEST_OPCODE
    override val expectedResponseOpcode: Byte = RESPONSE_OPCODE

    override fun encode(): ByteArray =
        byteArrayOf(requestOpcode, hours.toByte(), minutes.toByte(), subId.toByte())

    override fun decode(responsePayload: ByteArray): SimpleResultResponse {
        requireResponseFrame(responsePayload, RESPONSE_OPCODE, MIN_RESPONSE_LENGTH)
        return SimpleResultResponse(responsePayload.u(1))
    }

    companion object {

        const val REQUEST_OPCODE: Byte = 0x26
        const val RESPONSE_OPCODE: Byte = 0x86.toByte()

        private const val MINUTES_PER_HOUR = 60
        private val HOURS_RANGE = 0..5
        private val MINUTES_RANGE = 0..60
        private val SUB_ID_RANGE = 0..1
        private const val MIN_RESPONSE_LENGTH = 2
    }
}

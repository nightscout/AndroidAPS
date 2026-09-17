package app.aaps.pump.carelevo.ble.commands

import app.aaps.pump.carelevo.ble.BleCommand

/**
 * `CMD_TEMP_BASAL_REQ` (0x23) → `CMD_TEMP_BASAL_RES` (0x83). Starts a temp basal — **safety-critical
 * delivery**. One command class for both modes, parameterized by [Mode]:
 * - [Mode.BY_UNIT]    — an absolute rate in U/h.
 * - [Mode.BY_PERCENT] — a percentage of the scheduled basal.
 *
 * **The ByUnit↔ByPercent ASYMMETRY must be exact** (a wrong byte here is a patient-safety bug):
 * ```
 * BY_UNIT    (6 bytes): [0] 0x23, [1..2] unit  = [intPart, centiPart], [3] hour, [4] min, [5] 0x00
 * BY_PERCENT (5 bytes): [0] 0x23, [1..2] value = [intPart, centiPart], [3] hour, [4] min
 * ```
 * Wire-format quirks:
 * - **ByUnit trailing 0x00:** BY_UNIT appends a sixth `0x00` byte; BY_PERCENT appends **nothing**
 *   (the asymmetry).
 * - **ByPercent `/100.0`:** [byPercent] encodes `infusionPercent / 100.0` (e.g. 150 % → `1.50` →
 *   `[1, 50]`) before [encodeUnitCenti].
 * - **ByUnit has NO value-range check:** any `infusionUnit` is accepted on the wire. ByPercent
 *   validates the divided value, so `percent/100.0` must be in `0.0..200.0`.
 * - hour is range-checked 0..24, minute 0..59 — same ranges for both modes.
 *
 * Response (0x83): `[0] 0x83, [1] resultCode` → [SimpleResultResponse] (0 = SUCCESS). The wire reply is
 * result-only.
 */
class TempBasalCommand private constructor(
    private val mode: Mode,
    private val value: Double,
    private val hour: Int,
    private val minute: Int
) : BleCommand<SimpleResultResponse> {

    /** Which of the two 0x23 variants this instance encodes. Use [byUnit] / [byPercent] to build. */
    enum class Mode { BY_UNIT, BY_PERCENT }

    init {
        require(hour in HOUR_RANGE) { "hour $hour out of range $HOUR_RANGE" }
        require(minute in MINUTE_RANGE) { "minute $minute out of range $MINUTE_RANGE" }
        // BY_UNIT has no value-range check on the wire.
        if (mode == Mode.BY_PERCENT) {
            require(value in PERCENT_VALUE_RANGE) { "percent/100.0 = $value out of range $PERCENT_VALUE_RANGE" }
        }
    }

    override val requestOpcode: Byte = REQUEST_OPCODE
    override val expectedResponseOpcode: Byte = RESPONSE_OPCODE

    override fun encode(): ByteArray {
        val head = byteArrayOf(requestOpcode) + encodeUnitCenti(value) + byteArrayOf(hour.toByte(), minute.toByte())
        return when (mode) {
            Mode.BY_UNIT    -> head + byteArrayOf(TRAILING_BOOL_TRUE) // BY_UNIT trailing byte = 0x00
            Mode.BY_PERCENT -> head                                   // no trailing byte (the asymmetry)
        }
    }

    override fun decode(responsePayload: ByteArray): SimpleResultResponse {
        requireResponseFrame(responsePayload, RESPONSE_OPCODE, MIN_RESPONSE_LENGTH)
        return SimpleResultResponse(responsePayload.u(1))
    }

    companion object {

        const val REQUEST_OPCODE: Byte = 0x23
        const val RESPONSE_OPCODE: Byte = 0x83.toByte()

        private const val TRAILING_BOOL_TRUE: Byte = 0x00 // BY_UNIT trailing byte
        private val HOUR_RANGE = 0..24
        private val MINUTE_RANGE = 0..59
        private val PERCENT_VALUE_RANGE = 0.0..200.0
        private const val MIN_RESPONSE_LENGTH = 2

        /**
         * ByUnit variant — absolute temp basal rate [infusionUnit] (U/h) for [infusionHour]:[infusionMin].
         * No range check on [infusionUnit]. Encodes the 6-byte frame with the trailing 0x00.
         */
        fun byUnit(infusionUnit: Double, infusionHour: Int, infusionMin: Int): TempBasalCommand =
            TempBasalCommand(Mode.BY_UNIT, infusionUnit, infusionHour, infusionMin)

        /**
         * ByPercent variant — [infusionPercent] percent of scheduled basal for [infusionHour]:[infusionMin].
         * Encodes `infusionPercent / 100.0` (so 100 → `1.00`); that divided value must be in `0.0..200.0`.
         * Encodes the 5-byte frame (no trailing byte).
         */
        fun byPercent(infusionPercent: Int, infusionHour: Int, infusionMin: Int): TempBasalCommand =
            TempBasalCommand(Mode.BY_PERCENT, infusionPercent.toDouble() / 100.0, infusionHour, infusionMin)
    }
}

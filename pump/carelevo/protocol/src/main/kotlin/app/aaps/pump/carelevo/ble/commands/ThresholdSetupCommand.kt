package app.aaps.pump.carelevo.ble.commands

import app.aaps.pump.carelevo.ble.BleCommand

/**
 * `CMD_THRESHOLD_SETUP_REQ` (0x1B) → `CMD_THRESHOLD_SETUP_RES` (0x7B). Activation bundle that sets all
 * patch thresholds at once.
 *
 * Request wire format (7 bytes): `[0] 0x1B, [1] insulinRemainsThreshold, [2] expiryThreshold,
 * [3..4] maxBasalSpeed = [int,centi], [5..6] maxBolusDose = [int,centi], [7] buzzFlag`.
 *
 * **Wire-format notes:**
 * - `insulinRemainsThreshold` is a single wire byte, so `INS_REMAIN_RANGE` is 10..255; a value > 255
 *   would wrap on encode (300 → 0x2C = 44), silently misprogramming the low-insulin alarm threshold,
 *   so it is rejected instead. Every real caller feeds the low-insulin reminder preference (entries
 *   20..50 U), so nothing legitimately exceeds one byte; an out-of-range caller fails loudly rather
 *   than alarming 6× too late.
 * - `buzzFlag` is the inverted buzz flag → `buzzUse=true → 0x01`, `buzzUse=false → 0x00` (same double
 *   inversion as [BuzzModeCommand]).
 *
 * Response (0x7B): `[0] 0x7B, [1] resultCode` → [SimpleResultResponse].
 */
class ThresholdSetupCommand(
    private val insulinRemainsThreshold: Int,
    private val expiryThreshold: Int,
    private val maxBasalSpeed: Double,
    private val maxBolusDose: Double,
    private val buzzUse: Boolean
) : BleCommand<SimpleResultResponse> {

    init {
        require(insulinRemainsThreshold in INS_REMAIN_RANGE) { "insulinRemainsThreshold out of range $INS_REMAIN_RANGE" }
        require(expiryThreshold in EXPIRY_RANGE) { "expiryThreshold out of range $EXPIRY_RANGE" }
        require(maxBasalSpeed in MAX_BASAL_SPEED_RANGE) { "maxBasalSpeed out of range $MAX_BASAL_SPEED_RANGE" }
        require(maxBolusDose in MAX_BOLUS_DOSE_RANGE) { "maxBolusDose out of range $MAX_BOLUS_DOSE_RANGE" }
    }

    override val requestOpcode: Byte = REQUEST_OPCODE
    override val expectedResponseOpcode: Byte = RESPONSE_OPCODE

    override fun encode(): ByteArray =
        byteArrayOf(requestOpcode, insulinRemainsThreshold.toByte(), expiryThreshold.toByte()) +
            encodeUnitCenti(maxBasalSpeed) +
            encodeUnitCenti(maxBolusDose) +
            byteArrayOf(if (buzzUse) BUZZ_ON else BUZZ_OFF)

    override fun decode(responsePayload: ByteArray): SimpleResultResponse {
        requireResponseFrame(responsePayload, RESPONSE_OPCODE, MIN_RESPONSE_LENGTH)
        return SimpleResultResponse(responsePayload.u(1))
    }

    companion object {

        const val REQUEST_OPCODE: Byte = 0x1B
        const val RESPONSE_OPCODE: Byte = 0x7B
        // One wire byte; 10..255 valid, larger values would wrap — see class KDoc.
        private val INS_REMAIN_RANGE = 10..255
        private val EXPIRY_RANGE = 24..167
        private val MAX_BASAL_SPEED_RANGE = 0.05..15.0
        private val MAX_BOLUS_DOSE_RANGE = 0.05..25.0
        private const val BUZZ_ON: Byte = 0x01 // buzzUse=true  → 0x01 (inverted)
        private const val BUZZ_OFF: Byte = 0x00 // buzzUse=false → 0x00 (inverted)
        private const val MIN_RESPONSE_LENGTH = 2
    }
}

package app.aaps.pump.carelevo.ble.commands

import app.aaps.pump.carelevo.ble.BleCommand
import app.aaps.pump.carelevo.ble.BleResponse

/**
 * `CMD_INFUSION_INFO_REQ` (0x31) → `CMD_INFUSION_INFO_RPT` (0x91). The periodic status read: reservoir,
 * running time, infused basal/bolus totals, pump state + mode. Single-response [BleCommand].
 *
 * Request wire format (2 bytes): `[0] 0x31, [1] inquiryType` (0 = by-request).
 *
 * Response wire format (20 bytes):
 * ```
 * [0]       0x91            opcode
 * [1]       subId           inquiry source code (not persisted)
 * [2..3]    runningMinutes  = [2]*60 + [3]
 * [4..6]    insulinRemaining = [4]*100 + [5] + [6]/100.0   (U; the ×100 hundreds byte is a protocol quirk)
 * [7..8]    basal total     = [7] + [8]/100.0              (U)
 * [9..10]   bolus total     = [9] + [10]/100.0             (U)
 * [11]      pumpState (raw)
 * [12]      mode (raw)
 * [13..14]  infuseSetMinutes = [13]*60 + [14]              (not persisted)
 * [15..16]  currentInfusedProgramVolume = [15] + [16]/100.0 (not persisted)
 * [17..19]  realInfusedTime = ([17]*60 + [18])*60 + [19]   (seconds; not persisted)
 * ```
 * Decodes RAW values (unsigned). The pumpState/mode normalization to the persisted codes (the
 * int→enum→int roundtrip) happens at the state-apply layer (`CarelevoPatch.applyInfusionInfoReport`),
 * keeping this command a pure wire decoder.
 */
class InfusionInfoCommand(
    private val inquiryType: Int = DEFAULT_INQUIRY_TYPE
) : BleCommand<InfusionInfoResponse> {

    init {
        require(inquiryType in 0..1) { "inquiryType must be 0 or 1, got $inquiryType" }
    }

    override val requestOpcode: Byte = REQUEST_OPCODE
    override val expectedResponseOpcode: Byte = RESPONSE_OPCODE

    override fun encode(): ByteArray = byteArrayOf(requestOpcode, inquiryType.toByte())

    override fun decode(responsePayload: ByteArray): InfusionInfoResponse {
        require(responsePayload.isNotEmpty() && responsePayload[0] == expectedResponseOpcode) {
            "expected opcode 0x${"%02X".format(expectedResponseOpcode)}, got " +
                (responsePayload.getOrNull(0)?.let { "0x${"%02X".format(it)}" } ?: "empty")
        }
        require(responsePayload.size >= MIN_RESPONSE_LENGTH) {
            "response too short: ${responsePayload.size} < $MIN_RESPONSE_LENGTH"
        }
        fun u(i: Int) = responsePayload[i].toUByte().toInt()
        return InfusionInfoResponse(
            subId = u(1),
            runningMinutes = u(2) * MINUTES_PER_HOUR + u(3),
            insulinRemaining = u(4) * HUNDRED + u(5) + u(6) / CENTI,
            infusedTotalBasalAmount = u(7) + u(8) / CENTI,
            infusedTotalBolusAmount = u(9) + u(10) / CENTI,
            pumpStateRaw = u(11),
            modeRaw = u(12),
            currentInfusedProgramVolume = u(15) + u(16) / CENTI,
            realInfusedTime = (u(17) * MINUTES_PER_HOUR + u(18)) * SECONDS_PER_MINUTE + u(19)
        )
    }

    companion object {

        const val REQUEST_OPCODE: Byte = 0x31
        const val RESPONSE_OPCODE: Byte = 0x91.toByte()
        const val DEFAULT_INQUIRY_TYPE = 0

        private const val MIN_RESPONSE_LENGTH = 20
        private const val MINUTES_PER_HOUR = 60
        private const val SECONDS_PER_MINUTE = 60
        private const val HUNDRED = 100
        private const val CENTI = 100.0
    }
}

/**
 * Decoded response from [InfusionInfoCommand] — RAW wire values. [pumpStateRaw]/[modeRaw] are the
 * unnormalized bytes; `CarelevoPatch.applyInfusionInfoReport` applies the enum roundtrip before
 * persisting. [currentInfusedProgramVolume]/[realInfusedTime]/[subId] are decoded but not persisted.
 */
data class InfusionInfoResponse(
    val subId: Int,
    val runningMinutes: Int,
    val insulinRemaining: Double,
    val infusedTotalBasalAmount: Double,
    val infusedTotalBolusAmount: Double,
    val pumpStateRaw: Int,
    val modeRaw: Int,
    val currentInfusedProgramVolume: Double,
    val realInfusedTime: Int
) : BleResponse

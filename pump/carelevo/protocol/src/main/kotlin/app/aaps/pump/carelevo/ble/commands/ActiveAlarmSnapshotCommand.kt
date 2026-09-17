package app.aaps.pump.carelevo.ble.commands

import app.aaps.pump.carelevo.ble.BleCommand
import app.aaps.pump.carelevo.ble.BleResponse

/**
 * `CURRENT_ACTIVE_ALARM_REQ` (0x43) → one of `CURRENT_ACTIVE_ALARM_RPT` (0xA4/0xA5/0xA6).
 *
 * The second request byte selects which alarm tier snapshot to return:
 * - 0xA4: critical
 * - 0xA5: advisory
 * - 0xA6: notification
 *
 * Every response has the same fixed 15-byte shape:
 * ```
 * [0]   response opcode (0xA4 / 0xA5 / 0xA6)
 * [1]   infusing (0x01 = delivering, 0x00 = stopped)
 * [2]   out of insulin
 * [3]   operating life expired
 * [4]   low battery
 * [5]   out of range temperature
 * [6]   auto off
 * [7]   unconnected BLE
 * [8]   patch app incomplete
 * [9]   start insulin
 * [10]  self diagnosis failed
 * [11]  patch expired
 * [12]  patch error
 * [13]  occlusion detected
 * [14]  user forced termination
 * ```
 *
 * Slots a tier does not carry are documented as `unused` — the protocol does NOT promise they are
 * `0x00`, so [decode] masks them to `false` via [ActiveAlarmSnapshotTier.supportedFlags] rather than
 * trusting the wire byte. Without that mask a stale/garbage byte in an unused slot would surface as
 * a phantom alarm (e.g. an out-of-range-temperature alarm on the critical tier, which only the
 * advisory tier reports).
 */
class ActiveAlarmSnapshotCommand(
    val tier: ActiveAlarmSnapshotTier
) : BleCommand<ActiveAlarmSnapshotResponse> {

    override val requestOpcode: Byte = REQUEST_OPCODE
    override val expectedResponseOpcode: Byte = tier.responseOpcode

    override fun encode(): ByteArray = byteArrayOf(requestOpcode, tier.responseOpcode)

    override fun decode(responsePayload: ByteArray): ActiveAlarmSnapshotResponse {
        require(responsePayload.isNotEmpty() && responsePayload[0] == expectedResponseOpcode) {
            "expected opcode 0x${"%02X".format(expectedResponseOpcode)}, got " +
                (responsePayload.getOrNull(0)?.let { "0x${"%02X".format(it)}" } ?: "empty")
        }
        require(responsePayload.size >= RESPONSE_LENGTH) {
            "response too short: ${responsePayload.size} < $RESPONSE_LENGTH"
        }
        // A slot this tier does not carry is `unused` on the wire, not guaranteed zero — read it as
        // false regardless of the byte so an unused slot can never raise an alarm.
        fun flag(f: SnapshotAlarmFlag): Boolean =
            f in tier.supportedFlags && responsePayload[f.payloadIndex].toInt() != 0
        return ActiveAlarmSnapshotResponse(
            tier = tier,
            infusing = responsePayload[1].toInt() != 0,
            flags = ActiveAlarmSnapshotFlags(
                outOfInsulin = flag(SnapshotAlarmFlag.OUT_OF_INSULIN),
                operatingLifeExpired = flag(SnapshotAlarmFlag.OPERATING_LIFE_EXPIRED),
                lowBattery = flag(SnapshotAlarmFlag.LOW_BATTERY),
                outOfRangeTemperature = flag(SnapshotAlarmFlag.OUT_OF_RANGE_TEMPERATURE),
                autoOff = flag(SnapshotAlarmFlag.AUTO_OFF),
                unconnectedBle = flag(SnapshotAlarmFlag.UNCONNECTED_BLE),
                patchAppIncomplete = flag(SnapshotAlarmFlag.PATCH_APP_INCOMPLETE),
                startInsulin = flag(SnapshotAlarmFlag.START_INSULIN),
                selfDiagnosisFailed = flag(SnapshotAlarmFlag.SELF_DIAGNOSIS_FAILED),
                patchExpired = flag(SnapshotAlarmFlag.PATCH_EXPIRED),
                patchError = flag(SnapshotAlarmFlag.PATCH_ERROR),
                occlusionDetected = flag(SnapshotAlarmFlag.OCCLUSION_DETECTED),
                userForcedTermination = flag(SnapshotAlarmFlag.USER_FORCED_TERMINATION)
            )
        )
    }

    companion object {
        const val REQUEST_OPCODE: Byte = 0x43
        private const val RESPONSE_LENGTH = 15
    }
}

enum class ActiveAlarmSnapshotTier(val responseOpcode: Byte) {
    CRITICAL(0xA4.toByte()),
    ADVISORY(0xA5.toByte()),
    NOTIFICATION(0xA6.toByte());

    /**
     * Slots the patch actually populates for this tier, per the protocol's per-field
     * `/* Critical | Advisory | Notification */` annotations. Every other slot is `unused` and its
     * wire byte is not meaningful — see [ActiveAlarmSnapshotCommand.decode].
     */
    val supportedFlags: Set<SnapshotAlarmFlag>
        get() = when (this) {
            CRITICAL     -> setOf(
                SnapshotAlarmFlag.OUT_OF_INSULIN,
                SnapshotAlarmFlag.LOW_BATTERY,
                SnapshotAlarmFlag.AUTO_OFF,
                SnapshotAlarmFlag.UNCONNECTED_BLE,
                SnapshotAlarmFlag.PATCH_APP_INCOMPLETE,
                SnapshotAlarmFlag.SELF_DIAGNOSIS_FAILED,
                SnapshotAlarmFlag.PATCH_EXPIRED,
                SnapshotAlarmFlag.PATCH_ERROR,
                SnapshotAlarmFlag.OCCLUSION_DETECTED,
                SnapshotAlarmFlag.USER_FORCED_TERMINATION
            )

            ADVISORY     -> setOf(
                SnapshotAlarmFlag.OUT_OF_INSULIN,
                SnapshotAlarmFlag.OPERATING_LIFE_EXPIRED,
                SnapshotAlarmFlag.LOW_BATTERY,
                SnapshotAlarmFlag.OUT_OF_RANGE_TEMPERATURE,
                SnapshotAlarmFlag.AUTO_OFF,
                SnapshotAlarmFlag.UNCONNECTED_BLE,
                SnapshotAlarmFlag.PATCH_APP_INCOMPLETE,
                SnapshotAlarmFlag.START_INSULIN,
                SnapshotAlarmFlag.PATCH_EXPIRED
            )

            NOTIFICATION -> setOf(SnapshotAlarmFlag.OPERATING_LIFE_EXPIRED)
        }
}

data class ActiveAlarmSnapshotFlags(
    val outOfInsulin: Boolean,
    val operatingLifeExpired: Boolean,
    val lowBattery: Boolean,
    val outOfRangeTemperature: Boolean,
    val autoOff: Boolean,
    val unconnectedBle: Boolean,
    val patchAppIncomplete: Boolean,
    val startInsulin: Boolean,
    val selfDiagnosisFailed: Boolean,
    val patchExpired: Boolean,
    val patchError: Boolean,
    val occlusionDetected: Boolean,
    val userForcedTermination: Boolean
)

/**
 * One snapshot slot. [payloadIndex] is its byte position in the 15-byte report; [causeCode] is the
 * matching `CAUSE` byte from the pushed-alarm reports (0xA1/0xA2/0xA3), which lets a snapshot resolve
 * to the *same* [app.aaps.pump.carelevo.domain.type.AlarmCause] the live alarm would have produced.
 *
 * The slot order is the cause-code order — the two tables are the same table. `USER_FORCED_TERMINATION`
 * is the one slot with no pushed-alarm counterpart, so it has no [causeCode].
 */
enum class SnapshotAlarmFlag(val payloadIndex: Int, val causeCode: Int?) {
    OUT_OF_INSULIN(2, 0x01),
    OPERATING_LIFE_EXPIRED(3, 0x02),
    LOW_BATTERY(4, 0x03),
    OUT_OF_RANGE_TEMPERATURE(5, 0x04),
    AUTO_OFF(6, 0x05),
    UNCONNECTED_BLE(7, 0x06),
    PATCH_APP_INCOMPLETE(8, 0x07),
    START_INSULIN(9, 0x08),
    SELF_DIAGNOSIS_FAILED(10, 0x09),
    PATCH_EXPIRED(11, 0x0A),
    PATCH_ERROR(12, 0x0B),
    OCCLUSION_DETECTED(13, 0x0C),
    USER_FORCED_TERMINATION(14, null)
}

data class ActiveAlarmSnapshotResponse(
    val tier: ActiveAlarmSnapshotTier,
    val infusing: Boolean,
    val flags: ActiveAlarmSnapshotFlags
) : BleResponse

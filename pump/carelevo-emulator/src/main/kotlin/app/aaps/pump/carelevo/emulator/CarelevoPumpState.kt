package app.aaps.pump.carelevo.emulator

/**
 * Mutable state of the emulated CareLevo patch.
 *
 * The emulator answers status queries from here rather than from canned frames, so the driver sees a
 * patch that remembers what it was told: a basal program written over three sequences, a bolus that
 * draws the reservoir down, a stop that reports itself as stopped.
 *
 * Raw codes mirror `CarelevoBtEnums` and are deliberately raw (not enums) because they go on the wire:
 * - [pumpStateRaw]: 0 READY, 1 PRIMING, 2 RUNNING — anything else decodes to ERROR
 * - [modeRaw]: 1 BASAL, 2 TEMP_BASAL, 3 IMME_BOLUS, 4 EXTEND_IMME_BOLUS, 5 EXTEND_BOLUS.
 *   **0 decodes to ERROR**, so it is never a resting value.
 */
class CarelevoPumpState {

    // ---- Identity ---------------------------------------------------------------------------

    /** Exactly 13 chars — occupies bytes 2..14 of the 0x93 report, one ASCII byte per char. */
    var serialNumber: String = "CL24000000001"

    /** Exactly 4 chars — bytes 2..5 of the 0x94 report, ASCII. */
    var firmwareVersion: String = "1.10"

    /**
     * Five raw bytes at 0x94[11..15]. The decoder concatenates each byte's DECIMAL value, so
     * `[1, 2, 0, 0, 0]` reads back as the model name "12000" — it is not ASCII.
     */
    var modelBytes: ByteArray = byteArrayOf(1, 2, 0, 0, 0)

    /** Six raw bytes, MSB first; surfaces to the driver as the uppercase hex "94B2161D2F6D". */
    var macAddress: ByteArray = byteArrayOf(0x94.toByte(), 0xB2.toByte(), 0x16, 0x1D, 0x2F, 0x6D)

    /** Trailing checksum bytes of the 0x9B frame. Content is arbitrary — see [lastMacKey]. */
    var macCheckSum: ByteArray = byteArrayOf(0x5A)

    /** Key from the last 0x3B request, retained so 0x4B can verify the app's XOR fold. */
    var lastMacKey: Int? = null

    // ---- Reservoir and totals ---------------------------------------------------------------

    /** Units remaining. Reported by 0x91 and by the 0x84 bolus ack. */
    var insulinRemaining: Double = 200.0
    var infusedTotalBasal: Double = 0.0
    var infusedTotalBolus: Double = 0.0

    // ---- Lifecycle --------------------------------------------------------------------------

    var pumpStateRaw: Int = 2
    var modeRaw: Int = 1
    var subId: Int = 0
    var runningMinutes: Int = 0
    var stopped: Boolean = false
    var discarded: Boolean = false
    var needleInserted: Boolean = false
    var primingCount: Int = 0

    // ---- Basal ------------------------------------------------------------------------------

    /**
     * Segment speeds accumulated per sequence number. A full program is three independent writes
     * (seq 0, 1, 2), each separately acked; [basalProgramCommitted] flips only once seq 2 lands.
     */
    val basalSegmentsBySeq: MutableMap<Int, List<Double>> = mutableMapOf()
    var basalProgramCommitted: Boolean = false

    /** Set of the whole program, in segment order, once all three sequences have arrived. */
    val basalProgram: List<Double>
        get() = (0..2).flatMap { basalSegmentsBySeq[it].orEmpty() }

    // ---- Temp basal / bolus -----------------------------------------------------------------

    var tempBasalRunning: Boolean = false
    var extendedBolusRunning: Boolean = false

    /** actionId of the in-flight immediate bolus; echoed back on 0x84 for correlation. */
    var activeBolusActionId: Int? = null

    /** Units already delivered by the in-flight bolus — reported by the 0x8C cancel ack. */
    var bolusInfusedAmount: Double = 0.0
    var extendedInfusedAmount: Double = 0.0

    // ---- Thresholds and settings ------------------------------------------------------------

    var insulinRemainsThreshold: Int = 20
    var expiryThreshold: Int = 72
    var maxBasalSpeed: Double = 15.0
    var maxBolusDose: Double = 25.0
    var buzzUse: Boolean = false
    var lowInsulinNotice: Int = 20
    var expiryNotice: Int = 72
    var alertAlarmMode: Int = 0

    // ---- Safety check -----------------------------------------------------------------------

    /** Progress frames the 0x12 stream emits before its terminal frame. */
    var safetyCheckProgressFrames: Int = 2

    /** Priming volume reported by the safety-check stream, in whole units. */
    var safetyCheckVolume: Int = 210

    /** Priming duration reported by the safety-check stream, in seconds. */
    var safetyCheckDurationSeconds: Int = 210

    /** Terminal result of the safety-check stream. 0 = success; see `SafetyCheckCommand`. */
    var safetyCheckResult: Int = 0

    // ---- Fault injection --------------------------------------------------------------------

    /** Opcodes the patch silently ignores, so a caller's timeout is the only way out. */
    val silentOpcodes: MutableSet<Byte> = mutableSetOf()

    /** Per-opcode result-code override, for driving the driver's error paths. */
    val resultCodeOverrides: MutableMap<Byte, Int> = mutableMapOf()

    /** Result code this opcode should answer with — 0 (success) unless overridden. */
    fun resultFor(requestOpcode: Byte): Int = resultCodeOverrides[requestOpcode] ?: RESULT_SUCCESS

    companion object {

        const val RESULT_SUCCESS = 0
    }
}

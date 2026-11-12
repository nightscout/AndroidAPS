package app.aaps.plugins.dexcomoneplus.parse

import app.aaps.plugins.dexcomoneplus.OnePlusGlucoseSample
import kotlin.time.Clock

/**
 * Pure glucose parse / bounds helpers + Control EGV packet decode.
 *
 * Kotlin rewrite of field layouts from NightscoutFoundation/xDrip (GPL-3.0):
 * - `g5model/EGlucoseRxMessage.java` (opcode 0x4f + FastCRC16)
 * - `g5model/EGlucoseRxMessage2.java` / `cgm/dex/g7/EGlucoseRxMessage.java` (opcode 0x4e)
 * - trend scaling from `BaseGlucoseRxMessage.getTrend()` (raw ≠ 127 → mg/dL/min = raw/10)
 *
 * Pin: `1e86d9a2a52577ed2c30fbf7b69d75fd56e6918f`. See `plugins/dexcom_oneplus/NOTICE`.
 */
object OnePlusGlucoseParser {

    const val MIN_MGDL: Double = 20.0
    const val MAX_MGDL: Double = 600.0

    /** xDrip `EGlucoseRxMessage.opcode` (CRC-checked). */
    const val OPCODE_EGV1: Int = 0x4f

    /** xDrip `EGlucoseRxMessage2` / G7 `EGlucoseRxMessage.opcode` (no CRC in Ob1 path). */
    const val OPCODE_EGV2: Int = 0x4e

    /** Upstream invalid trend marker (`BaseGlucoseRxMessage` / G7 `getTrend`). */
    const val TREND_INVALID: Int = 127

    /** Bit of the flags byte (0x4E byte 18, backfill record byte 7) that marks a display-only value. */
    const val DISPLAY_ONLY_FLAG: Int = 0x10

    fun inRange(mgdl: Double): Boolean = mgdl in MIN_MGDL..MAX_MGDL

    /**
     * Accepts a numeric mg/dL value; returns null if outside [MIN_MGDL]..[MAX_MGDL].
     * Bounds gate (reject, do not coerce).
     */
    fun sanitizeMgdl(mgdl: Double): Double? = if (inRange(mgdl)) mgdl else null

    /**
     * Builds a sample when value is in range; null otherwise (caller logs ERROR / drops).
     */
    fun toSample(
        mgdl: Double,
        timestampMs: Long,
        trendSlopeMgdlPerMin: Double? = null,
        sequence: Long? = null,
    ): OnePlusGlucoseSample? {
        val safe = sanitizeMgdl(mgdl) ?: return null
        return OnePlusGlucoseSample(
            mgdl = safe,
            timestampMs = timestampMs,
            trendSlopeMgdlPerMin = trendSlopeMgdlPerMin,
            sequence = sequence,
        )
    }

    /**
     * Decode Control EGV notify bytes → [OnePlusGlucoseSample], or null when opcode/layout/bounds fail.
     */
    fun parse(packet: ByteArray, nowMs: Long = Clock.System.now().toEpochMilliseconds()): OnePlusGlucoseSample? =
        parseControlPacket(packet, nowMs)?.sample

    data class ParsedEgv(
        val sample: OnePlusGlucoseSample?,
        val calibration: OnePlusCalibrationState,
        val usable: Boolean,
        val ageSeconds: Int?,
        val sessionAgeSeconds: Int?,
        val opcode: Int,
        /**
         * The sensor marks the value as for display only: it repeats a fingerstick right after a
         * calibration, it is not a measurement. It never becomes a [sample].
         */
        val glucoseIsDisplayOnly: Boolean = false,
        val predictedGlucose: Int? = null,
        /** Byte 18 of a 0x4E packet, raw, for the log. Null for 0x4F. */
        val flags: Int? = null,
    )

    /**
     * Parse a Control characteristic notification with calibration metadata.
     * Returns null if not an EGV opcode / too short / CRC fail (EGV1).
     */
    fun parseControlPacket(packet: ByteArray, nowMs: Long = Clock.System.now().toEpochMilliseconds()): ParsedEgv? {
        if (packet.isEmpty()) return null
        val opcode = packet[0].toInt() and 0xff
        return when (opcode) {
            OPCODE_EGV1 -> parseEgv1(packet, nowMs)
            OPCODE_EGV2 -> parseEgv2(packet, nowMs)
            else -> null
        }
    }

    /**
     * Opcode 0x4f — `g5model/EGlucoseRxMessage` (min 14 bytes, FastCRC16 on trailing 2).
     * Wall timestamp uses [nowMs] (upstream `getRealTimestamp` default = receive time).
     */
    private fun parseEgv1(packet: ByteArray, nowMs: Long): ParsedEgv? {
        if (packet.size < 14) return null
        if (!OnePlusFastCrc16.check(packet)) return null
        val data = OnePlusByteReader(packet)
        data.skip(1) // opcode
        data.skip(1) // status / battery raw
        val sequence = data.int().toLong() and 0xffffffffL
        val txTimestamp = data.int() // dex relative seconds
        val glucoseBytes = data.short().toInt() and 0xffff
        val glucoseIsDisplayOnly = (glucoseBytes and 0xf000) != 0
        val glucose = glucoseBytes and 0x0fff
        val state = data.u8()
        val trend = data.byte().toInt() // signed byte promoted
        val predicted = if (data.remaining >= 2) data.short().toInt() and 0x03ff else null
        val cal = OnePlusCalibrationState.parse(state)
        val sample = if (cal.usableGlucose() && glucose > 13) {
            toSample(
                mgdl = glucose.toDouble(),
                timestampMs = nowMs,
                trendSlopeMgdlPerMin = trendToSlope(trend),
                sequence = sequence,
            )
        } else {
            null
        }
        return ParsedEgv(
            sample = sample,
            calibration = cal,
            usable = cal.usableGlucose() && sample != null,
            ageSeconds = null,
            sessionAgeSeconds = if (txTimestamp > 0) txTimestamp else null,
            opcode = OPCODE_EGV1,
            glucoseIsDisplayOnly = glucoseIsDisplayOnly,
            predictedGlucose = predicted,
        )
    }

    /**
     * Opcode 0x4e — G7 / ONE+ `EGlucoseRxMessage2` / `cgm/dex/g7/EGlucoseRxMessage` (min 19 bytes).
     * Timestamp = [nowMs] − ageSeconds × 1000 (upstream `getRealTimestamp`).
     */
    private fun parseEgv2(packet: ByteArray, nowMs: Long): ParsedEgv? {
        if (packet.size < 19) return null
        // Ob1 path matches opcode only (no CRC).
        val data = OnePlusByteReader(packet)
        data.skip(1) // opcode 0x4e
        data.skip(1) // status_raw
        val sessionAge = readUnsignedInt(data).toInt()
        val sequence = readUnsignedShort(data).toLong()
        readUnsignedShort(data) // unused / "bogus" upstream
        val age = readUnsignedShort(data)
        val glucoseBytes = data.short().toInt() and 0xffff
        val glucose = glucoseBytes and 0x0fff
        val state = data.u8()
        val trend = data.byte().toInt() // signed byte promoted
        var predicted: Int? = data.short().toInt() and 0x03ff
        if (predicted == 0x3ff) predicted = null // Message2 invalid marker
        // Display only is bit 0x10 of byte 18, as in Loop's G7SensorKit (`G7GlucoseMessage`) and in
        // the Dexcom G7 driver of AAPS PR 5397. xDrip reads it from the top bits of the glucose
        // field, which is the G6 layout: on a G7 those bits are always 0, so a value echoed after a
        // calibration was never recognised and went to the loop.
        val flags = data.u8()
        val glucoseIsDisplayOnly = glucoseBytes != 0xffff && (flags and DISPLAY_ONLY_FLAG) != 0
        val cal = OnePlusCalibrationState.parse(state)
        val adjusted = if (cal == OnePlusCalibrationState.Stopped && sessionAge * 1000L < 30L * 60L * 1000L) {
            OnePlusCalibrationState.WarmingUp
        } else {
            cal
        }
        val ts = nowMs - age * 1000L
        // Sample when usable + in range; ageRecent (age < 305) is Ob1 sync-only, not an insert gate.
        val sample = if (adjusted.usableGlucose() && glucose > 13 && !glucoseIsDisplayOnly) {
            toSample(
                mgdl = glucose.toDouble(),
                timestampMs = ts,
                trendSlopeMgdlPerMin = trendToSlope(trend),
                sequence = sequence,
            )
        } else {
            null
        }
        return ParsedEgv(
            sample = sample,
            calibration = adjusted,
            usable = adjusted.usableGlucose() && sample != null,
            ageSeconds = age,
            sessionAgeSeconds = sessionAge,
            opcode = OPCODE_EGV2,
            glucoseIsDisplayOnly = glucoseIsDisplayOnly,
            predictedGlucose = predicted,
            flags = flags,
        )
    }

    /**
     * Upstream: `trend != 127 ? trend / 10.0 : NaN`. The EGV trend byte is a signed rate scaled by
     * ten, so dividing by ten gives mg/dL per minute — see [OnePlusGlucoseSample.trendSlopeMgdlPerMin].
     */
    internal fun trendToSlope(trend: Int): Double? {
        if (trend == TREND_INVALID) return null
        return trend / 10.0
    }

    /** Matches `cgm/dex/g7/BaseMessage.getUnsignedInt`. */
    private fun readUnsignedInt(data: OnePlusByteReader): Long {
        val b0 = data.u8().toLong()
        val b1 = data.u8().toLong()
        val b2 = data.u8().toLong()
        val b3 = data.u8().toLong()
        return b0 + (b1 shl 8) + (b2 shl 16) + (b3 shl 24)
    }

    /** Matches `cgm/dex/g7/BaseMessage.getUnsignedShort`. */
    private fun readUnsignedShort(data: OnePlusByteReader): Int {
        val b0 = data.u8()
        val b1 = data.u8()
        return b0 + (b1 shl 8)
    }
}

package app.aaps.plugins.dexcomoneplus.parse

import app.aaps.plugins.dexcomoneplus.OnePlusGlucoseSample
import kotlin.time.Clock

/**
 * Assembles ProbablyBackfill notifications and decodes G7/ONE+ records.
 *
 * Short-TxId path (`streamType > 0` in xDrip): concatenate payload bytes; each
 * record is 9 bytes: dexTime | glucose12 | type | flags | trend. A record whose flags carry
 * [OnePlusGlucoseParser.DISPLAY_ONLY_FLAG] repeats a fingerstick and is skipped.
 *
 * Provenance: xDrip `BackFillStream` at pin
 * `1e86d9a2a52577ed2c30fbf7b69d75fd56e6918f` (GPL-3.0).
 */
class OnePlusBackFillStream {

    private val buffer = ByteArray(MAX_BUFFER)

    /** How much of [buffer] holds bytes, the same thing `ByteBuffer.position()` told us. */
    private var filled = 0

    fun hasData(): Boolean = filled > 0

    fun byteCount(): Int = filled

    fun reset() {
        filled = 0
    }

    /** Append a G7/ONE+ backfill notification (full packet). */
    fun pushG7(packet: ByteArray) {
        if (packet.isEmpty()) return
        val remaining = MAX_BUFFER - filled
        if (remaining <= 0) return
        val n = minOf(packet.size, remaining)
        packet.copyInto(buffer, destinationOffset = filled, startIndex = 0, endIndex = n)
        filled += n
    }

    /**
     * Decode buffered G7 records into glucose samples.
     *
     * @param currentDexTimeSeconds transmitter clock now (from TransmitterTime)
     * @param nowMs wall clock corresponding to [currentDexTimeSeconds]
     */
    fun decode(
        currentDexTimeSeconds: Int,
        nowMs: Long = Clock.System.now().toEpochMilliseconds(),
        maxAgeMs: Long = MAX_AGE_MS,
    ): List<OnePlusGlucoseSample> {
        val extent = filled
        if (extent < G7_RECORD_SIZE) return emptyList()
        // A reader over the filled part only, which is what flipping the old buffer gave.
        val view = OnePlusByteReader(buffer.copyOf(extent))
        val out = ArrayList<OnePlusGlucoseSample>()
        try {
            while (view.remaining >= G7_RECORD_SIZE) {
                val dexTime = view.int()
                val glucose = view.short().toInt() and 0x0fff
                val type = view.u8()
                val flags = view.u8()
                val trend = view.byte().toInt()
                val state = OnePlusCalibrationState.parse(type)
                if (!state.usableGlucose()) continue
                if (flags and OnePlusGlucoseParser.DISPLAY_ONLY_FLAG != 0) continue
                if (dexTime == 0) continue
                val ts = fromDexTime(dexTime, currentDexTimeSeconds, nowMs)
                val age = nowMs - ts
                if (age < 0L || age > maxAgeMs) continue
                val sample = OnePlusGlucoseParser.toSample(
                    mgdl = glucose.toDouble(),
                    timestampMs = ts,
                    trendSlopeMgdlPerMin = trendToSlope(trend),
                ) ?: continue
                out.add(sample)
            }
        } catch (_: Exception) {
            // mismatched trailing bytes — return what we parsed
        }
        return out
    }

    companion object {
        const val G7_RECORD_SIZE: Int = 9
        const val MAX_BUFFER: Int = 2800
        /** Align with Ob1 short-TxId window (~24 h) with slack. */
        const val MAX_AGE_MS: Long = 25L * 60L * 60L * 1000L

        fun fromDexTime(dexTime: Int, currentDexTimeSeconds: Int, nowMs: Long): Long =
            nowMs - (currentDexTimeSeconds.toLong() - dexTime.toLong()) * 1000L

        private fun trendToSlope(trend: Int): Double? = OnePlusGlucoseParser.trendToSlope(trend)
    }
}

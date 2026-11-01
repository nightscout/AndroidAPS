package app.aaps.plugins.libre3.parse

import app.aaps.plugins.libre3.Libre3GlucoseSample
import app.aaps.plugins.libre3.warmup.Libre3WarmupClock

/**
 * Which way the glucose is going, as the sensor sees it.
 *
 * @param arrowName the name AAPS uses for the same arrow. The driver module cannot see the AAPS
 *   arrow type, so the name is carried as text and turned into an arrow on the other side.
 */
enum class Libre3Trend(val arrowName: String) {

    NOT_DETERMINED("NONE"),
    FALLING_QUICKLY("DoubleDown"),
    FALLING("SingleDown"),
    STABLE("Flat"),
    RISING("SingleUp"),
    RISING_QUICKLY("DoubleUp"),
    UNKNOWN("NONE");

    companion object {

        fun fromBits(bits: Int): Libre3Trend = when (bits) {
            0    -> NOT_DETERMINED
            1    -> FALLING_QUICKLY
            2    -> FALLING
            3    -> STABLE
            4    -> RISING
            5    -> RISING_QUICKLY
            else -> UNKNOWN
        }
    }
}

/** What the sensor says about itself while it made this reading. */
enum class Libre3SensorCondition {
    OK, INVALID, ESA, UNKNOWN;

    companion object {

        fun fromBits(bits: Int): Libre3SensorCondition = when (bits) {
            0    -> OK
            1    -> INVALID
            2    -> ESA
            else -> UNKNOWN
        }
    }
}

/** Why a reading may not be given to the loop. */
enum class Libre3QualityIssue {

    /** The sensor is still warming up. */
    SENSOR_WARMUP,

    /** The sensor has run for its whole life. */
    SENSOR_EXPIRED,

    /** The number is outside anything that can be shown. */
    GLUCOSE_UNAVAILABLE,

    /** The sensor marked the reading itself as bad. */
    DATA_QUALITY,

    /** The sensor was not in a good state. */
    SENSOR_CONDITION,

    /**
     * The sensor says this reading should not be acted on.
     *
     * This one is only advice. A reading can carry this and still be given to the loop when every
     * other check passes. That matches the upstream rule.
     */
    NOT_ACTIONABLE;

    /** True when this issue alone stops a reading from reaching the loop. */
    val blocksUse: Boolean get() = this != NOT_ACTIONABLE
}

/**
 * One real time reading, read from the 29 plain bytes the sensor sends.
 *
 * Ported from LibreCRKit `DataPlane/RealtimeGlucoseReading.swift` at pin `a86b92f`. The Swift
 * parser is the truth for these offsets.
 *
 * @param lifeCount minutes since the sensor started. Also the anti repeat marker.
 * @param glucoseMgdl the number to show and to use, already mapped into the shown range, or null
 *   when the sensor sent nothing usable.
 * @param rateOfChangeMgdlPerMinute how fast it moves, or null when the sensor did not say.
 */
data class Libre3GlucoseReading(
    val lifeCount: Int,
    val glucoseMgdl: Int?,
    val uncappedGlucoseMgdl: Int,
    val rateOfChangeMgdlPerMinute: Double?,
    val trend: Libre3Trend,
    val sensorCondition: Libre3SensorCondition,
    val dataQualityGood: Boolean,
    val actionable: Boolean,
    /**
     * Byte 14 exactly as the sensor sent it.
     *
     * Kept only for the log. A sensor variant that puts the "may be acted on" bit somewhere else
     * would be invisible without it, and the upstream project logs the same byte for that reason.
     */
    val trendAndStatusByte: Int,
) {

    /**
     * Everything that is wrong with this reading, in the order it was checked.
     *
     * @param clock where the sensor is in its life, or null when that is not known yet.
     */
    fun qualityIssues(clock: Libre3WarmupClock?): List<Libre3QualityIssue> {
        val issues = mutableListOf<Libre3QualityIssue>()
        if (clock != null) {
            if (clock.isExpired) issues.add(Libre3QualityIssue.SENSOR_EXPIRED)
            else if (clock.isWarmingUp) issues.add(Libre3QualityIssue.SENSOR_WARMUP)
        }
        if (glucoseMgdl == null) issues.add(Libre3QualityIssue.GLUCOSE_UNAVAILABLE)
        if (!dataQualityGood) issues.add(Libre3QualityIssue.DATA_QUALITY)
        if (sensorCondition != Libre3SensorCondition.OK) issues.add(Libre3QualityIssue.SENSOR_CONDITION)
        if (!actionable) issues.add(Libre3QualityIssue.NOT_ACTIONABLE)
        return issues
    }

    /**
     * True when this reading may be given to the loop.
     *
     * Every check must pass except the advice one. This is the gate that decides what AAPS is
     * allowed to dose on, so it is deliberately strict and has its own tests.
     */
    fun isUsable(clock: Libre3WarmupClock?): Boolean = qualityIssues(clock).none { it.blocksUse }
}

/** Raised when a block of bytes is not a reading. */
class Libre3ParseException(message: String) : Exception(message)

/**
 * Turns a reading into the sample the plugin ingests, or refuses it.
 *
 * This is the **only** way a reading may become something AAPS can dose on, and the gate is baked
 * in on purpose: there is no path that produces a sample without the checks having run.
 *
 * The sensor life is always built from the reading's own minute counter, never left out. A missing
 * life would silently switch off the warm-up and expiry checks, and the upstream project always
 * passes one for exactly that reason.
 *
 * @param activatedAtMs when the sensor was started, in phone time. The sample time is counted from
 *   here, so it must be the sensor's own start, not the moment of the NFC scan. A value of zero
 *   means it is not known, and then no sample is made at all.
 * @param wearMinutes how long this sensor may run, from the NFC step. It has no default on
 *   purpose: a missing whole life switches the expiry check off, so the caller must say what it
 *   knows, even if the answer is that it knows nothing.
 * @return the sample, or null when this reading may not be used.
 */
fun Libre3GlucoseReading.toSampleOrNull(
    activatedAtMs: Long,
    wearMinutes: Int?,
    warmupMinutes: Int = Libre3WarmupClock.DEFAULT_WARMUP_MINUTES,
): Libre3GlucoseSample? {
    val clock = Libre3WarmupClock(
        lifeCountMinutes = lifeCount,
        warmupMinutes = warmupMinutes,
        wearMinutes = wearMinutes,
    )
    // Without a real start time every sample time would be counted from 1970. AAPS would then see
    // readings that are decades old, find no recent glucose, and quietly do nothing. Refusing is
    // the only safe answer: the caller has to learn the start time first.
    if (activatedAtMs <= 0L) return null
    if (!isUsable(clock)) return null
    // The number that may be used is the one mapped into the shown range, never the raw one.
    val usableMgdl = glucoseMgdl ?: return null
    return Libre3GlucoseSample(
        mgdl = usableMgdl.toDouble(),
        timestampMs = Libre3WarmupClock.sampleTimeMs(activatedAtMs, lifeCount),
        lifeCount = lifeCount,
        trendArrowRaw = trend.arrowName,
        rateOfChangeMgdlPerMin = rateOfChangeMgdlPerMinute,
    )
}

/**
 * Reads the 29 plain bytes of a real time reading.
 *
 * Layout, from the Swift parser:
 * - 0, two bytes: minutes since the sensor started
 * - 2, two bytes: packed word. Bits 0 to 12 the number, bits 13 and 14 the sensor state,
 *   bit 15 means the number cannot be shown
 * - 4, two bytes with a sign: hundredths of mg/dL per minute. The lowest possible value means
 *   "not known"
 * - 14: bits 0 to 2 the direction, bit 3 says the reading may be acted on
 * - 15, two bytes: the number in mg/dL before it is cut to the shown range
 */
object Libre3GlucoseParser {

    const val PLAINTEXT_SIZE = 29

    /** Below this the sensor is only saying "low". It is shown as this number. */
    const val DISPLAY_MIN_MGDL = 39

    /** Above this the sensor is only saying "high". It is shown as this number. */
    const val DISPLAY_MAX_MGDL = 501

    /** The value that means "no rate of change was measured". */
    const val RATE_OF_CHANGE_MISSING = Short.MIN_VALUE.toInt()

    fun parse(plaintext: ByteArray): Libre3GlucoseReading {
        if (plaintext.size != PLAINTEXT_SIZE) {
            throw Libre3ParseException("a reading is 29 bytes, not ${plaintext.size}")
        }
        val packed = u16(plaintext, 2)
        val rateRaw = u16(plaintext, 4).toShort().toInt()
        val trendAndStatus = plaintext[14].toInt() and 0xFF
        val uncapped = u16(plaintext, 15)

        return Libre3GlucoseReading(
            lifeCount = u16(plaintext, 0),
            glucoseMgdl = displayValue(uncapped),
            uncappedGlucoseMgdl = uncapped,
            rateOfChangeMgdlPerMinute = if (rateRaw == RATE_OF_CHANGE_MISSING) null else rateRaw / 100.0,
            trend = Libre3Trend.fromBits(trendAndStatus and 0x07),
            sensorCondition = Libre3SensorCondition.fromBits((packed shr 13) and 0x03),
            // Bit 15 of the packed word is the sensor's own "do not show this" mark.
            dataQualityGood = (packed and 0x8000) == 0,
            actionable = (trendAndStatus and 0x08) != 0,
            trendAndStatusByte = trendAndStatus,
        )
    }

    /**
     * Maps what the sensor sent into what may be shown and used.
     *
     * 1 to 38 becomes 39, 39 to 501 stays as it is, 502 to 999 becomes 501, and anything else
     * means the sensor had no number to give.
     */
    fun displayValue(rawMgdl: Int): Int? = when (rawMgdl) {
        in 1 until DISPLAY_MIN_MGDL     -> DISPLAY_MIN_MGDL
        in DISPLAY_MIN_MGDL..DISPLAY_MAX_MGDL -> rawMgdl
        in 502 until 1000               -> DISPLAY_MAX_MGDL
        else                            -> null
    }

    private fun u16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
}

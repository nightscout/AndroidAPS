package app.aaps.plugins.dexcomoneplus.session

import app.aaps.plugins.dexcomoneplus.OnePlusGlucoseSample
import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import app.aaps.plugins.dexcomoneplus.parse.OnePlusCalibrateTx
import kotlin.math.abs

/**
 * Whether a fingerstick may go to the sensor now.
 *
 * A sensor keeps a calibration it takes for good, so these checks run before the user confirms and
 * again when the value is handed over. The limits are software choices, not clinical advice:
 * range 40..400 mg/dL as the sensor accepts, blood at most one hour old, sensor past warm-up with a
 * recent reading, and glucose not changing faster than [MAX_RATE_MGDL_PER_MIN].
 */
object OnePlusCalibrationGate {

    enum class Refusal {

        /** Outside 40..400 mg/dL, what the sensor itself accepts. */
        OUT_OF_RANGE,

        /** The blood was measured more than an hour ago, or in the future. */
        TOO_OLD,

        /** The sensor is warming up, or has failed. */
        NOT_READY,

        /** No usable reading in the last [RECENT_READING_MS]: nothing proves the sensor is past warm-up. */
        NO_RECENT_READING,

        /** The last reading has no trend, so it is not known whether glucose is stable. */
        RATE_UNKNOWN,

        /** Glucose changes faster than [MAX_RATE_MGDL_PER_MIN]. A fingerstick then does not match the sensor. */
        CHANGING_TOO_FAST,
    }

    /** Two G7 cycles and a little more. */
    const val RECENT_READING_MS = 11L * 60L * 1000L

    const val MAX_RATE_MGDL_PER_MIN = 2.0

    fun check(
        glucoseMgdl: Int,
        bloodAtMs: Long,
        nowMs: Long,
        phase: OnePlusWarmupState.Phase,
        lastReading: OnePlusGlucoseSample?,
    ): Refusal? {
        if (glucoseMgdl < OnePlusCalibrateTx.MIN_GLUCOSE_MGDL || glucoseMgdl > OnePlusCalibrateTx.MAX_GLUCOSE_MGDL) return Refusal.OUT_OF_RANGE
        if (bloodAtMs > nowMs || OnePlusCalibrationQueue.isTooOld(bloodAtMs, nowMs)) return Refusal.TOO_OLD
        // CONNECTING and RECONNECTING are the normal gap between two radio windows, not a reason to refuse.
        if (phase == OnePlusWarmupState.Phase.WARMING || phase == OnePlusWarmupState.Phase.FAILED) return Refusal.NOT_READY
        if (lastReading == null || nowMs - lastReading.timestampMs > RECENT_READING_MS) return Refusal.NO_RECENT_READING
        val rate = lastReading.trendSlopeMgdlPerMin ?: return Refusal.RATE_UNKNOWN
        if (abs(rate) > MAX_RATE_MGDL_PER_MIN) return Refusal.CHANGING_TOO_FAST
        return null
    }
}

package app.aaps.plugins.libre3.warmup

import kotlin.math.abs

/** Where a sensor is in its life. */
enum class Libre3LifePhase { WARMUP, ACTIVE, EXPIRED }

/**
 * Where a sensor is in its life, worked out from its own minute counter.
 *
 * Ported from LibreCRKit `DataPlane/SensorLifecycle.swift` at pin `a86b92f`.
 *
 * The sensor never sends a "time left". It sends how many minutes it has been running, and the
 * NFC step told us how long its warm-up and its whole life are. Everything here follows from
 * those three numbers, so it is pure and unit tested.
 *
 * @param lifeCountMinutes minutes since the sensor started, as the sensor counts them.
 * @param warmupMinutes warm-up length read over NFC. 60 minutes on every sensor seen so far.
 * @param wearMinutes whole life read over NFC, or null when it is not known.
 */
data class Libre3WarmupClock(
    val lifeCountMinutes: Int,
    val warmupMinutes: Int = DEFAULT_WARMUP_MINUTES,
    val wearMinutes: Int? = null,
) {

    private val elapsedMinutes: Int get() = maxOf(0, lifeCountMinutes)

    /** Minutes of warm-up left, never below zero. */
    val remainingWarmupMinutes: Int get() = maxOf(0, warmupMinutes - elapsedMinutes)

    /** Milliseconds of warm-up left, for the countdown on the screen. */
    val remainingWarmupMs: Long get() = remainingWarmupMinutes * 60_000L

    /** Minutes of sensor life left, or null when the whole life is not known. */
    val remainingWearMinutes: Int? get() = wearMinutes?.let { maxOf(0, it - elapsedMinutes) }

    val isWarmupComplete: Boolean get() = elapsedMinutes >= warmupMinutes

    /** True while the sensor is still warming up. Glucose must not reach the loop in this state. */
    val isWarmingUp: Boolean get() = !isWarmupComplete && !isExpired

    /** True once the sensor has run for its whole life. */
    val isExpired: Boolean get() = wearMinutes?.let { elapsedMinutes >= it } ?: false

    val phase: Libre3LifePhase
        get() = when {
            isExpired         -> Libre3LifePhase.EXPIRED
            !isWarmupComplete -> Libre3LifePhase.WARMUP
            else              -> Libre3LifePhase.ACTIVE
        }

    init {
        require(warmupMinutes >= 0) { "a warm-up cannot be shorter than nothing" }
        require(wearMinutes == null || wearMinutes >= 0) { "a sensor life cannot be shorter than nothing" }
        // A whole life shorter than the warm-up makes no sense. These numbers come from the sensor
        // over NFC, so they are not trusted. The order in [phase] is chosen so that such a sensor
        // reads as finished, and a finished sensor never feeds the loop. Blocking is the safe way
        // round, so this stays a note and not a refusal: a real sensor with an odd answer should
        // still be shown to the user rather than making the driver throw.
    }

    companion object {

        /** Used only when the sensor did not say how long its warm-up is. */
        const val DEFAULT_WARMUP_MINUTES = 60

        /**
         * When the sensor was started, worked out backwards from a reading.
         *
         * Used when the NFC step could not tell us: a sensor taken over mid-life that reported no
         * activation time of its own. The sensor's own minute counter is the honest anchor.
         */
        fun activationTimeFromReading(sampleTimeMs: Long, lifeCountMinutes: Int): Long =
            sampleTimeMs - lifeCountMinutes * 60_000L

        /**
         * Time of a reading, counted from the moment the sensor was started.
         *
         * This is why the activation moment has to be the sensor's own. If it were the moment of
         * the NFC scan, every reading of a sensor taken over mid-life would land in the future.
         */
        fun sampleTimeMs(activatedAtMs: Long, lifeCountMinutes: Int): Long =
            activatedAtMs + lifeCountMinutes * 60_000L

        /**
         * True when a stored activation moment disagrees with what the sensor itself says by more
         * than half an hour. The sensor is then believed, not the stored value.
         *
         * Kept from LibreLoop, which self-heals this way after a phone clock change or a stored
         * value written by an older build.
         *
         * @param receivedAtMs the phone clock at the moment the reading **arrived**. It must never
         *   be the output of [sampleTimeMs]: that value is itself built from [activatedAtMs], so
         *   the two sides would always agree, this check would always answer false, and the repair
         *   would quietly stop working. A sensor adopted in the middle of its life would then keep
         *   a wrong start time for ever, with a false warm-up and every reading timed wrongly.
         */
        fun anchorLooksWrong(activatedAtMs: Long, receivedAtMs: Long, lifeCountMinutes: Int): Boolean {
            if (activatedAtMs <= 0L) return true
            val fromSensor = activationTimeFromReading(receivedAtMs, lifeCountMinutes)
            return abs(fromSensor - activatedAtMs) > ANCHOR_TOLERANCE_MS
        }

        /** How far a stored activation moment may differ from the sensor before it is dropped. */
        const val ANCHOR_TOLERANCE_MS = 30L * 60_000L
    }
}

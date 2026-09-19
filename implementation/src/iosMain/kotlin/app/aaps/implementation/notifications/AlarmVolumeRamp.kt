package app.aaps.implementation.notifications

import kotlin.math.ln
import kotlin.math.pow

/**
 * The volume curve an AAPS alarm ramps along, as plain arithmetic.
 *
 * Deliberately separate from the player so it can be tested without an audio device, and kept
 * identical to `AlarmSoundPlayerImpl` on Android: an alarm that grew louder at a different rate on
 * one platform would be a quiet behaviour change in the one feature where behaviour matters most.
 *
 * The shape is a logarithmic rise - quiet for a while, then quickly to full - paired with a delay
 * that shrinks as the level climbs, so a missed alarm becomes hard to sleep through without starting
 * out startling.
 *
 * Both halves are duplicated from the Android class rather than shared, because that class is still
 * in `androidMain`. When it moves to `commonMain` this should go with it and one copy should win.
 */
internal class AlarmVolumeRamp {

    /** 0 until [start] has been called; then 1..[STEPS] as [next] is called. */
    var level: Int = 0
        private set

    fun start() {
        level = 0
    }

    /** Advances one step and returns the volume for it, in 0f..1f. */
    fun next(): Float {
        level++
        val percentage = 100.0.coerceAtMost(level / STEPS.toDouble() * 100)
        return (1 - (ln(1.0.coerceAtLeast(100.0 - percentage)) / ln(100.0))).toFloat()
    }

    /** True while there are steps left; once false the alarm is at full volume. */
    fun hasMore(): Boolean = level < STEPS

    /**
     * Milliseconds to wait before the next step.
     *
     * Shrinks with the square of the level, floored so the last steps do not become a stutter.
     */
    fun delayMillis(): Long =
        MIN_DELAY_MS.coerceAtLeast(
            BASE_DELAY_MS - ((level - 1).toDouble().pow(DELAY_DECREMENT_EXPONENT) * 1000).toLong()
        )

    companion object {

        const val INITIAL_SILENT_TIME_MS = 3_000L

        private const val STEPS = 40
        private const val BASE_DELAY_MS = 15_000L
        private const val MIN_DELAY_MS = 2_000L
        private const val DELAY_DECREMENT_EXPONENT = 2.0
    }
}

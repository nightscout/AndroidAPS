package app.aaps.wear.complications.cwf

/**
 * The watch face drawn **without seconds**, kept ready and refreshed when the minute turns.
 *
 * This is what the always-on slot of the pushed document publishes, and it is what makes the second
 * hand disappear at the instant the watch dozes rather than a minute later.
 *
 * Why a second slot at all, measured on a Galaxy Watch 4:
 *
 * - the runtime repaints **once** as the watch dozes, and then not again until the minute turns;
 * - that one repaint takes the frame it already has. Ours went out 154 ms later - our own detection
 *   is only 27 ms behind the display state, so being quicker is not the answer;
 * - the runtime knew 214 ms before the display state changed, but only through a service tied to a
 *   privileged permission an ordinary app cannot hold.
 *
 * So no amount of speed wins that race. What wins is not racing: the document holds two full-screen
 * images of the same face, one with seconds and one without, and the runtime swaps them itself at the
 * mode change - the same mechanism that already makes the whole picture vanish in always-on, which is
 * instant and reliable.
 *
 * **Two complete pictures, never two halves.** Splitting the seconds out of one picture was tried on
 * paper and rejected: `SECOND` sits in the middle of the paint order with cover plates, the date and
 * the hands drawn over it, and the digital seconds are characters inside the TIME view's own text.
 * Each slot therefore carries a whole face, which has no z-order to reconcile and nothing to keep in
 * step.
 *
 * **And it is not double the work.** Everything expensive is shared - one watch face, one pipeline,
 * one set of cached layers, one read of the data. What is left is the final stack and the
 * compression, and this picture only needs a new one when the minute changes, because without
 * seconds nothing else about it moves that the eye can see: the minute hand travels 0.1 degree per
 * second. So one compression a minute is added to the one a second the awake face already pays.
 */
class CwfMinuteFrame {

    @Volatile private var held: PreparedFrame? = null

    /** Replaces the held picture. [second] is the moment it depicts. */
    fun offer(second: Long, bytes: ByteArray) {
        held = PreparedFrame(second, bytes)
    }

    /**
     * The held picture, or null when it cannot honestly be shown for [now].
     *
     * Refused when it depicts a different minute. Its seconds are absent, so nobody can see those go
     * stale - but the minute is drawn, both as a hand and as text, and showing the previous one is the
     * fault that took the longest to find on this watch face.
     */
    fun take(now: Long): PreparedFrame? =
        held?.takeIf { it.second <= now && now / 60_000 == it.second / 60_000 }

    /** Whether the producer should draw a new one for [now]. */
    fun stale(now: Long): Boolean =
        held?.let { now / 60_000 != it.second / 60_000 } ?: true

    /** Drops it, for a change that makes every picture wrong - new data, a new zip. */
    fun clear() {
        held = null
    }

    /** Whether anything is held at all. */
    val prepared: Boolean get() = held != null
}

package app.aaps.plugins.source

/**
 * Decides when a pre-soak sensor may take over the loop by itself, without the user pressing the
 * promote button.
 *
 * Used by the native CGM plugins that have a pre-soak sensor. It is checked on every reading of the
 * pre-soak sensor, so it needs no timer, it survives an app restart, and a sensor that sends nothing
 * is never switched to.
 *
 * It only says "yes" or "not yet". The switch itself is the same function the button calls, with
 * all of its own checks. This class adds checks on top of them, it never removes one.
 */
object StagingAutoPromotion {

    private const val MINUTE_MS = 60L * 1000L
    private const val HOUR_MS = 60L * MINUTE_MS

    /** Readings older than this do not count as "recent". */
    const val RECENT_WINDOW_MS = 30L * MINUTE_MS

    /** The newest reading must be at most this old. */
    const val MAX_LAST_READING_AGE_MS = 15L * MINUTE_MS

    /**
     * After an automatic switch was tried and refused, wait at least this long before the next try.
     * Keeps the log readable when a reading arrives every minute.
     */
    const val RETRY_INTERVAL_MS = 30L * MINUTE_MS

    /** A value at or below this is the sensor's low limit, not a real glucose. */
    const val RAIL_LOW_MGDL = 39.0

    /** A value at or above this is the sensor's high limit, not a real glucose. */
    const val RAIL_HIGH_MGDL = 400.0

    enum class Decision {

        /** The setting is off. */
        DISABLED,

        /** The pre-soak start time is not known. */
        NO_START,

        /** The set number of hours has not passed yet. */
        TOO_EARLY,

        /** Not enough readings in the last [RECENT_WINDOW_MS]. */
        TOO_FEW_RECENT,

        /** The newest reading is older than [MAX_LAST_READING_AGE_MS]. */
        STALE,

        /** A recent reading is at the sensor's low or high limit. */
        RAILED,

        /** All checks passed: switch now. */
        PROMOTE,
    }

    /**
     * @param enabled the user's switch for the automatic promotion
     * @param delayHours hours after the pre-soak start before the switch may happen
     * @param startMs pre-soak start (epoch ms), 0 or less when not known
     * @param readings pre-soak readings as (timestamp ms, mg/dL), any order
     * @param minRecentReadings how many readings the last [RECENT_WINDOW_MS] must hold; it depends on
     *   the sensor cadence (5 min for Dexcom ONE+ / G7)
     * @param nowMs current time (epoch ms)
     */
    fun decide(
        enabled: Boolean,
        delayHours: Int,
        startMs: Long,
        readings: List<Pair<Long, Double>>,
        minRecentReadings: Int,
        nowMs: Long,
    ): Decision {
        if (!enabled) return Decision.DISABLED
        if (startMs <= 0L) return Decision.NO_START
        if (nowMs - startMs < delayHours * HOUR_MS) return Decision.TOO_EARLY
        val recent = readings.filter { (ts, _) -> ts in (nowMs - RECENT_WINDOW_MS)..nowMs }
        if (recent.size < minRecentReadings) return Decision.TOO_FEW_RECENT
        val newestMs = recent.maxOf { it.first }
        if (nowMs - newestMs > MAX_LAST_READING_AGE_MS) return Decision.STALE
        if (recent.any { (_, mgdl) -> mgdl <= RAIL_LOW_MGDL || mgdl >= RAIL_HIGH_MGDL }) return Decision.RAILED
        return Decision.PROMOTE
    }
}

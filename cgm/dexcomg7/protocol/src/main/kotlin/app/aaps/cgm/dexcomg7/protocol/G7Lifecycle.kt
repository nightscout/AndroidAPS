package app.aaps.cgm.dexcomg7.protocol

import kotlin.math.abs

/** Where a sensor is in its life, as shown to the user. */
enum class G7LifecycleState {

    /** No sensor paired and nothing to pair with. */
    UNPAIRED,

    /** Paired, but no reading yet. A sensor only links around each 5-minute reading, so this can last minutes. */
    CONNECTING,
    WARMUP,
    OK,
    FAILED,

    /** Past the nominal end; readings go on for up to 12 hours. */
    GRACE_PERIOD,

    /** Session over; readings have stopped. */
    EXPIRED
}

/** Sensor lifecycle alerts. Missing readings are not here: AAPS has its own alarm for that. */
enum class G7LifecycleAlert {

    /** 24 hours before the nominal end. */
    EXPIRING_SOON,

    /** 2 hours before the nominal end. */
    EXPIRING_IMMINENTLY,

    /** Nominal end; readings go on through the grace period. */
    EXPIRED,

    /** End of the grace period; readings stop. */
    SESSION_ENDED,
    SENSOR_FAILED,

    /** The sensor refused the connection, or a stored key stopped working. */
    CONNECTION_REFUSED,

    /** Warmup is over and readings are usable. */
    WARMUP_FINISHED
}

/** The timing rules of a sensor session. All times are milliseconds since the epoch. */
object G7Lifecycle {

    const val DEFAULT_LIFETIME_MS = 10L * 24 * 60 * 60 * 1000
    const val DEFAULT_WARMUP_MS = 27L * 60 * 1000
    const val GRACE_PERIOD_MS = 12L * 60 * 60 * 1000

    const val EXPIRING_SOON_LEAD_MS = 24L * 60 * 60 * 1000
    const val EXPIRING_IMMINENTLY_LEAD_MS = 2L * 60 * 60 * 1000

    /** How far back to ask for backfill when nothing is known about the gap (a fresh pairing). */
    const val BACKFILL_WINDOW_MS = 3L * 60 * 60 * 1000

    /** The most one backfill request asks for. What the sensor no longer has, it leaves out. */
    const val MAXIMUM_BACKFILL_WINDOW_MS = 24L * 60 * 60 * 1000

    /** More time than this between the stored and the live reading means at least one is missing. */
    const val BACKFILL_GAP_MS = 7L * 60 * 1000

    /**
     * How far the sensor clock may move against the phone clock before we take a new anchor. Also the
     * margin around known readings in a backfill. Readings are 5 minutes apart, so 2 minutes is safe.
     */
    const val CLOCK_TOLERANCE_MS = 2L * 60 * 1000

    /** Nominal life, without the grace period. [sessionLengthSeconds] includes the grace period. */
    fun lifetimeMs(sessionLengthSeconds: Long?): Long =
        if (sessionLengthSeconds != null && sessionLengthSeconds > 0) sessionLengthSeconds * 1000 - GRACE_PERIOD_MS else DEFAULT_LIFETIME_MS

    fun warmupMs(warmupSeconds: Int?): Long = if (warmupSeconds != null && warmupSeconds > 0) warmupSeconds * 1000L else DEFAULT_WARMUP_MS

    fun expiresAt(activatedAt: Long, sessionLengthSeconds: Long?): Long = activatedAt + lifetimeMs(sessionLengthSeconds)

    fun endsAt(activatedAt: Long, sessionLengthSeconds: Long?): Long = expiresAt(activatedAt, sessionLengthSeconds) + GRACE_PERIOD_MS

    /**
     * The sensor's state for display. [sensorKnown] is true once a first reading has named the sensor.
     * [canAuthenticate] is true when a pairing code or key is stored.
     */
    fun state(
        sensorKnown: Boolean,
        canAuthenticate: Boolean,
        activatedAt: Long?,
        sessionLengthSeconds: Long?,
        latestState: AlgorithmState?,
        now: Long
    ): G7LifecycleState {
        if (!sensorKnown) return if (canAuthenticate) G7LifecycleState.CONNECTING else G7LifecycleState.UNPAIRED
        if (activatedAt != null && endsAt(activatedAt, sessionLengthSeconds) < now) return G7LifecycleState.EXPIRED
        if (latestState != null) {
            if (latestState.isInWarmup) return G7LifecycleState.WARMUP
            if (latestState.sensorFailed) return G7LifecycleState.FAILED
            if (latestState.isSessionEnded) return G7LifecycleState.EXPIRED
        }
        if (activatedAt != null && expiresAt(activatedAt, sessionLengthSeconds) < now) return G7LifecycleState.GRACE_PERIOD
        return G7LifecycleState.OK
    }

    /**
     * The latest session-clock alert whose moment has passed, or null before the first one.
     * The caller posts it once per sensor. A later moment replaces an earlier one, because only the
     * newest says what matters now.
     */
    fun currentSessionAlert(activatedAt: Long, sessionLengthSeconds: Long?, now: Long): G7LifecycleAlert? {
        val expiresAt = expiresAt(activatedAt, sessionLengthSeconds)
        val endsAt = endsAt(activatedAt, sessionLengthSeconds)
        return when {
            now >= endsAt                                     -> G7LifecycleAlert.SESSION_ENDED
            now >= expiresAt                                  -> G7LifecycleAlert.EXPIRED
            now >= expiresAt - EXPIRING_IMMINENTLY_LEAD_MS    -> G7LifecycleAlert.EXPIRING_IMMINENTLY
            now >= expiresAt - EXPIRING_SOON_LEAD_MS          -> G7LifecycleAlert.EXPIRING_SOON
            else                                              -> null
        }
    }

    /**
     * The backfill request range in sensor seconds, or null when nothing is missing.
     *
     * [latestStoredAt] is the newest reading already in the database, and [liveReadingAt] is the
     * reading that just came in. Readings come every 5 minutes, so when these two are less than
     * [BACKFILL_GAP_MS] apart nothing is missing and we do not ask. Otherwise the range starts after
     * the stored reading and ends before the live one, so neither is sent to us again. A long outage
     * is asked for in full, up to [MAXIMUM_BACKFILL_WINDOW_MS].
     */
    fun backfillRange(activatedAt: Long, latestStoredAt: Long?, liveReadingAt: Long): Pair<Long, Long>? {
        if (latestStoredAt != null && liveReadingAt - latestStoredAt <= BACKFILL_GAP_MS) return null
        val from = latestStoredAt?.let { it + CLOCK_TOLERANCE_MS } ?: (liveReadingAt - BACKFILL_WINDOW_MS)
        val earliest = maxOf(activatedAt, from, liveReadingAt - MAXIMUM_BACKFILL_WINDOW_MS)
        val latest = liveReadingAt - CLOCK_TOLERANCE_MS
        if (earliest > latest) return null
        return (earliest - activatedAt) / 1000 to (latest - activatedAt) / 1000
    }

    /**
     * The phone time of the sensor's second zero, used to turn sensor seconds into phone time.
     *
     * [measured] is worked out again on each reading (phone time now minus the sensor's clock). It
     * moves by up to a second each time, because the sensor counts whole seconds and Bluetooth adds
     * its own delay. So we keep [stored] while [measured] stays within [CLOCK_TOLERANCE_MS] of it. The
     * same sensor second then always gives the same timestamp, and a reading we get twice is found
     * as the same reading in the database. Only a real change of the phone clock moves the anchor.
     */
    fun clockAnchor(stored: Long?, measured: Long): Long =
        if (stored != null && abs(measured - stored) <= CLOCK_TOLERANCE_MS) stored else measured
}

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

/**
 * The moments before and after the end of a sensor session that the user is told about, in time order.
 *
 * The session ends at its nominal end ([G7Lifecycle.expiresAt], 10 or 15 days). A 12-hour grace period
 * follows, in which readings go on; its end ([G7Lifecycle.endsAt]) is the real end, when readings stop.
 * [beforeExpiryMs] is where the moment lies: positive before the nominal end, negative after it.
 */
enum class G7SessionMilestone(val beforeExpiryMs: Long) {

    ENDS_IN_24H(24L * 60 * 60 * 1000),
    ENDS_IN_6H(6L * 60 * 60 * 1000),
    ENDS_IN_2H(2L * 60 * 60 * 1000),

    /** The nominal end: the grace period starts. */
    GRACE_STARTED(0),
    GRACE_ENDS_IN_6H(-G7Lifecycle.GRACE_PERIOD_MS + 6L * 60 * 60 * 1000),
    GRACE_ENDS_IN_2H(-G7Lifecycle.GRACE_PERIOD_MS + 2L * 60 * 60 * 1000),

    /** The grace period is over, or the sensor says its session has ended: readings stop. */
    ENDED(-G7Lifecycle.GRACE_PERIOD_MS)
}

/** Sensor lifecycle alerts other than the session end ([G7SessionMilestone]). Missing readings are not here. */
enum class G7LifecycleAlert {

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

    /** From this long before the nominal end, the status screen shows the end date as a warning. */
    const val EXPIRING_SOON_LEAD_MS = 24L * 60 * 60 * 1000

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
     * The latest [G7SessionMilestone] whose moment has passed, or null before the first one. The caller
     * tells the user once for each. A later moment replaces an earlier one, because only the newest says
     * what matters now. [sessionEnded] is the sensor itself saying its session is over, which counts as
     * [G7SessionMilestone.ENDED] whatever the clock says.
     */
    fun currentMilestone(activatedAt: Long, sessionLengthSeconds: Long?, now: Long, sessionEnded: Boolean = false): G7SessionMilestone? {
        if (sessionEnded) return G7SessionMilestone.ENDED
        val expiresAt = expiresAt(activatedAt, sessionLengthSeconds)
        return G7SessionMilestone.entries.lastOrNull { now >= expiresAt - it.beforeExpiryMs }
    }

    /** When [milestone] comes for this sensor. */
    fun milestoneAt(activatedAt: Long, sessionLengthSeconds: Long?, milestone: G7SessionMilestone): Long =
        expiresAt(activatedAt, sessionLengthSeconds) - milestone.beforeExpiryMs

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

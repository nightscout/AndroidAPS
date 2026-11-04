package app.aaps.core.data.time

import kotlinx.datetime.TimeZone
import kotlin.concurrent.Volatile
import kotlin.time.Clock

/**
 * Time zone offset in milliseconds for a given moment, DST aware.
 *
 * The records keep this as `utcOffset`. It is written to the database, it takes part in
 * `contentEqualsTo` (so a changed value makes a record look different and sync again), it is sent to
 * Nightscout - which validates it and answers 400 when it is wrong - and it goes to Open Humans.
 * So the value has to stay exactly what the old `TimeZone.getDefault().getOffset(timestamp)`
 * produced. Keeping it as a platform function does that at every call site at once, without
 * touching the models or the ~154 places that already pass `utcOffset` themselves.
 */
expect fun systemUtcOffsetAt(timestamp: Long): Long

/**
 * The system time zone, read once and then kept for a short while.
 *
 * `TimeZone.currentSystemDefault()` costs a very different amount on each platform. On the JVM it is
 * a cached default plus a lookup in memory. On Apple targets kotlinx-datetime calls
 * `NSTimeZone.resetSystemTimeZone()` on purpose and then resolves the zone name again, which reads
 * and parses the zone file from disk on **every** call.
 *
 * That call sits in the hottest path in the app. `MidnightTime.secondsFromMidnight` is asked for the
 * basal rate of every minute of every temporary basal, again for every 5 minute step, so the zone is
 * read millions of times per IOB/COB run. On iOS one run took about 37 s because of it
 * (nightscout/AndroidAPS#5484). On Android the same path was once the most expensive app code in a
 * CPU trace, which is why `MidnightTime` already caches the day - this caches the one piece that
 * was left.
 *
 * ### How it stays correct
 *
 * A phone can cross a time zone without restarting, and the basal profile is looked up in the
 * current zone, so a stale zone would shift the profile of the past by the offset - the bug in
 * `_docs/IOB_TIME_ZONE.md`, except it would not end. Two things refresh it:
 *
 * 1. [invalidate], called by the platform watcher the moment the system reports a change
 *    (`TimeDateOrTZChangeReceiver` on Android, `IosTimeDateOrTZChangeWatcher` on iOS). This is the
 *    mechanism, and it makes the change take effect at once.
 * 2. [RECHECK_INTERVAL_MILLIS], a re-read of the zone when the kept one is older than that. This is
 *    insurance, not the mechanism, and it has to stay on **every** platform: the Wear app is a
 *    separate process with no watcher of its own, the desktop has no system notification to listen
 *    to at all, and a watcher that is started late or dropped in a refactor would otherwise leave a
 *    wrong zone in place until the next restart. One extra read per 10 s is nothing next to that.
 *
 * So the zone used can be at most [RECHECK_INTERVAL_MILLIS] old, and only when the watcher did not
 * report the change itself.
 */
object SystemTimeZone {

    /** How long one read stays good when nothing reports a change. */
    const val RECHECK_INTERVAL_MILLIS = 10_000L

    private class Kept(val zone: TimeZone, val readAtMillis: Long)

    /**
     * One immutable value, so a reader never sees a zone with another read's timestamp. Two threads
     * reading at the same time only costs one extra lookup, as in `MidnightTime`.
     */
    @Volatile private var kept: Kept? = null

    /** The system time zone, from the last read when that is recent enough. */
    fun current(): TimeZone = currentAt(Clock.System.now().toEpochMilliseconds())

    /** Forget the kept zone. The next caller reads it again. */
    fun invalidate() {
        kept = null
    }

    /**
     * [current] with the time given rather than read, so a test can move it.
     *
     * A negative age means the clock went backwards - the user set the time by hand, or an NTP step -
     * and the kept zone is then thrown away rather than held for the whole interval measured against
     * a time that no longer exists.
     */
    internal fun currentAt(nowMillis: Long): TimeZone {
        kept?.let { held ->
            val age = nowMillis - held.readAtMillis
            if (age in 0..<RECHECK_INTERVAL_MILLIS) return held.zone
        }
        val zone = TimeZone.currentSystemDefault()
        kept = Kept(zone, nowMillis)
        return zone
    }
}
